/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan initial implementation
 */
package org.locationtech.geogig.storage.postgresql.v9;

import org.locationtech.geogig.storage.RevObjectSerializer;
import org.locationtech.geogig.storage.datastream.DataStreamRevObjectSerializerV1;
import org.locationtech.geogig.storage.datastream.DataStreamRevObjectSerializerV2;
import org.locationtech.geogig.storage.datastream.DataStreamRevObjectSerializerV2_1;
import org.locationtech.geogig.storage.datastream.DataStreamRevObjectSerializerV2_2;
import org.locationtech.geogig.storage.datastream.RevObjectSerializerLZF;
import org.locationtech.geogig.storage.datastream.RevObjectSerializerProxy;

/**
 * 
 * @since 1.4
 */
class PGSerializationProxy extends RevObjectSerializerProxy {
    /**
     * For historical reasons, the LZF wrapped formats must be kept
     */
    private static final RevObjectSerializer[] SUPPORTED_FORMATS = { //
            new RevObjectSerializerLZF(DataStreamRevObjectSerializerV1.INSTANCE), //
            new RevObjectSerializerLZF(DataStreamRevObjectSerializerV2.INSTANCE), //
            new RevObjectSerializerLZF(DataStreamRevObjectSerializerV2_1.INSTANCE), //
            new RevObjectSerializerLZF(DataStreamRevObjectSerializerV2_2.INSTANCE)//
            // The above formats oughta stay like that for backwards compatibility
            //, new FlatBuffersRevObjectSerializer()
    };

    public PGSerializationProxy() {
        super(SUPPORTED_FORMATS);
    }
}
