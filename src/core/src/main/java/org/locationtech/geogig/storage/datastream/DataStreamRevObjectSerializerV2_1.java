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
public class DataStreamRevObjectSerializerV2_1 extends DataStreamRevObjectSerializerV2 {

    public static final DataStreamRevObjectSerializerV2_1 INSTANCE = new DataStreamRevObjectSerializerV2_1();

    public DataStreamRevObjectSerializerV2_1() {
        super(FormatCommonV2_1.INSTANCE);
    }

    protected DataStreamRevObjectSerializerV2_1(FormatCommonV2 format) {
        super(format);
    }
        @Override
    public String getDisplayName() {
        return "Binary 2.1";
    }

}
