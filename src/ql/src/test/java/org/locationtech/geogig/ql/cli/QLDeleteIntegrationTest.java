/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.ql.cli;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.plumbing.LsTreeOp;
import org.locationtech.geogig.plumbing.LsTreeOp.Strategy;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.ql.porcelain.QLDelete;
import org.locationtech.geogig.repository.DiffObjectCount;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

public class QLDeleteIntegrationTest extends RepositoryTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Override
    public void setUpInternal() throws Exception {
        insertAndAdd(points1);
        insertAndAdd(points2);
        insertAndAdd(lines1);
        insertAndAdd(lines2);

        geogig.command(CommitOp.class).call();

        insertAndAdd(points3);
        insertAndAdd(lines3);

        insertAndAdd(points1_modified);

        geogig.command(CommitOp.class).call();
    }

    private Supplier<DiffObjectCount> delete(String query) {
        return geogig.command(QLDelete.class).setStatement(query).call();
    }

    @Test
    public void simpleDelete() {
        DiffObjectCount res = delete("delete from Points").get();
        assertEquals(3, res.getFeaturesRemoved());
        assertEquals(ImmutableSet.of(), lsTree("WORK_HEAD:Points"));
        assertEquals(ImmutableSet.of(idP1, idP2, idP3), lsTree("STAGE_HEAD:Points"));
        assertEquals(ImmutableSet.of(idP1, idP2, idP3), lsTree("HEAD:Points"));
    }

    @Test
    public void simpleFidFilterDelete() {
        DiffObjectCount res = delete("delete from Points where @id = 'Points.3'").get();
        assertEquals(1, res.getFeaturesRemoved());
        assertEquals(ImmutableSet.of(idP1, idP2), lsTree("WORK_HEAD:Points"));
        assertEquals(ImmutableSet.of(idP1, idP2, idP3), lsTree("STAGE_HEAD:Points"));
        assertEquals(ImmutableSet.of(idP1, idP2, idP3), lsTree("HEAD:Points"));
    }

    public Set<String> lsTree(String treeIsh) {
        List<NodeRef> nodes = Lists.newArrayList(geogig.command(LsTreeOp.class)
                .setReference(treeIsh).setStrategy(Strategy.DEPTHFIRST_ONLY_FEATURES).call());
        Set<String> ids = new HashSet<>(Lists.transform(nodes, (n) -> n.name()));
        return ids;
    }
}
