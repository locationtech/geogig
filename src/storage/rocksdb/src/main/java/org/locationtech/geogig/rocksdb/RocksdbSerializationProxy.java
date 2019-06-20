/* Copyright (c) 2019 Gabriel Roldan
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan initial implementation
 */
package org.locationtech.geogig.rocksdb;

import org.locationtech.geogig.flatbuffers.FlatBuffersRevObjectSerializer;
import org.locationtech.geogig.storage.RevObjectSerializer;
import org.locationtech.geogig.storage.datastream.DataStreamRevObjectSerializerV1;
import org.locationtech.geogig.storage.datastream.DataStreamRevObjectSerializerV2;
import org.locationtech.geogig.storage.datastream.DataStreamRevObjectSerializerV2_1;
import org.locationtech.geogig.storage.datastream.DataStreamRevObjectSerializerV2_2;
import org.locationtech.geogig.storage.datastream.RevObjectSerializerProxy;
import org.locationtech.geogig.storage.format.lzf.RevObjectSerializerLZF;

/**
 * @since 2.0
 */
class RocksdbSerializationProxy extends RevObjectSerializerProxy {
    /**
     * For historical reasons, the LZF wrapped formats must be kept
     */
    private static final RevObjectSerializer[] SUPPORTED_FORMATS = { //
            new RevObjectSerializerLZF(DataStreamRevObjectSerializerV1.INSTANCE), //
            new RevObjectSerializerLZF(DataStreamRevObjectSerializerV2.INSTANCE), //
            new RevObjectSerializerLZF(DataStreamRevObjectSerializerV2_1.INSTANCE), //
            new RevObjectSerializerLZF(DataStreamRevObjectSerializerV2_2.INSTANCE)//
            // The above formats oughta stay like that for backwards compatibility
            , new FlatBuffersRevObjectSerializer()//
    };

    static final RocksdbSerializationProxy INSTANCE = new RocksdbSerializationProxy();

    public RocksdbSerializationProxy() {
        super(SUPPORTED_FORMATS);
    }
}
