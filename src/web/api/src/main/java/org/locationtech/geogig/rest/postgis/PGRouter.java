/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.rest.postgis;

import org.restlet.Router;

public class PGRouter extends Router {

    public PGRouter() {
        attach("/import.{extension}", PGImport.class);
        attach("/import", PGImport.class);
    }
}
