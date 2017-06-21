/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql.functional;

import org.locationtech.geogig.storage.postgresql.PGTestProperties;

import cucumber.api.PendingException;

public class PGTestUtil {

    public static void checkPgTestsEnabled() throws PendingException {
        if (!PGTestProperties.isTestsEnabled()) {
            System.err.println(
                    "#######################################################################################################");
            System.err.println(
                    "#                                                                                                     #");
            System.err.println(
                    "#     PostgreSQL functional tests disabled. Configure $HOME/.geogig-pg-backend-tests.properties       #");
            System.err.println(
                    "#                                                                                                     #");
            System.err.println(
                    "#######################################################################################################");
            throw new PendingException();
        }
    }
}
