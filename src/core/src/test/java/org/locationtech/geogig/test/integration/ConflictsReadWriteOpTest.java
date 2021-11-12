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

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.plumbing.merge.ConflictsQueryOp;
import org.locationtech.geogig.plumbing.merge.ConflictsWriteOp;
import org.locationtech.geogig.repository.Conflict;

import com.google.common.collect.Sets;

public class ConflictsReadWriteOpTest extends RepositoryTestCase {

    protected @Override void setUpInternal() throws Exception {
    }

    @Test
    public void testReadWriteConflicts() throws Exception {
        Conflict conflict = new Conflict(idP1, RevObjectTestSupport.hashString("ancestor"),
                RevObjectTestSupport.hashString("ours"), RevObjectTestSupport.hashString("theirs"));
        Conflict conflict2 = new Conflict(idP2, RevObjectTestSupport.hashString("ancestor2"),
                RevObjectTestSupport.hashString("ours2"),
                RevObjectTestSupport.hashString("theirs2"));
        List<Conflict> conflicts = List.of(conflict, conflict2);
        repo.command(ConflictsWriteOp.class).setConflicts(conflicts).call();

        Set<Conflict> returnedConflicts = Sets
                .newHashSet(repo.command(ConflictsQueryOp.class).call());

        assertEquals(Set.copyOf(conflicts), returnedConflicts);
    }

}
