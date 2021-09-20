package org.graylog.testing.containermatrix.descriptors;

import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContainerMatrixEngineDescriptor extends EngineDescriptor {
    private static final Logger LOG = LoggerFactory.getLogger(ContainerMatrixEngineDescriptor.class);

    /**
     * Create a new {@code EngineDescriptor} with the supplied {@link UniqueId}
     * and display name.
     *
     * @param uniqueId    the {@code UniqueId} for the described {@code TestEngine};
     *                    never {@code null}
     * @param displayName the display name for the described {@code TestEngine};
     *                    never {@code null} or blank
     * @see TestEngine#getId()
     * @see TestDescriptor#getDisplayName()
     */
    public ContainerMatrixEngineDescriptor(UniqueId uniqueId, String displayName) {
        super(uniqueId, displayName);
    }
}
