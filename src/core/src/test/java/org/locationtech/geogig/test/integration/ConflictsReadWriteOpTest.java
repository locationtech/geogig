/* Copyright (c) 2013-2016 Boundless and others.
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
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
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
        Conflict conflict = new Conflict(idP1, RevObjectTestSupport.hashString("ancestor"), RevObjectTestSupport.hashString("ours"),
                RevObjectTestSupport.hashString("theirs"));
        Conflict conflict2 = new Conflict(idP2, RevObjectTestSupport.hashString("ancestor2"), RevObjectTestSupport.hashString("ours2"),
                RevObjectTestSupport.hashString("theirs2"));
        ArrayList<Conflict> conflicts = Lists.newArrayList(conflict, conflict2);
        geogig.command(ConflictsWriteOp.class).setConflicts(conflicts).call();

        Set<Conflict> returnedConflicts = Sets
                .newHashSet(geogig.command(ConflictsQueryOp.class).call());

        assertEquals(Sets.newHashSet(conflicts), returnedConflicts);
    }

}
