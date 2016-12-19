/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.datastream;

import org.locationtech.geogig.storage.impl.ObjectSerializingFactory;
import org.locationtech.geogig.storage.impl.RevTreeSerializationTest;

public class DataStreamRevTreeSerializationTest extends RevTreeSerializationTest {
    @Override
    protected ObjectSerializingFactory getObjectSerializingFactory() {
        return new DataStreamSerializationFactoryV1();
    }
}
