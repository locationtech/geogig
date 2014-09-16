/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.fs;

import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.storage.ConfigDatabaseTest;

public class IniFileConfigDatabaseTest extends ConfigDatabaseTest<IniFileConfigDatabase> {

    protected IniFileConfigDatabase createDatabase(final Platform platform) {
        return new IniFileConfigDatabase(platform);
    }

    @Override
    protected void destroy(IniFileConfigDatabase config) {
        //
    }

}
