/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.flatbuffers;

import org.locationtech.geogig.storage.RevObjectSerializer;
import org.locationtech.geogig.storage.impl.RevObjectSerializerConformanceTest;

public class FlatBuffersRevObjectSerializerStreamingTest
        extends RevObjectSerializerConformanceTest {

    protected @Override RevObjectSerializer newObjectSerializer() {
        return new FlatBuffersRevObjectSerializer(true);
    }

}
