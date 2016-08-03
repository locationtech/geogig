/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.geogig.web.functional;

import com.google.inject.AbstractModule;

/**
 * Bind the {@link DefaultFunctionalTestContext} for use in web API functional tests.
 */
public class FunctionalTestModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(FunctionalTestContext.class).to(DefaultFunctionalTestContext.class);
    }

}
