/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest.geopkg;

import org.restlet.Router;

/**
 * Web API Router for GeoPackage operations.
 */
public class GeoPkgRouter extends Router {

    public GeoPkgRouter() {
        initRouter();
    }

    private void initRouter() {
        attach("/import", GeoPkgImportWebOp.class);
        attach("/import.{extension}", GeoPkgImportWebOp.class);
    }
}
