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
 * Factory for retreiving ImportContextService instances.
 */
public class ImportContextServiceFactory {

    public static ImportContextService getContextService(String format) {
        // look up the context builder from the ServiceLoader
        final ServiceLoader<ImportContextService> svcLoader = ServiceLoader.load(
            ImportContextService.class);
        final Iterator<ImportContextService> iterator = svcLoader.iterator();
        while (iterator.hasNext()) {
            final ImportContextService context = iterator.next();
            if (context.accepts(format)) {
                return context;
            }
        }
        // didn't find an ImportContect for the specified format
        throw new RuntimeException("No ImportContext builder for format \"" + format + "\"");
    }
}
