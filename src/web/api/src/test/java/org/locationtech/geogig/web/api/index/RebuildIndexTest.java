/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.web.api.index;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import javax.json.JsonObject;

import org.junit.Test;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.porcelain.index.CreateQuadTree;
import org.locationtech.geogig.porcelain.index.Index;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.repository.TestParams;
import org.locationtech.geogig.test.TestData;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.ParameterSet;

import com.google.common.base.Optional;

public class RebuildIndexTest extends AbstractIndexWebOpTest {

    @Override
    protected String getRoute() {
        return "rebuild";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return RebuildIndex.class;
    }

    @Override
    protected boolean requiresTransaction() {
        return false;
    }

    @Test
    public void testBuildParameters() {
        ParameterSet options = TestParams.of("treeRefSpec", "points", "geometryAttributeName",
                "the_geom");

        RebuildIndex op = (RebuildIndex) buildCommand(options);
        assertEquals("points", op.treeRefSpec);
        assertEquals("the_geom", op.geometryAttributeName);
    }

    @Test
    public void testRebuildIndex() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        Index index = geogig.command(CreateQuadTree.class).setTreeRefSpec("Points").call();
        IndexInfo indexInfo = index.info();

        ParameterSet options = TestParams.of("treeRefSpec", "Points");

        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        // There are 4 unique canonical trees for Points in the history since two commits share the
        // same tree (branch1, and the merge of branch1)
        assertEquals(4, response.getInt("treesRebuilt"));

        // make sure old commits are indexed
        ObjectId canonicalFeatureTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish("HEAD:Points").call().get();
        Optional<ObjectId> indexedTreeId = geogig.indexDatabase().resolveIndexedTree(indexInfo,
                canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        // make sure old commits are indexed
        canonicalFeatureTreeId = geogig.command(ResolveTreeish.class).setTreeish("HEAD~1:Points")
                .call().get();
        indexedTreeId = geogig.indexDatabase().resolveIndexedTree(indexInfo,
                canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        canonicalFeatureTreeId = geogig.command(ResolveTreeish.class).setTreeish("branch1:Points")
                .call().get();
        indexedTreeId = geogig.indexDatabase().resolveIndexedTree(indexInfo,
                canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        canonicalFeatureTreeId = geogig.command(ResolveTreeish.class).setTreeish("branch1~1:Points")
                .call().get();
        indexedTreeId = geogig.indexDatabase().resolveIndexedTree(indexInfo,
                canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        canonicalFeatureTreeId = geogig.command(ResolveTreeish.class).setTreeish("branch2:Points")
                .call().get();
        indexedTreeId = geogig.indexDatabase().resolveIndexedTree(indexInfo,
                canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());
    }
}
