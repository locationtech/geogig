/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.test.functional;

import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.impl.ContextBuilderImpl;
import org.locationtech.geogig.test.TestPlatform;

/**
 * Repository {@link ContextBuilderImpl} used by functional tests to enforce the repository's
 * {@link Platform} be a {@link TestPlatform} in order to ensure the user's home directory (where
 * the {@code .getogigconfig} config file is looked for) points to the test's temporary folder
 * instead of the actual home directory of the user running the test suite. This ensures the actual
 * {@code .geogigconfig} is not overwritten by the tests that call {@code configure --global}
 *
 */
public class CLITestContextBuilder extends ContextBuilderImpl {

    private TestPlatform platform;

    public CLITestContextBuilder(TestPlatform platform) {
        this.platform = platform;
    }

    public @Override Context build(Hints hints) {
        TestPlatform contextPlatform = platform.clone();
        Hints contextHints = new Hints(hints).platform(contextPlatform);
        return super.build(contextHints);
    }
}
