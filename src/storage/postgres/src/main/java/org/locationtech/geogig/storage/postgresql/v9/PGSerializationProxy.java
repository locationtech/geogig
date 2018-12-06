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
import org.locationtech.geogig.storage.datastream.DataStreamSerializationFactoryV1;
import org.locationtech.geogig.storage.datastream.DataStreamSerializationFactoryV2;
import org.locationtech.geogig.storage.datastream.DataStreamSerializationFactoryV2_1;
import org.locationtech.geogig.storage.datastream.DataStreamSerializationFactoryV2_2;
import org.locationtech.geogig.storage.datastream.LZFSerializationFactory;
import org.locationtech.geogig.storage.datastream.SerializationFactoryProxy;

/**
 * 
 * @since 1.4
 */
class PGSerializationProxy extends SerializationFactoryProxy {
    /**
     * For historical reasons, the LZF wrapped formats must be kept
     */
    private static final RevObjectSerializer[] SUPPORTED_FORMATS = { //
            new LZFSerializationFactory(DataStreamSerializationFactoryV1.INSTANCE), //
            new LZFSerializationFactory(DataStreamSerializationFactoryV2.INSTANCE), //
            new LZFSerializationFactory(DataStreamSerializationFactoryV2_1.INSTANCE), //
            new LZFSerializationFactory(DataStreamSerializationFactoryV2_2.INSTANCE)//
            // The above formats oughta stay like that for backwards compatibility
            //, new FlatBuffersRevObjectSerializer()
    };

    public PGSerializationProxy() {
        super(SUPPORTED_FORMATS);
    }
}
