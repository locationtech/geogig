/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.storage.cache;

import org.locationtech.geogig.model.PriorityService;
import org.locationtech.geogig.model.ServiceFinder;

/**
 * A builder for {@link SharedCache}, intended for {@link CacheManager} to look up the default
 * interface using {@link ServiceFinder}.
 * 
 * @since 1.4
 */
public interface SharedCacheBuilder extends PriorityService {

    void setMaxSizeBytes(long maxSizeBytes);

    SharedCache build();

}
