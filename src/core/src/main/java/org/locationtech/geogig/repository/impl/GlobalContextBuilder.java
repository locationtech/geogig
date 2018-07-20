/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository.impl;

public class GlobalContextBuilder {

    private static ContextBuilder builder = new PluginsContextBuilder();

    public static ContextBuilder builder() {
        return builder;
    }

    public static void builder(ContextBuilder builder) {
        GlobalContextBuilder.builder = builder;
    }

}
