/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 * 
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.plumbing;

import org.geotools.data.DataStore;

import com.google.common.base.Supplier;

/**
 * Extends the {@link com.google.common.base.Supplier} interface to provide for a way to request
 * resource cleanup, if applicable to the {@link org.geotools.data.DataStore DataStore}.
 */
public interface DataStoreSupplier extends Supplier<DataStore> {
    void cleanupResources();
}
