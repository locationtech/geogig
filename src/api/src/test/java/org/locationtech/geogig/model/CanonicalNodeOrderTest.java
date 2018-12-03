/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.RevObject.TYPE;

import com.google.common.primitives.UnsignedLong;

public class CanonicalNodeOrderTest {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void testNodeOrder() {
        Node node = RevObjectFactory.defaultInstance().createNode("Points.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0000"), ObjectId.NULL,
                TYPE.FEATURE, null, null);
        Node node2 = RevObjectFactory.defaultInstance().createNode("Lines.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0001"), ObjectId.NULL,
                TYPE.FEATURE, null, null);
        Node node3 = RevObjectFactory.defaultInstance().createNode("Lines.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0001"),
                ObjectId.valueOf("abc123000000000000001234567890abcdef0002"), TYPE.TREE, null,
                null);
        assertFalse(0 == CanonicalNodeOrder.INSTANCE.compare(node, node2));
        assertFalse(0 == CanonicalNodeOrder.INSTANCE.compare(node, node3));
        assertEquals(0, CanonicalNodeOrder.INSTANCE.compare(node2, node3));
    }

    @Test
    public void testBucket() {
        Node node = RevObjectFactory.defaultInstance().createNode("Lines.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0001"), ObjectId.NULL,
                TYPE.FEATURE, null, null);
        Node node2 = RevObjectFactory.defaultInstance().createNode("Lines.1",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0001"),
                ObjectId.valueOf("abc123000000000000001234567890abcdef0002"), TYPE.TREE, null,
                null);

        for (int i = 0; i < 8; i++) {
            assertEquals(CanonicalNodeOrder.INSTANCE.bucket(node, i),
                    CanonicalNodeOrder.INSTANCE.bucket(node2, i));
        }

        try {
            CanonicalNodeOrder.INSTANCE.bucket(node, 8);
            fail();
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }

        exception.expect(IllegalArgumentException.class);
        CanonicalNodeOrder.INSTANCE.bucket(node, -1);
    }

    @Test
    public void testNormalizedSizeLimits() {
        assertEquals(512, CanonicalNodeNameOrder.normalizedSizeLimit(0));
        assertEquals(512, CanonicalNodeNameOrder.normalizedSizeLimit(1));
        assertEquals(512, CanonicalNodeNameOrder.normalizedSizeLimit(2));
        assertEquals(256, CanonicalNodeNameOrder.normalizedSizeLimit(3));
        assertEquals(256, CanonicalNodeNameOrder.normalizedSizeLimit(4));
        assertEquals(256, CanonicalNodeNameOrder.normalizedSizeLimit(5));
        assertEquals(256, CanonicalNodeNameOrder.normalizedSizeLimit(6));
        assertEquals(256, CanonicalNodeNameOrder.normalizedSizeLimit(7));

        exception.expect(IllegalArgumentException.class);
        CanonicalNodeNameOrder.normalizedSizeLimit(-1);
    }

    @Test
    public void testHash() {
        assertEquals(590701660006484765L,
                CanonicalNodeNameOrder.INSTANCE.hashCodeLong("0").longValue());
        assertEquals(590700560494856554L,
                CanonicalNodeNameOrder.INSTANCE.hashCodeLong("1").longValue());
        assertEquals(590699460983228343L,
                CanonicalNodeNameOrder.INSTANCE.hashCodeLong("2").longValue());
        assertEquals(590698361471600132L,
                CanonicalNodeNameOrder.INSTANCE.hashCodeLong("3").longValue());
        assertEquals(287424979109030320L,
                CanonicalNodeNameOrder.INSTANCE.hashCodeLong("f1").longValue());
        assertEquals(1791227333405493115L, CanonicalNodeNameOrder.INSTANCE
                .hashCodeLong("some-rather-large-feature-identifier").longValue());

        String node1Name = "Lines.1";
        String node2Name = "Points.1";

        UnsignedLong node1Hash = CanonicalNodeNameOrder.INSTANCE.hashCodeLong(node1Name);
        UnsignedLong node2Hash = CanonicalNodeNameOrder.INSTANCE.hashCodeLong(node2Name);

        assertFalse(node1Hash.equals(node2Hash));

        assertFalse(0 == CanonicalNodeNameOrder.INSTANCE.compare(node1Hash.longValue(), node1Name,
                node2Hash.longValue(), node2Name));

        // If the bits are the same, the compare should fall back to string compare
        int nameCompare = node1Name.compareTo(node2Name);
        assertEquals(nameCompare,
                CanonicalNodeNameOrder.INSTANCE.compare(1L, node1Name, 1L, node2Name));

        for (int i = 0; i < 8; i++) {
            assertTrue(CanonicalNodeNameOrder.bucket(node1Name, i) == CanonicalNodeNameOrder
                    .bucket(node1Hash.longValue(), i));
        }
    }

    @Test
    public void testMaxBucketsForLevel() {
        assertEquals(32, CanonicalNodeNameOrder.maxBucketsForLevel(0));
        assertEquals(32, CanonicalNodeNameOrder.maxBucketsForLevel(1));
        assertEquals(32, CanonicalNodeNameOrder.maxBucketsForLevel(2));
        assertEquals(8, CanonicalNodeNameOrder.maxBucketsForLevel(3));
        assertEquals(8, CanonicalNodeNameOrder.maxBucketsForLevel(4));
        assertEquals(4, CanonicalNodeNameOrder.maxBucketsForLevel(5));
        assertEquals(4, CanonicalNodeNameOrder.maxBucketsForLevel(6));
        assertEquals(2, CanonicalNodeNameOrder.maxBucketsForLevel(7));

        assertEquals(2, CanonicalNodeNameOrder.maxBucketsForLevel(8));
        assertEquals(2, CanonicalNodeNameOrder.maxBucketsForLevel(9));
        assertEquals(2, CanonicalNodeNameOrder.maxBucketsForLevel(10));

        exception.expect(IllegalArgumentException.class);
        CanonicalNodeNameOrder.maxBucketsForLevel(-1);
    }
}
