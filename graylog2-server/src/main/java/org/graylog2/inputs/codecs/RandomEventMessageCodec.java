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
package org.graylog2.inputs.codecs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.assistedinject.Assisted;
import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.inputs.random.generators.FakeEventMessageGenerator;
import org.graylog2.plugin.Message;
import org.graylog2.plugin.configuration.ConfigurationRequest;
import org.graylog2.plugin.inputs.annotations.Codec;
import org.graylog2.plugin.inputs.annotations.ConfigClass;
import org.graylog2.plugin.inputs.annotations.FactoryClass;
import org.graylog2.plugin.inputs.codecs.AbstractCodec;
import org.graylog2.plugin.inputs.codecs.CodecAggregator;
import org.graylog2.plugin.journal.RawMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.IOException;

@Codec(name = "random-event-msg", displayName = "Random Event Message")
public class RandomEventMessageCodec extends AbstractCodec {
    private static final Logger log = LoggerFactory.getLogger(RandomEventMessageCodec.class);
    private final ObjectMapper objectMapper;

    @Inject
    public RandomEventMessageCodec(@Assisted Configuration configuration, ObjectMapper objectMapper) {
        super(configuration);
        this.objectMapper = objectMapper;
    }

    @Nullable
    public Message decode(@Nonnull RawMessage rawMessage) {

        if (!rawMessage.getCodecName().equals(getName())) {
            log.error("Cannot decode payload type {}, skipping message {}",
                    rawMessage.getCodecName(), rawMessage.getId());
            return null;
        }
        try {
            final FakeEventMessageGenerator.GeneratorState state = objectMapper.readValue(rawMessage.getPayload(), FakeEventMessageGenerator.GeneratorState.class);
            return FakeEventMessageGenerator.generateMessage(state);
        } catch (IOException e) {
            log.error("Cannot decode message to class FakeEventMessageGenerator.GeneratorState", e);
        }
        return null;
    }

    @Nullable
    @Override
    public CodecAggregator getAggregator() {
        return null;
    }

    @FactoryClass
    public interface Factory extends AbstractCodec.Factory<RandomEventMessageCodec> {
        @Override
        RandomEventMessageCodec create(Configuration configuration);

        @Override
        RandomHttpMessageCodec.Config getConfig();

        @Override
        RandomHttpMessageCodec.Descriptor getDescriptor();
    }

    @ConfigClass
    public static class Config extends AbstractCodec.Config {
        @Override
        public void overrideDefaultValues(@Nonnull ConfigurationRequest cr) {

        }
    }

    public static class Descriptor extends AbstractCodec.Descriptor {
        @Inject
        public Descriptor() {
            super(RandomEventMessageCodec.class.getAnnotation(Codec.class).displayName());
        }
    }
}
