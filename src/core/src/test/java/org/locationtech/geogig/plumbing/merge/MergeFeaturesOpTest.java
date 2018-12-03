/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing.merge;

import org.junit.Test;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.opengis.feature.Feature;

public class MergeFeaturesOpTest extends RepositoryTestCase {

    @Override
    protected void setUpInternal() throws Exception {

    }

    @Test
    public void testMergeFeatureSameGeometryChange() throws Exception {
        String ancestorLine = "LINESTRING (-75.1195282 38.7801263, -75.1195626 38.7806208, -75.1195701 38.780762, -75.1195916 38.7816402, -75.1195154 38.7820072)";
        String leftLine = "LINESTRING (-75.1195282 38.7801263, -75.1195626 38.7806208, -75.1195645 38.7807768, -75.1195916 38.7816402, -75.1195841 38.7817429, -75.1195702 38.7818159, -75.1195333 38.7819121, -75.119487 38.7819971)";
        String rightLine = "LINESTRING (-75.1195282 38.7801263, -75.1195626 38.7806208, -75.1195645 38.7807768, -75.1195916 38.7816402, -75.1195841 38.7817429, -75.1195702 38.7818159, -75.1195333 38.7819121, -75.119487 38.7819971)";

        final String fid = "112233";
        final Feature ancestor = super.feature(linesType, fid, "secondary", 1, ancestorLine);
        final Feature left = super.feature(linesType, fid, "secondary", 1, leftLine);
        final Feature right = super.feature(linesType, fid, "primary", 1, rightLine);

        final String childPath = NodeRef.appendChild(linesName, fid);
        final NodeRef ancestorRef;
        final NodeRef masterChangeRef;
        final NodeRef branchChangeRef;

        super.insertAndAdd(ancestor);
        super.commit("common ancestor");
        ancestorRef = geogig.command(FindTreeChild.class).setParent(repo.workingTree().getTree())
                .setChildPath(childPath).call().get();

        geogig.command(BranchCreateOp.class).setName("branch").call();

        super.insertAndAdd(left);
        super.commit("master change");

        masterChangeRef = geogig.command(FindTreeChild.class)
                .setParent(repo.workingTree().getTree()).setChildPath(childPath).call().get();

        assertEquals("branch", geogig.command(CheckoutOp.class).setSource("branch").call()
                .getNewRef().localName());
        super.insertAndAdd(right);
        super.commit("branch change");

        branchChangeRef = geogig.command(FindTreeChild.class)
                .setParent(repo.workingTree().getTree()).setChildPath(childPath).call().get();

        Feature merged = geogig.command(MergeFeaturesOp.class)//
                .setAncestorFeature(ancestorRef)//
                .setFirstFeature(masterChangeRef)//
                .setSecondFeature(branchChangeRef)//
                .call();
        assertNotNull(merged);
    }
}
