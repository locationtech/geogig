/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.gui.internal;

import org.junit.Ignore;
import org.junit.Test;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

public class MapPaneTest extends RepositoryTestCase {

    @Override
    protected void setUpInternal() throws Exception {
        insertAndAdd(points1, points2, points3);
        // commit("points");
        insertAndAdd(lines1, lines2, lines3);
        // commit("lines");
    }

    @Test
    @Ignore
    public void show() throws Exception {
        GeoGIG geogig = getGeogig();
        Repository repository = geogig.getRepository();
        MapPane mapPane = new MapPane(repository);
        mapPane.show();
        Thread.sleep(2000);
    }
}
