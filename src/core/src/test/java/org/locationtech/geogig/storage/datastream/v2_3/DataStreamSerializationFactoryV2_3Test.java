/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.datastream.v2_3;

import org.locationtech.geogig.storage.datastream.DataStreamSerializationFactoryV2_2Test;
import org.locationtech.geogig.storage.impl.ObjectSerializingFactory;

public class DataStreamSerializationFactoryV2_3Test extends DataStreamSerializationFactoryV2_2Test {

    @Override
    protected ObjectSerializingFactory getObjectSerializingFactory() {
        return DataStreamSerializationFactoryV2_3.INSTANCE;
    }

}
