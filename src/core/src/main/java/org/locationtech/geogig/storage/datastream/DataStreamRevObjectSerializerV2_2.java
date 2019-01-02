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
 * Serialization factory for serial version 2.2
 *
 * @see FormatCommonV2_2
 */
public class DataStreamRevObjectSerializerV2_2 extends DataStreamRevObjectSerializerV2_1 {

    public static final DataStreamRevObjectSerializerV2_2 INSTANCE = new DataStreamRevObjectSerializerV2_2();

    public DataStreamRevObjectSerializerV2_2() {
        super(FormatCommonV2_2.INSTANCE);
    }

    @Override
    public String getDisplayName() {
        return "Binary 2.2";
    }

}