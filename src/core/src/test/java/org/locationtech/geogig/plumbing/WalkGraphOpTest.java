/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import static com.google.common.collect.Lists.newCopyOnWriteArrayList;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.BucketIndex;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;

public class WalkGraphOpTest extends RepositoryTestCase {

    private static class AccumulatingListener implements WalkGraphOp.Listener {

        public List<Object> events = newCopyOnWriteArrayList();

        public List<String> sevents = newCopyOnWriteArrayList();

        @Override
        public void starTree(NodeRef treeNode) {
            events.add(treeNode);
            sevents.add("TREE " + treeNode.name());
        }

        @Override
        public void featureType(RevFeatureType ftype) {
            events.add(ftype);
            sevents.add("FEATURETYPE " + ftype.getName().getLocalPart());
        }

        @Override
        public void feature(NodeRef featureNode) {
            events.add(featureNode);
            sevents.add("FEATURE " + featureNode.name());
        }

        @Override
        public void endTree(NodeRef treeNode) {
            sevents.add("END TREE " + treeNode.name());
        }

        @Override
        public void endBucket(BucketIndex bucketIndex, Bucket bucket) {
            sevents.add("END BUCKET");
        }

        @Override
        public void commit(RevCommit commit) {
            events.add(commit);
            sevents.add("COMMIT " + commit.getMessage());
        }

        @Override
        public void bucket(BucketIndex bucketIndex, Bucket bucket) {
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

        Set<String> expected = ImmutableSet.of(//
                "COMMIT Lines.1", //
                "TREE ", //
                "FEATURETYPE Points", //
                "TREE Points", //
                "FEATURETYPE Lines", //
                "TREE Lines", //
                "FEATURE Points.1", //
                "END TREE Points", //
                "FEATURE Lines.1", //
                "END TREE Lines", //
                "END TREE ");

        assertEquals(expected, new HashSet<>(sevents));
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

        String expected = "Object NodeRef[Points/Points.1 -> " + oid + "] not found.";
        exception.expect(IllegalStateException.class);
        exception.expectMessage(expected);

        AccumulatingListener listener = new AccumulatingListener();
        geogig.command(WalkGraphOp.class).setReference("HEAD").setListener(listener).call();
    }
}
