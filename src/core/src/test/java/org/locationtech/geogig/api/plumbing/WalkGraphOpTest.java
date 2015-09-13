/* Copyright (c) 2015 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.plumbing;

import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.api.Bucket;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

public class WalkGraphOpTest extends RepositoryTestCase {

    private static class AccumulatingListener implements WalkGraphOp.Listener {

        public List<Object> events = new ArrayList<>();

        public List<String> sevents = new ArrayList<>();

        @Override
        public void starTree(Node treeNode) {
            events.add(treeNode);
            sevents.add("TREE");
        }

        @Override
        public void featureType(RevFeatureType ftype) {
            events.add(ftype);
            sevents.add("FEATURETYPE");
        }

        @Override
        public void feature(Node featureNode) {
            events.add(featureNode);
            sevents.add("FEATURE");
        }

        @Override
        public void endTree(Node treeNode) {
            sevents.add("END TREE");
        }

        @Override
        public void endBucket(int bucketIndex, int bucketDepth, Bucket bucket) {
            sevents.add("END BUCKET");
        }

        @Override
        public void commit(RevCommit commit) {
            events.add(commit);
            sevents.add("COMMIT");
        }

        @Override
        public void bucket(int bucketIndex, int bucketDepth, Bucket bucket) {
            events.add(bucket);
            sevents.add("END BUCKET");
        }
    };

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Override
    protected void setUpInternal() throws Exception {
        // do nothing, call populate() where needed
    }

    @Test
    public void testListenerNotProvided() {
        exception.expect(IllegalStateException.class);
        exception.expectMessage("Listener not provided");
        geogig.command(WalkGraphOp.class).setReference("HEAD").call();
    }

    @Test
    public void testRefNotProvided() {
        exception.expect(IllegalStateException.class);
        exception.expectMessage("Reference not provided");
        geogig.command(WalkGraphOp.class).setListener(new AccumulatingListener()).call();
    }

    @Test
    public void testInvalidReference() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Can't resolve reference ");
        geogig.command(WalkGraphOp.class).setListener(new AccumulatingListener())
                .setReference("bad_ref").call();
    }

    @Test
    public void testRefDoesNotResolveToTree() throws Exception {
        super.populate(true, points1).get(0);

        String notATreeRef;
        {
            Optional<ObjectId> featureRef = geogig.command(RevParse.class)
                    .setRefSpec("HEAD:" + pointsName + "/" + idP1).call();
            notATreeRef = featureRef.get().toString();
        }

        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("can't be resolved to a tree");
        geogig.command(WalkGraphOp.class).setListener(new AccumulatingListener())
                .setReference(notATreeRef).call();
    }

    @Test
    public void testEmptyHead() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("does not exist");
        geogig.command(WalkGraphOp.class).setListener(new AccumulatingListener())
                .setReference("HEAD").call();
    }

    @Test
    public void testHead() throws Exception {
        final boolean oneCommitPerFeature = true;
        populate(oneCommitPerFeature, points1, lines1);

        AccumulatingListener listener = new AccumulatingListener();
        geogig.command(WalkGraphOp.class).setReference("HEAD").setListener(listener).call();

        List<String> sevents = listener.sevents;

        List<String> expected = ImmutableList.of(//
                "COMMIT",//
                "TREE",//
                "FEATURETYPE",//
                "TREE",//
                "FEATURE",//
                "END TREE", //
                "FEATURETYPE",//
                "TREE",//
                "FEATURE",//
                "END TREE",//
                "END TREE");

        assertEquals(expected, sevents);
    }

    @Test
    public void testObjectDoesNotExist() throws Exception {
        final boolean oneCommitPerFeature = true;
        populate(oneCommitPerFeature, points1, lines1);

        Optional<ObjectId> point1Oid = geogig.command(RevParse.class)
                .setRefSpec("HEAD:" + pointsName + "/" + idP1).call();
        assertTrue(point1Oid.isPresent());

        ObjectId oid = point1Oid.get();
        geogig.getRepository().objectDatabase().delete(oid);

        String expected = "Object BoundedFeatureNode[Points.1 -> " + oid + "] not found.";
        exception.expect(IllegalStateException.class);
        exception.expectMessage(expected);

        AccumulatingListener listener = new AccumulatingListener();
        geogig.command(WalkGraphOp.class).setReference("HEAD").setListener(listener).call();
    }
}
