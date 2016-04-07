/* Copyright (c) 2016 Boundless.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage;

import static org.junit.Assert.assertEquals;
import static org.locationtech.geogig.storage.NodePathStorageOrder.INSTANCE;
import static org.locationtech.geogig.storage.NodePathStorageOrder.maxBucketsForLevel;
import static org.locationtech.geogig.storage.NodePathStorageOrder.normalizedSizeLimit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class NodePathStorageOrderTest {

    @Rule
    public ExpectedException ex = ExpectedException.none();

    @Test
    public void testMaxBucketsForLevel() {
        assertEquals(32, maxBucketsForLevel(0));
        assertEquals(32, maxBucketsForLevel(1));
        assertEquals(32, maxBucketsForLevel(2));
        assertEquals(8, maxBucketsForLevel(3));
        assertEquals(8, maxBucketsForLevel(4));
        assertEquals(4, maxBucketsForLevel(5));
        assertEquals(4, maxBucketsForLevel(6));
        assertEquals(2, maxBucketsForLevel(7));

        assertEquals(2, maxBucketsForLevel(8));
        assertEquals(2, maxBucketsForLevel(9));
        assertEquals(2, maxBucketsForLevel(10));

        ex.expect(IllegalArgumentException.class);
        maxBucketsForLevel(-1);
    }

    @Test
    public void testNormalizedSizeLimit() {
        assertEquals(512, normalizedSizeLimit(0));
        assertEquals(512, normalizedSizeLimit(1));
        assertEquals(512, normalizedSizeLimit(2));
        assertEquals(256, normalizedSizeLimit(3));
        assertEquals(256, normalizedSizeLimit(4));
        assertEquals(256, normalizedSizeLimit(5));
        assertEquals(256, normalizedSizeLimit(6));
        assertEquals(256, normalizedSizeLimit(7));
        assertEquals(256, normalizedSizeLimit(8));
        assertEquals(256, normalizedSizeLimit(9));
        assertEquals(256, normalizedSizeLimit(10));
    }

    @Test
    public void testHashCodeLong() {
        assertEquals(590701660006484765L, INSTANCE.hashCodeLong("0").longValue());
        assertEquals(590700560494856554L, INSTANCE.hashCodeLong("1").longValue());
        assertEquals(590699460983228343L, INSTANCE.hashCodeLong("2").longValue());
        assertEquals(590698361471600132L, INSTANCE.hashCodeLong("3").longValue());
        assertEquals(287424979109030320L, INSTANCE.hashCodeLong("f1").longValue());
        assertEquals(1791227333405493115L,
                INSTANCE.hashCodeLong("some-rather-large-feature-identifier").longValue());
    }

    @Test
    public void testBucket() {
        assertEquals(1, INSTANCE.bucket("0", 0).intValue());
        assertEquals(6, INSTANCE.bucket("0", 1).intValue());
        assertEquals(19, INSTANCE.bucket("0", 2).intValue());
        assertEquals(0, INSTANCE.bucket("0", 3).intValue());
        assertEquals(5, INSTANCE.bucket("0", 4).intValue());
        assertEquals(3, INSTANCE.bucket("0", 5).intValue());
        assertEquals(2, INSTANCE.bucket("0", 6).intValue());
        assertEquals(0, INSTANCE.bucket("0", 7).intValue());

        assertEquals(22, INSTANCE.bucket("some-feature-id", 0).intValue());
        assertEquals(26, INSTANCE.bucket("some-feature-id", 1).intValue());
        assertEquals(18, INSTANCE.bucket("some-feature-id", 2).intValue());
        assertEquals(6, INSTANCE.bucket("some-feature-id", 3).intValue());
        assertEquals(4, INSTANCE.bucket("some-feature-id", 4).intValue());
        assertEquals(1, INSTANCE.bucket("some-feature-id", 5).intValue());
        assertEquals(3, INSTANCE.bucket("some-feature-id", 6).intValue());
        assertEquals(1, INSTANCE.bucket("some-feature-id", 7).intValue());

        ex.expect(IllegalArgumentException.class);
        ex.expectMessage("too deep: 8");
        INSTANCE.bucket("0", 8);
    }
}
