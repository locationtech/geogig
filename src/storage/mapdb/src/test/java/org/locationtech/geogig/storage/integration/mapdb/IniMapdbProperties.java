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

import org.locationtech.geogig.test.integration.OnlineTestProperties;


//TODO review: do we need it, it is at the right place (online test Properties?)? Do we need Properties at all?
public class IniMapdbProperties extends OnlineTestProperties {

    public IniMapdbProperties() {
        super(".geogig-mapdb-tests.properties", "mapdb.key1", "value1",
                "mapdb.key2", "value2");
    }
}
