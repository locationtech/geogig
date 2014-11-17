/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.storage.datastream;

import org.locationtech.geogig.storage.ObjectSerializingFactory;
import org.locationtech.geogig.storage.RevFeatureTypeSerializationTest;

public class DataStreamFeatureTypeV2Serialization extends RevFeatureTypeSerializationTest {
    @Override
    protected ObjectSerializingFactory getObjectSerializingFactory() {
        return new DataStreamSerializationFactoryV2();
    }
}
