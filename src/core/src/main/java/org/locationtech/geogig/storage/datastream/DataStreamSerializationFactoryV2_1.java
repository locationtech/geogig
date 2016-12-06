/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.datastream;

/**
 * Serialization factory for serial version 2.1
 * 
 * @see FormatCommonV2_1
 */
public class DataStreamSerializationFactoryV2_1 extends DataStreamSerializationFactoryV2 {

    public static final DataStreamSerializationFactoryV2_1 INSTANCE = new DataStreamSerializationFactoryV2_1();

    public DataStreamSerializationFactoryV2_1() {
        super(FormatCommonV2_1.INSTANCE);
    }
}
