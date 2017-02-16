/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.datastream.v2_2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.locationtech.geogig.model.impl.RevObjectTestSupport.hashString;
import static org.locationtech.geogig.storage.datastream.v2_2.TestSupport.assertEqualsFully;
import static org.locationtech.geogig.storage.datastream.v2_2.TestSupport.nodes;
import static org.locationtech.geogig.storage.datastream.v2_2.TestSupport.tree;

import java.io.IOException;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.HashObject;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.memory.HeapObjectStore;

import com.google.common.collect.ImmutableList;

public class RevTreeFormatTest {

    private ObjectStore store;

    @Before
    public void before() {
        store = new HeapObjectStore();
        store.open();
    }

    @After
    public void after() {
        store.close();
    }

    @Test
    public void testEmptyTree() throws IOException {
        RevTree orig = RevTree.EMPTY;
        RevTree decoded = encodeDecode(orig);
        assertSame(orig, decoded);
    }

    @Test
    public void testFeatureLeafWithNonRepeatedMetadataIds() throws IOException {
        RevTree tree;
        List<Node> tNodes = nodes(TYPE.TREE, 1024, true, true, true);
        List<Node> fNodes = nodes(TYPE.FEATURE, 1024, true, true, true);
        tree = tree(2048, tNodes, fNodes, null);
        encodeDecode(tree);
    }

    @Test
    public void testFeatureLeafWithRepeatedMetadataIdsNoExtraData() throws IOException {
        RevTree tree;
        List<ObjectId> repeatingMdIds = ImmutableList.of(//
                hashString("mdid1"), //
                hashString("mdid2"), //
                hashString("mdid3"));
        List<Node> tNodes = nodes(TYPE.TREE, 1024, repeatingMdIds, true, false);
        List<Node> fNodes = nodes(TYPE.FEATURE, 1024, repeatingMdIds, true, false);
        tree = tree(2048, tNodes, fNodes, null);
        encodeDecode(tree);
    }

    @Test
    public void testFeatureLeafWithRepeatedMetadataIdsAndExtraData() throws IOException {
        RevTree tree;
        List<ObjectId> repeatingMdIds = ImmutableList.of(//
                hashString("mdid1"), //
                hashString("mdid2"), //
                hashString("mdid3"));
        List<Node> tNodes = nodes(TYPE.TREE, 1024, repeatingMdIds, true, true);
        List<Node> fNodes = nodes(TYPE.FEATURE, 1024, repeatingMdIds, true, true);
        tree = tree(2048, tNodes, fNodes, null);
        encodeDecode(tree);
    }

    @Test
    public void testBucketsTree() throws IOException {
        RevTree tree;
        SortedMap<Integer, Bucket> buckets = new TreeMap<>();
        buckets.put(1, Bucket.create(hashString("b1"), null));
        tree = tree(1024, null, null, buckets);
        encodeDecode(tree);
    }

    @Test
    public void testFullRoundtrip() throws IOException {
        List<Node> tNodes = nodes(TYPE.TREE, 512, true, true, true);
        List<Node> fNodes = nodes(TYPE.FEATURE, 512, false, true, true);
        final RevTree orig = tree(2048, tNodes, fNodes, null);

        final byte[] encoded = RevTreeFormat.encode(orig);
        assertNotNull(encoded);
        RevTree decoded = RevTreeFormat.decode(orig.getId(), encoded);
        final byte[] encoded2 = RevTreeFormat.encode(decoded);
        RevTree decoded2 = RevTreeFormat.decode(decoded.getId(), encoded2);

        assertEqualsFully(orig, decoded);
        assertEqualsFully(orig, decoded2);
        assertEqualsFully(decoded, decoded2);
    }

    @Test
    public void testConsistentHashing() throws IOException {
        List<Node> tNodes = nodes(TYPE.TREE, 512, true, true, true);
        List<Node> fNodes = nodes(TYPE.FEATURE, 512, false, true, true);
        final RevTree orig = tree(2048, tNodes, fNodes, null);

        final byte[] encoded = RevTreeFormat.encode(orig);
        assertNotNull(encoded);
        RevTree decoded = RevTreeFormat.decode(orig.getId(), encoded);
        
        ObjectId expected = new HashObject().setObject(orig).call();
        ObjectId actual = new HashObject().setObject(decoded).call();
        assertEquals(expected, actual);
    }

    private RevTree encodeDecode(RevTree orig) throws IOException {
        final byte[] encoded = RevTreeFormat.encode(orig);
        assertNotNull(encoded);
        RevTree decoded = RevTreeFormat.decode(orig.getId(), encoded);
        assertEquals(orig, decoded);
        // equals only checks for objectId

        assertEquals(TYPE.TREE, decoded.getType());
        assertEquals(orig.size(), decoded.size());
        assertEquals(orig.numTrees(), decoded.numTrees());
        assertEqualsFully(orig.trees(), decoded.trees());
        assertEqualsFully(orig.features(), decoded.features());
        assertEquals(orig.buckets(), decoded.buckets());

        return decoded;
    }

}
