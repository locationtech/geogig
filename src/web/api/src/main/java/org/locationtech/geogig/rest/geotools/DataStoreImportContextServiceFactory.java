/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest.geotools;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Factory for retrieving {@link DataStoreImportContextService} instances.
 */
public class DataStoreImportContextServiceFactory {

    public static DataStoreImportContextService getContextService(String format) {
        // look up the context builder from the ServiceLoader
        final ServiceLoader<DataStoreImportContextService> svcLoader = ServiceLoader.load(DataStoreImportContextService.class);
        final Iterator<DataStoreImportContextService> iterator = svcLoader.iterator();
        while (iterator.hasNext()) {
            final DataStoreImportContextService context = iterator.next();
            if (context.accepts(format)) {
                return context;
            }
        }
        // didn't find an ImportContect for the specified format
        throw new IllegalArgumentException("Unsupported input format: '" + format + "'");
    }
}
