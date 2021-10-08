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
package org.graylog2.storage.versionprobe;

import com.google.auto.value.AutoValue;
import org.graylog2.plugin.Version;

import javax.annotation.Nullable;
import java.util.Optional;

@AutoValue
public abstract class SearchVersion {

    public enum Distribution {
        ELASTICSEARCH,
        OPENSEARCH
    }

    public SearchVersion major() {
        return create(distribution(), Version.from(version().getVersion().getMajorVersion(), 0, 0));
    }

    public abstract Distribution distribution();

    public abstract Version version();

    public static SearchVersion elasticsearch(final Version version) {
        return create(Distribution.ELASTICSEARCH, version);
    }

    /**
     * @param distribution Assumes ELASTICSEARCH by default when no distribution is provided
     */
    public static SearchVersion create(@Nullable final String distribution, final Version version) {
        final Distribution dst = Optional.ofNullable(distribution)
                .map(String::trim)
                .map(String::toUpperCase)
                .map(Distribution::valueOf)
                .orElse(Distribution.ELASTICSEARCH);
        return new AutoValue_SearchVersion(dst, version);
    }

    public static SearchVersion create(final Distribution distribution, final Version version) {
        return new AutoValue_SearchVersion(distribution, version);
    }

    @Override
    public String toString() {
        return distribution().name() + ":" + version();
    }
}
