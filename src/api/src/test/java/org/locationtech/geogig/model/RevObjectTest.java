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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;

import org.junit.Test;

public class RevObjectTest {

    @Test
    public void testTypeValues() {
        assertEquals(RevObject.TYPE.COMMIT, RevObject.TYPE.valueOf(RevCommit.class));
        assertEquals(RevObject.TYPE.FEATURE, RevObject.TYPE.valueOf(RevFeature.class));
        assertEquals(RevObject.TYPE.FEATURETYPE, RevObject.TYPE.valueOf(RevFeatureType.class));
        assertEquals(RevObject.TYPE.TAG, RevObject.TYPE.valueOf(RevTag.class));
        assertEquals(RevObject.TYPE.TREE, RevObject.TYPE.valueOf(RevTree.class));

        assertEquals(RevObject.TYPE.COMMIT, RevObject.TYPE.valueOf(RevObject.TYPE.COMMIT.value()));
        assertEquals(RevObject.TYPE.FEATURE,
                RevObject.TYPE.valueOf(RevObject.TYPE.FEATURE.value()));
        assertEquals(RevObject.TYPE.FEATURETYPE,
                RevObject.TYPE.valueOf(RevObject.TYPE.FEATURETYPE.value()));
        assertEquals(RevObject.TYPE.TAG, RevObject.TYPE.valueOf(RevObject.TYPE.TAG.value()));
        assertEquals(RevObject.TYPE.TREE, RevObject.TYPE.valueOf(RevObject.TYPE.TREE.value()));
    }

    @Test
    public void testNaturalOrder() {
        assertEquals(0, RevTree.EMPTY.compareTo(RevTree.EMPTY));
        ObjectId oId1 = ObjectId.valueOf("abc123000000000000001234567890abcdef0001");
        ObjectId oId2 = ObjectId.valueOf("abc123000000000000001234567890abcdef0002");
        RevFeature f1 = RevObjectFactory.defaultInstance().createFeature(oId1,
                Collections.singletonList("1"));
        RevFeature f2 = RevObjectFactory.defaultInstance().createFeature(oId2,
                Collections.singletonList("2"));
        assertTrue(f1.compareTo(f2) < 0);
        assertTrue(f2.compareTo(f1) > 0);
    }

    @Test
    public void testToString() {
        ObjectId oId = ObjectId.valueOf("abc123000000000000001234567890abcdef0001");
        StringBuilder builder = new StringBuilder();
        RevObjects.toString(oId, 5, builder);
        assertEquals("abc1230000", builder.toString());
        builder = new StringBuilder();
        RevObjects.toString(oId, ObjectId.NUM_BYTES, builder);
        assertEquals("abc123000000000000001234567890abcdef0001", builder.toString());
        StringBuilder builder2 = RevObjects.toString(oId, ObjectId.NUM_BYTES, null);
        assertEquals("abc123000000000000001234567890abcdef0001", builder2.toString());

        try {
            RevObjects.toString(oId, -1, null);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            RevObjects.toString(oId, ObjectId.NUM_BYTES + 1, null);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            RevObjects.toString(null, -1, null);
            fail();
        } catch (NullPointerException e) {
            // expected
        }

    }
}
