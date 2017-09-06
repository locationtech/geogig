/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import static org.locationtech.geogig.model.impl.RevObjectTestSupport.createCommits;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

import com.google.common.base.Optional;

/**
 *
 */
public class RevParseTest extends RepositoryTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    private ObjectId poId1;

    private ObjectId loId1;

    private ObjectId treeId;

    private ObjectId commitId1;

    private ObjectId commitId2;

    private ObjectId commitId3;

    private ObjectId commitId4;

    @Before
    public void setUpRepo() throws Exception {
        poId1 = insertAndAdd(points1);
        RevCommit commit = geogig.command(CommitOp.class).setMessage("Commit1").call();
        commitId1 = commit.getId();
        treeId = commit.getTreeId();

        insertAndAdd(points2);
        commit = geogig.command(CommitOp.class).setMessage("Commit2").call();
        commitId2 = commit.getId();

        insertAndAdd(points3);
        commit = geogig.command(CommitOp.class).setMessage("Commit3").call();
        commitId3 = commit.getId();

        geogig.command(BranchCreateOp.class).setName("branch1").setAutoCheckout(true).call();

        loId1 = insertAndAdd(lines1);
        commit = geogig.command(CommitOp.class).setMessage("Commit4").call();
        commitId4 = commit.getId();

        insertAndAdd(lines2);
        commit = geogig.command(CommitOp.class).setMessage("Commit5").call();
        commit.getId();

        insertAndAdd(lines3);
        commit = geogig.command(CommitOp.class).setMessage("Commit6").call();
        commit.getId();

        geogig.command(CheckoutOp.class).setSource("master").call();
    }

    @Override
    protected void setUpInternal() throws Exception {
        injector.configDatabase().put("user.name", "groldan");
        injector.configDatabase().put("user.email", "groldan@boundlessgeo.com");
    }

    @Test
    public void testRevParseWithNoRefSpec() {
        exception.expect(IllegalStateException.class);
        geogig.command(RevParse.class).call();
    }

    @Test
    public void testRevParse() {
        Optional<ObjectId> objectId = geogig.command(RevParse.class).setRefSpec("master").call();
        assertEquals(commitId3, objectId.get());

        objectId = geogig.command(RevParse.class).setRefSpec("WORK_HEAD:Points/Points.1").call();
        assertEquals(poId1, objectId.get());

        objectId = geogig.command(RevParse.class).setRefSpec("branch1:Lines/Lines.1").call();
        assertEquals(loId1, objectId.get());

        objectId = geogig.command(RevParse.class).setRefSpec("branch1^1^1").call();
        assertEquals(commitId4, objectId.get());

        objectId = geogig.command(RevParse.class).setRefSpec("branch1^^").call();
        assertEquals(commitId4, objectId.get());

        objectId = geogig.command(RevParse.class).setRefSpec("master~2").call();
        assertEquals(commitId1, objectId.get());

        objectId = geogig.command(RevParse.class).setRefSpec("branch1^2").call();
        assertEquals(Optional.absent(), objectId);

        objectId = geogig.command(RevParse.class).setRefSpec("master^").call();
        assertEquals(commitId2, objectId.get());

        objectId = geogig.command(RevParse.class).setRefSpec("master^0").call();
        assertEquals(commitId3, objectId.get());

        objectId = geogig.command(RevParse.class).setRefSpec("HEAD").call();
        assertEquals(commitId3, objectId.get());

        objectId = geogig.command(RevParse.class).setRefSpec(commitId1.toString() + "^1").call();
        assertEquals(Optional.absent(), objectId);

        objectId = geogig.command(RevParse.class).setRefSpec(ObjectId.NULL.toString() + "^").call();
        assertEquals(Optional.absent(), objectId);

        objectId = geogig.command(RevParse.class).setRefSpec(ObjectId.NULL.toString()).call();
        assertEquals(ObjectId.NULL, objectId.get());
        objectId = geogig.command(RevParse.class).setRefSpec(
                ObjectId.NULL.toString().substring(0, ObjectId.NULL.toString().length() - 10))
                .call();
        assertEquals(ObjectId.NULL, objectId.get());

        objectId = geogig.command(RevParse.class).setRefSpec(commitId1.toString() + "~1").call();
        assertEquals(Optional.absent(), objectId);

        objectId = geogig.command(RevParse.class)
                .setRefSpec(commitId1.toString().substring(0, commitId1.toString().length() - 2))
                .call();
        assertEquals(commitId1, objectId.get());

        objectId = geogig.command(RevParse.class).setRefSpec(commitId1.toString() + "~a").call();
        assertEquals(Optional.absent(), objectId);

        objectId = geogig.command(RevParse.class).setRefSpec(commitId1.toString() + "^{commit}")
                .call();
        assertEquals(commitId1, objectId.get());

        objectId = geogig.command(RevParse.class).setRefSpec(poId1.toString() + "^{feature}")
                .call();
        assertEquals(poId1, objectId.get());

        objectId = geogig.command(RevParse.class).setRefSpec(treeId.toString() + "^{tree}").call();
        assertEquals(treeId, objectId.get());

        objectId = geogig.command(RevParse.class).setRefSpec("master^{commit}").call();
        assertEquals(commitId3, objectId.get());

        // TODO: Make a case for Tags when they actually do something

        objectId = geogig.command(RevParse.class)
                .setRefSpec(RevObjectTestSupport.hashString("NotAFeature").toString()).call();
        assertEquals(Optional.absent(), objectId);
    }

    @Test
    public void testRevParseWithFeatureObjectIdAndDelimiter() {
        exception.expect(IllegalArgumentException.class);
        geogig.command(RevParse.class).setRefSpec(loId1.toString() + "^").call();
    }

    @Test
    public void testRevParseWithFeatureCheckIfCommit() {
        exception.expect(IllegalArgumentException.class);
        geogig.command(RevParse.class).setRefSpec(loId1.toString() + "^0").call();
    }

    @Test
    public void testRevParseWithInvalidRefSpec() {
        Optional<ObjectId> oid = geogig.command(RevParse.class)
                .setRefSpec("WORK_HEAD:Lines/Lines.1").call();
        assertFalse(oid.isPresent());
    }

    @Test
    public void testRevParseVerifyToWrongType() {
        exception.expect(IllegalArgumentException.class);
        geogig.command(RevParse.class).setRefSpec(poId1.toString() + "^{commit}").call();
    }

    @Test
    public void testRevParseVerifyWithInvalidType() {
        exception.expect(IllegalArgumentException.class);
        geogig.command(RevParse.class).setRefSpec(poId1.toString() + "^{blah}").call();
    }

    @Test
    public void testResolveToMultipleIds() {
        ConflictsDatabase mockConflictsDb = mock(ConflictsDatabase.class);
        ObjectDatabase mockdb = mock(ObjectDatabase.class);
        Context mockCommands = mock(Context.class);
        RefParse mockRefParse = mock(RefParse.class);

        when(mockRefParse.setName(anyString())).thenReturn(mockRefParse);
        when(mockCommands.command(eq(RefParse.class))).thenReturn(mockRefParse);
        Optional<Ref> ref = Optional.absent();
        when(mockRefParse.call()).thenReturn(ref);

        List<ObjectId> oIds = Arrays.asList(RevObjectTestSupport.hashString("Object 1"),
                RevObjectTestSupport.hashString("Object 2"));
        when(mockCommands.objectDatabase()).thenReturn(mockdb);
        when(mockdb.lookUp(anyString())).thenReturn(oIds);
        when(mockCommands.conflictsDatabase()).thenReturn(mockConflictsDb);
        RevParse command = new RevParse();
        command.setContext(mockCommands);

        exception.expect(IllegalArgumentException.class);
        command.setRefSpec(commitId1.toString().substring(0, commitId1.toString().length() - 2))
                .call();
    }

    public @Test void testLargeCommitList() {
        // before the commit that added this test and patch, RevParse would StackOverflow at ~4300
        // or so
        final int numCommits = 10_000;
        List<RevCommit> commits = createCommits(numCommits);

        repo.objectDatabase().putAll(commits.iterator());
        repo.command(UpdateRef.class).setName(Ref.HEAD).setNewValue(commits.get(0).getId()).call();

        RevParse revParse = repo.command(RevParse.class);

        for (int i = 0; i < numCommits; i += 100) {
            Optional<ObjectId> ancestorN = revParse.setRefSpec("HEAD~" + i).call();
            assertTrue("Ancestor " + i + " not found", ancestorN.isPresent());
            assertEquals("at index " + i, commits.get(i).getId(), ancestorN.get());
        }
    }

    public @Test void testAbortsOnAncestorOfShallowClone() {
        LinkedList<RevCommit> commits = new LinkedList<>(createCommits(100));
        // a shallow clone will have it's eldest commit with a parent that doesn't exist, let's
        // simulate that here by not adding the first one to the ObjectStore

        RevCommit parentOfShallow = commits.removeLast();
        RevCommit shallowCommit = commits.getLast();
        assertEquals(parentOfShallow.getId(), shallowCommit.getParentIds().get(0));

        repo.objectDatabase().putAll(commits.iterator());
        repo.command(UpdateRef.class).setName(Ref.HEAD).setNewValue(commits.get(0).getId()).call();

        RevParse revParse = repo.command(RevParse.class);

        Optional<ObjectId> shallow = revParse.setRefSpec("HEAD~98").call();
        assertTrue(shallow.isPresent());
        assertEquals(shallowCommit.getId(), shallow.get());

        Optional<ObjectId> ancestorOfShallow = revParse.setRefSpec("HEAD~99").call();
        assertFalse(ancestorOfShallow.isPresent());
    }

}
