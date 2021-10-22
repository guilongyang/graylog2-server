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
package org.graylog2.cluster.leader;

import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.common.util.concurrent.Uninterruptibles;
import org.graylog2.cluster.lock.LockService;
import org.graylog2.periodical.NodePingThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

import static java.util.concurrent.TimeUnit.SECONDS;

@Singleton
public class MongoLeaderElectionService extends AbstractExecutionThreadService implements LeaderElectionService {
    private static final String RESOURCE_NAME = "cluster-leader";
    private static final Logger log = LoggerFactory.getLogger(MongoLeaderElectionService.class);

    private final LockService lockService;
    private final EventBus eventBus;
    private final NodePingThread nodePingThread;

    private volatile boolean isLeader = false;

    @Inject
    public MongoLeaderElectionService(LockService lockService, EventBus eventBus, NodePingThread nodePingThread) {
        this.lockService = lockService;
        this.eventBus = eventBus;
        this.nodePingThread = nodePingThread;
    }

    @Override
    public boolean isLeader() {
        return isLeader;
    }

    @Override
    protected void run() throws Exception {

        while (isRunning()) {
            final boolean wasLeader = isLeader;

            // TODO: failure handling
            isLeader = lockService.lock(RESOURCE_NAME).isPresent();

            if (wasLeader != isLeader) {
                log.info("Leader changed. This node is {}.", isLeader ? "now the leader" : "not the leader anymore");
                // Ensure the nodes collection is up to date before we publish the event
                nodePingThread.doRun();
                eventBus.post(new LeaderChangedEvent());
            }

            // TODO: make configurable. Handle interruption on shutdown.
            Uninterruptibles.sleepUninterruptibly(2, SECONDS);
        }
    }
}
