/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.osm.internal.coordcache;

import org.locationtech.geogig.api.Platform;

public class MappedPointCacheTest extends PointCacheTest {

    @Override
    protected MappedPointCache createCache(Platform platform) {
        return new MappedPointCache(platform);
    }

}
