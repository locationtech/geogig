/* Copyright (c) 2015 SWM Services GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Sebastian Schmidt (SWM Services GmbH) - initial implementation
 */
package org.locationtech.geogig.storage.integration.mapdb;

//TODO what is this test for?

import org.junit.Test;
import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.di.GeogigModule;

import com.google.inject.Guice;
import com.google.inject.util.Modules;

public class MapdbRevTreeBuilderTest extends
        org.locationtech.geogig.test.integration.RevTreeBuilderTest {
    @Override
    protected Context createInjector() {
        return Guice.createInjector(
                Modules.override(new GeogigModule()).with(new MapdbTestStorageModule()))
                .getInstance(Context.class);
    }

    @Test
    // $codepro.audit.disable unnecessaryOverride
    public void testPutIterate() throws Exception {
        super.testPutIterate();
    }

    @Test
    // $codepro.audit.disable unnecessaryOverride
    public void testPutRandomGet() throws Exception {
        super.testPutRandomGet();
    }

    public static void main(String... args) {
        MapdbRevTreeBuilderTest test = new MapdbRevTreeBuilderTest();
        try {
            test.setUp();
            test.testPutRandomGet();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.exit(0);
        }
    }
}
