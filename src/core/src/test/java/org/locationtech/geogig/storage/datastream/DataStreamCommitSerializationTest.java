/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.storage.datastream;

import org.locationtech.geogig.storage.ObjectSerializingFactory;
import org.locationtech.geogig.storage.RevCommitSerializationTest;

public class DataStreamCommitSerializationTest extends RevCommitSerializationTest {

    @Override
    protected ObjectSerializingFactory getObjectSerializingFactory() {
        return new DataStreamSerializationFactoryV1();
    }

}
