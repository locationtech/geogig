/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Ben Carriel (Cornell University) - initial implementation
 */
package org.locationtech.geogig.test.integration;

import org.junit.Test;
import org.locationtech.geogig.porcelain.StatusOp;
import org.locationtech.geogig.porcelain.StatusOp.StatusSummary;

public class StatusOpTest extends RepositoryTestCase {

    @Override
    protected void setUpInternal() throws Exception {
        super.populate(true, points1);
    }

    @Test
    public void testNothingToChange() {
        StatusSummary summary = geogig.command(StatusOp.class).call();
        assertAllFieldsNotNull(summary);
        assertEquals(0, summary.getCountStaged());
        assertEquals(0, summary.getCountUnstaged());
        assertEquals(0, summary.getCountConflicts());
    }

    @Test
    public void testOneAdd() {
        try {
            super.insert(points1_modified);
        } catch (Exception e) {
            e.printStackTrace();
        }
        StatusSummary summary = geogig.command(StatusOp.class).call();
        assertAllFieldsNotNull(summary);
        assertEquals(2, summary.getCountUnstaged());
    }

    @Test
    public void testOneStaged() {
        try {
            super.insertAndAdd(points1_modified);
        } catch (Exception e) {
            e.printStackTrace();
        }
        StatusSummary summary = geogig.command(StatusOp.class).call();
        assertAllFieldsNotNull(summary);
        assertEquals(2, summary.getCountStaged());
    }

    @Test
    public void testTwoStaged() {
        try {
            super.insert(points2);
            super.insertAndAdd(points1_modified);
        } catch (Exception e) {
            e.printStackTrace();
        }
        StatusSummary summary = geogig.command(StatusOp.class).call();
        assertAllFieldsNotNull(summary);
        assertEquals(3, summary.getCountStaged());
    }

    private void assertAllFieldsNotNull(StatusSummary summary) {
        assertNotNull(summary);
        assertNotNull(summary.getStaged());
        assertNotNull(summary.getUnstaged());
        assertNotNull(summary.getConflicts());
    }
}
