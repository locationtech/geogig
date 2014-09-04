/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.osm.internal.coordcache;

import org.junit.Ignore;
import org.locationtech.geogig.api.Platform;

@Ignore
public class BDBJEPointCacheTest extends PointCacheTest {

    @Override
    protected BDBJEPointCache createCache(Platform platform) {
        return new BDBJEPointCache(platform);
    }

}
