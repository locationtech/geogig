/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.RevObjectTestUtil;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.model.internal.DAGNode.FeatureDAGNode;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.memory.HeapObjectStore;

public class DAGNodeTest {

    private TreeCache cache;

    private RevTree featuresTree;

    @Before
    public void before() {
        ObjectStore store = new HeapObjectStore();
        store.open();
        cache = mock(TreeCache.class);
        featuresTree = RevObjectTestSupport.INSTANCE.createFeaturesTree(store, "f", 512);
    }

    @Test
    public void lazyFeatureNodeCreate() {
        DAGNode node = DAGNode.featureNode(5, 511);
        assertTrue(node instanceof FeatureDAGNode);
        FeatureDAGNode fnode = (FeatureDAGNode) node;
        assertEquals(5, fnode.leafRevTreeId);
        assertEquals(511, fnode.nodeIndex);
        assertFalse("a lazy feature node can never be nil", node.isNull());
    }

    @Test
    public void lazyFeatureNodeEquals() {
        DAGNode node = DAGNode.featureNode(5, 511);
        assertEquals(node, DAGNode.featureNode(5, 511));
        assertNotEquals(node, DAGNode.featureNode(5, 510));
        assertNotEquals(node, DAGNode.featureNode(4, 511));
    }

    @Test
    public void lazyFeatureNodeResolve() {
        DAGNode node = DAGNode.featureNode(5, 511);

        when(cache.resolve(eq(5))).thenReturn(featuresTree);
        Node resolved = node.resolve(cache);
        assertNotNull(resolved);
        Node expected = featuresTree.features().get(511);
        RevObjectTestUtil.deepEquals(expected, resolved);
    }

}
