/*
 * Copyright (C) 2020 Graylog, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */
package org.graylog.plugins.views.search.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.Uninterruptibles;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import one.util.streamex.StreamEx;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.graylog.plugins.views.audit.ViewsAuditEventTypes;
import org.graylog.plugins.views.search.Parameter;
import org.graylog.plugins.views.search.Query;
import org.graylog.plugins.views.search.QueryMetadata;
import org.graylog.plugins.views.search.Search;
import org.graylog.plugins.views.search.SearchDomain;
import org.graylog.plugins.views.search.SearchExecutionGuard;
import org.graylog.plugins.views.search.SearchJob;
import org.graylog.plugins.views.search.SearchMetadata;
import org.graylog.plugins.views.search.db.SearchDbService;
import org.graylog.plugins.views.search.db.SearchJobService;
import org.graylog.plugins.views.search.engine.QueryEngine;
import org.graylog.plugins.views.search.events.SearchJobExecutionEvent;
import org.graylog.plugins.views.search.permissions.SearchUser;
import org.graylog2.audit.jersey.AuditEvent;
import org.graylog2.audit.jersey.NoAuditEvent;
import org.graylog2.plugin.rest.PluginRestResource;
import org.graylog2.shared.rest.resources.RestResource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.GET;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.google.common.base.MoreObjects.firstNonNull;

@Api(value = "Search")
@Path("/views/search")
@Produces(MediaType.APPLICATION_JSON)
@RequiresAuthentication
public class SearchResource extends RestResource implements PluginRestResource {
    private static final Logger LOG = LoggerFactory.getLogger(SearchResource.class);

    private static final String BASE_PATH = "views/search";

    private final QueryEngine queryEngine;
    private final SearchDbService searchDbService;
    private final SearchJobService searchJobService;
    private final ObjectMapper objectMapper;
    private final PermittedStreams permittedStreams;
    private final SearchExecutionGuard executionGuard;
    private final SearchDomain searchDomain;
    private final EventBus serverEventBus;


    @Inject
    public SearchResource(QueryEngine queryEngine,
                          SearchDbService searchDbService,
                          SearchJobService searchJobService,
                          ObjectMapper objectMapper,
                          PermittedStreams permittedStreams,
                          SearchExecutionGuard executionGuard,
                          SearchDomain searchDomain,
                          EventBus serverEventBus) {
        this.queryEngine = queryEngine;
        this.searchDbService = searchDbService;
        this.searchJobService = searchJobService;
        this.objectMapper = objectMapper;
        this.permittedStreams = permittedStreams;
        this.executionGuard = executionGuard;
        this.searchDomain = searchDomain;
        this.serverEventBus = serverEventBus;
    }

    @POST
    @ApiOperation(value = "Create a search query", response = Search.class, code = 201)
    @AuditEvent(type = ViewsAuditEventTypes.SEARCH_CREATE)
    public Response createSearch(@ApiParam Search search, @Context SearchUser searchUser) {
        final Optional<Search> previous = Optional.ofNullable(search.id()).flatMap(searchDbService::get);
        if (!searchUser.isAdmin() && !previous.map(searchUser::owns).orElse(true)) {
            throw new ForbiddenException("Unable to update search with id <" + search.id() + ">, already exists and user is not permitted to overwrite it.");
        }

        guard(search, searchUser);

        final Search saved = searchDbService.save(search.toBuilder().owner(searchUser.username()).build());
        if (saved == null || saved.id() == null) {
            return Response.serverError().build();
        }
        LOG.debug("Created new search object {}", saved.id());
        return Response.created(URI.create(Objects.requireNonNull(saved.id()))).entity(saved).build();
    }

    @GET
    @ApiOperation(value = "Retrieve a search query")
    @Path("{id}")
    public Search getSearch(@ApiParam(name = "id") @PathParam("id") String searchId, @Context SearchUser searchUser) {
        return searchDomain.getForUser(searchId, searchUser)
                .orElseThrow(() -> new NotFoundException("Search with id " + searchId + " does not exist"));
    }

    @GET
    @ApiOperation(value = "Get all searches which the user may see")
    public List<Search> getAllSearches(@Context SearchUser searchUser) {
        // TODO should be paginated
        return searchDomain.getAllForUser(searchUser, searchUser::canReadView);
    }

    @POST
    @ApiOperation(value = "Execute the referenced search query asynchronously",
            notes = "Starts a new search, irrespective whether or not another is already running")
    @Path("{id}/execute")
    @NoAuditEvent("Creating audit event manually in method body.")
    public Response executeQuery(@ApiParam(name = "id") @PathParam("id") String id,
                                 @ApiParam ExecutionState executionState,
                                 @Context SearchUser searchUser) {
        Search search = getSearch(id, searchUser);

        search = search.addStreamsToQueriesWithoutStreams(() -> loadAllAllowedStreamsForUser(searchUser));

        guard(search, searchUser);

        search = search.applyExecutionState(objectMapper, firstNonNull(executionState, ExecutionState.empty()));

        final SearchJob searchJob = searchJobService.create(search, searchUser.username());

        postAuditEvent(searchJob);

        final SearchJob runningSearchJob = queryEngine.execute(searchJob);

        return Response.created(URI.create(BASE_PATH + "/status/" + runningSearchJob.getId()))
                .entity(runningSearchJob)
                .build();
    }

    private void postAuditEvent(SearchJob searchJob) {
        final SearchJobExecutionEvent searchJobExecutionEvent = SearchJobExecutionEvent.create(getCurrentUser(), searchJob, DateTime.now(DateTimeZone.UTC));
        this.serverEventBus.post(searchJobExecutionEvent);
    }

    private ImmutableSet<String> loadAllAllowedStreamsForUser(SearchUser searchUser) {
        return permittedStreams.load(searchUser::canReadStream);
    }

    private void guard(Search search, SearchUser searchUser) {
        this.executionGuard.check(search, searchUser::canReadStream);
    }

    @POST
    @ApiOperation(value = "Execute a new synchronous search", notes = "Executes a new search and waits for its result")
    @Path("sync")
    @NoAuditEvent("Creating audit event manually in method body.")
    public Response executeSyncJob(@ApiParam @NotNull(message = "Search body is mandatory") Search search,
                                   @ApiParam(name = "timeout", defaultValue = "60000")
                                   @QueryParam("timeout") @DefaultValue("60000") long timeout,
                                   @Context SearchUser searchUser) {
        final String username = searchUser.username();

        search = search.addStreamsToQueriesWithoutStreams(() -> loadAllAllowedStreamsForUser(searchUser));

        guard(search, searchUser);

        final SearchJob searchJob = queryEngine.execute(searchJobService.create(search, username));

        postAuditEvent(searchJob);

        try {
            Uninterruptibles.getUninterruptibly(searchJob.getResultFuture(), timeout, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            LOG.error("Error executing search job <{}>", searchJob.getId(), e);
            throw new InternalServerErrorException("Error executing search job: " + e.getMessage());
        } catch (TimeoutException e) {
            throw new InternalServerErrorException("Timeout while executing search job");
        } catch (Exception e) {
            LOG.error("Other error", e);
            throw e;
        }

        return Response.ok(searchJob).build();
    }

    @GET
    @ApiOperation(value = "Retrieve the status of an executed query")
    @Path("status/{jobId}")
    public SearchJob jobStatus(@ApiParam(name = "jobId") @PathParam("jobId") String jobId, @Context SearchUser searchUser) {
        final SearchJob searchJob = searchJobService.load(jobId, searchUser.username()).orElseThrow(NotFoundException::new);
        try {
            // force a "conditional join", to catch fast responses without having to poll
            Uninterruptibles.getUninterruptibly(searchJob.getResultFuture(), 5, TimeUnit.MILLISECONDS);
        } catch (ExecutionException | TimeoutException ignore) {
        }
        return searchJob;
    }

    @GET
    @ApiOperation(value = "Metadata for the given Search object", notes = "Used for already persisted search objects")
    @Path("metadata/{searchId}")
    public SearchMetadata metadata(@ApiParam("searchId") @PathParam("searchId") String searchId, @Context SearchUser searchUser) {
        final Search search = getSearch(searchId, searchUser);
        return metadataForObject(search);
    }

    @POST
    @ApiOperation(value = "Metadata for the posted Search object", notes = "Intended for search objects that aren't yet persisted (e.g. for validation or interactive purposes)")
    @Path("metadata")
    @NoAuditEvent("Only returning metadata for given search, not changing any data")
    public SearchMetadata metadataForObject(@ApiParam @NotNull Search search) {
        if (search == null) {
            throw new IllegalArgumentException("Search must not be null.");
        }
        final Map<String, QueryMetadata> queryMetadatas = StreamEx.of(search.queries()).toMap(Query::id, query -> queryEngine.parse(search, query));
        return SearchMetadata.create(queryMetadatas, Maps.uniqueIndex(search.parameters(), Parameter::name));
    }


}
