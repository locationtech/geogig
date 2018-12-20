/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.model.impl;

import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevObjectFactoryConformanceTest;

public class RevObjectFactoryImplConformanceTest extends RevObjectFactoryConformanceTest {

    protected @Override RevObjectFactory newFactory() {
        return new RevObjectFactoryImpl();
    }

}
