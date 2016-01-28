/* Copyright (c) 2015 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.osm.internal.coordcache;

import org.locationtech.geogig.api.Platform;

public class MapdbPointCacheTest extends PointCacheTest {

    @Override
    protected MapdbPointCache createCache(Platform platform) {
        return new MapdbPointCache(platform);
    }

}
