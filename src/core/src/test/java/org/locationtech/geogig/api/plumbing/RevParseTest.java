/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.api.plumbing;

import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.porcelain.BranchCreateOp;
import org.locationtech.geogig.api.porcelain.CheckoutOp;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.storage.StagingDatabase;
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
        injector.configDatabase().put("user.email", "groldan@opengeo.org");
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
        objectId = geogig
                .command(RevParse.class)
                .setRefSpec(
                        ObjectId.NULL.toString().substring(0,
                                ObjectId.NULL.toString().length() - 10)).call();
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
                .setRefSpec(ObjectId.forString("NotAFeature").toString()).call();
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
        StagingDatabase mockIndexDb = mock(StagingDatabase.class);
        Context mockCommands = mock(Context.class);
        RefParse mockRefParse = mock(RefParse.class);

        when(mockRefParse.setName(anyString())).thenReturn(mockRefParse);
        when(mockCommands.command(eq(RefParse.class))).thenReturn(mockRefParse);
        Optional<Ref> ref = Optional.absent();
        when(mockRefParse.call()).thenReturn(ref);

        List<ObjectId> oIds = Arrays.asList(ObjectId.forString("Object 1"),
                ObjectId.forString("Object 2"));
        when(mockIndexDb.lookUp(anyString())).thenReturn(oIds);
        when(mockCommands.stagingDatabase()).thenReturn(mockIndexDb);
        RevParse command = new RevParse();
        command.setContext(mockCommands);

        exception.expect(IllegalArgumentException.class);
        command.setRefSpec(commitId1.toString().substring(0, commitId1.toString().length() - 2))
                .call();
    }
}
