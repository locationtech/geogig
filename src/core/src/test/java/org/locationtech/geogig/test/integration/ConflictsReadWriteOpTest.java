/* Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.test.integration;

import java.util.ArrayList;
import java.util.Set;

import org.junit.Test;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.plumbing.merge.ConflictsQueryOp;
import org.locationtech.geogig.plumbing.merge.ConflictsWriteOp;
import org.locationtech.geogig.repository.Conflict;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class ConflictsReadWriteOpTest extends RepositoryTestCase {

    @Override
    protected void setUpInternal() throws Exception {
    }

    @Test
    public void testReadWriteConflicts() throws Exception {
        Conflict conflict = new Conflict(idP1, RevObjects.forString("ancestor"), RevObjects.forString("ours"),
                RevObjects.forString("theirs"));
        Conflict conflict2 = new Conflict(idP2, RevObjects.forString("ancestor2"), RevObjects.forString("ours2"),
                RevObjects.forString("theirs2"));
        ArrayList<Conflict> conflicts = Lists.newArrayList(conflict, conflict2);
        geogig.command(ConflictsWriteOp.class).setConflicts(conflicts).call();

        Set<Conflict> returnedConflicts = Sets
                .newHashSet(geogig.command(ConflictsQueryOp.class).call());

        assertEquals(Sets.newHashSet(conflicts), returnedConflicts);
    }

}
