/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing.merge;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.storage.impl.PersistedIterable;
import org.locationtech.jts.geom.Envelope;

import com.google.common.collect.Lists;

/**
 * Unit test suite for {@link ConflictsUtils.DiffEntrySerializer}
 *
 */
public class MergeStatusBuilderDiffEntrySerializerTest {

    private ConflictsUtils.DiffEntrySerializer serializer;

    private ByteArrayOutputStream stream;

    private DataOutputStream out;

    @Before
    public void before() {
        serializer = new ConflictsUtils.DiffEntrySerializer();
        stream = new ByteArrayOutputStream();
        out = new DataOutputStream(stream);
    }

    @Test
    public void testFullEntry() throws IOException {
        Node ln = RevObjectTestSupport.featureNode("testNode", 1);
        Node rn = RevObjectTestSupport.featureNode("testNode", 2);

        ObjectId metadataId = RevObjectTestSupport.hashString("test");

        NodeRef left = new NodeRef(ln, "parent/path", metadataId);
        NodeRef right = new NodeRef(rn, "parent/path", metadataId);

        DiffEntry entry = new DiffEntry(left, right);

        serializer.write(out, entry);

        byte[] array = stream.toByteArray();

        DiffEntry read = serializer.read(new DataInputStream(new ByteArrayInputStream(array)));
        assertEquals(entry, read);
    }

    @Test
    public void testFullEntryNullParentPath() throws IOException {
        // null parent path is only allowed for NodeRef.ROOT named nodes
        Node ln = RevObjectFactory.defaultInstance().createNode(NodeRef.ROOT, RevTree.EMPTY_TREE_ID,
                ObjectId.NULL, TYPE.TREE, new Envelope(0, 0, 0, 0), null);
        Node rn = RevObjectFactory.defaultInstance().createNode(NodeRef.ROOT,
                RevObjectTestSupport.hashString("rnd"), ObjectId.NULL, TYPE.TREE,
                new Envelope(0, 1, 0, 1), null);

        ObjectId metadataId = RevObjectTestSupport.hashString("test");

        NodeRef left = new NodeRef(ln, null, metadataId);
        NodeRef right = new NodeRef(rn, null, metadataId);

        DiffEntry entry = new DiffEntry(left, right);

        serializer.write(out, entry);

        byte[] array = stream.toByteArray();

        DiffEntry read = serializer.read(new DataInputStream(new ByteArrayInputStream(array)));
        assertEquals(entry, read);
    }

    @Test
    public void testNoDefaultMetadataId() throws IOException {
        // null parent path is only allowed for NodeRef.ROOT named nodes
        Node ln = RevObjectFactory.defaultInstance().createNode(NodeRef.ROOT, RevTree.EMPTY_TREE_ID,
                ObjectId.NULL, TYPE.TREE, new Envelope(0, 0, 0, 0), null);
        Node rn = RevObjectFactory.defaultInstance().createNode(NodeRef.ROOT,
                RevObjectTestSupport.hashString("rnd"), ObjectId.NULL, TYPE.TREE,
                new Envelope(0, 1, 0, 1), null);

        ObjectId metadataId = ObjectId.NULL;

        NodeRef left = new NodeRef(ln, null, metadataId);
        NodeRef right = new NodeRef(rn, null, metadataId);

        DiffEntry entry = new DiffEntry(left, right);

        serializer.write(out, entry);

        byte[] array = stream.toByteArray();

        DiffEntry read = serializer.read(new DataInputStream(new ByteArrayInputStream(array)));
        assertEquals(entry, read);
    }

    @Test
    public void testNullNodeRef() throws IOException {
        // null parent path is only allowed for NodeRef.ROOT named nodes
        Node rn = RevObjectTestSupport.featureNode("testNode", 2);

        ObjectId metadataId = RevObjectTestSupport.hashString("test");

        NodeRef left = null;
        NodeRef right = new NodeRef(rn, "parent/path", metadataId);

        DiffEntry entry = new DiffEntry(left, right);

        serializer.write(out, entry);

        byte[] array = stream.toByteArray();

        DiffEntry read = serializer.read(new DataInputStream(new ByteArrayInputStream(array)));
        assertEquals(entry, read);
    }

    @Test
    public void testPersistedIterableOfNodeRefs() {
        int buffSize = 10;
        Path tmpDir = null;// use platform's default
        boolean compress = true;

        List<DiffEntry> expected = new ArrayList<>();

        try (PersistedIterable<DiffEntry> iterable = new PersistedIterable<>(tmpDir, serializer,
                buffSize, compress)) {

            ObjectId defaultMetadataId = RevObjectTestSupport.hashString("test");

            for (int i = 0; i < 1000; i++) {
                Node ln = RevObjectTestSupport.featureNode("testNode", i);
                Node rn = RevObjectTestSupport.featureNode("testNode", i, true);

                ObjectId metadataId = i % 2 == 0 ? defaultMetadataId : ObjectId.NULL;

                NodeRef left = new NodeRef(ln, "parent/path", metadataId);
                NodeRef right = new NodeRef(rn, "parent/path", metadataId);

                DiffEntry entry = new DiffEntry(left, right);
                expected.add(entry);

                iterable.add(entry);
                assertEquals(i + 1, iterable.size());
            }

            assertEquals(expected.size(), iterable.size());

            Iterator<DiffEntry> iterator = iterable.iterator();
            ArrayList<DiffEntry> actual = Lists.newArrayList(iterator);
            assertEquals(expected.size(), actual.size());
            assertEquals(expected, actual);
        }

    }
}
