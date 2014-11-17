/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.test.integration;

import org.junit.Test;
import org.locationtech.geogig.api.CommitBuilder;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.plumbing.RevParse;
import org.locationtech.geogig.api.plumbing.UpdateRef;
import org.locationtech.geogig.api.porcelain.BranchCreateOp;
import org.locationtech.geogig.api.porcelain.CheckoutOp;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.api.porcelain.InitOp;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

public class RevParseIntegrationTest extends RepositoryTestCase {

    RevCommit masterCommit1, masterCommit2, masterCommit3, masterCommit4;

    RevCommit branchCommit1, branchCommit2;

    RevCommit mergeCommit;

    /**
     * <pre>
     * <code>
     *                                                                         HEAD
     *                                                                          /                                                                             
     * masterCommit1    masterCommit2   masterCommit3   mergeCommit   masterCommit4
     *    o----------------o----------------o-------------o--------------o 
     *                      \                            /
     *                       \                          /
     *                        o------------------------o
     *                 branchCommit1                 branchCommit2
     *                                                        \
     *                                                        BRANCH
     * </code>
     * </pre>
     */
    @Override
    protected void setUpInternal() throws Exception {
        geogig.command(InitOp.class).call();

        masterCommit1 = commit("masterCommit1");
        masterCommit2 = commit("masterCommit2");

        Ref branch = geogig.command(BranchCreateOp.class).setName("BRANCH")
                .setSource(masterCommit2.getId().toString()).setAutoCheckout(true).call();
        assertEquals(masterCommit2.getId(), branch.getObjectId());

        branchCommit1 = commit("branchCommit1");
        branchCommit2 = commit("branchCommit2");

        geogig.command(CheckoutOp.class).setSource("master").call();

        masterCommit3 = commit("masterCommit3");

        // fake a merge until we have the merge op in place

        CommitBuilder cb = new CommitBuilder();
        cb.setParentIds(ImmutableList.of(masterCommit3.getId(), branchCommit2.getId()));
        cb.setMessage("mergeCommit");
        cb.setAuthor("groldan");
        cb.setCommitter("groldan");
        cb.setTreeId(masterCommit3.getTreeId());
        long now = System.currentTimeMillis();
        cb.setAuthorTimestamp(now);
        cb.setCommitterTimestamp(now);
        mergeCommit = cb.build();

        getRepository().objectDatabase().put(mergeCommit);

        geogig.command(UpdateRef.class).setName("refs/heads/master")
                .setOldValue(masterCommit3.getId()).setNewValue(mergeCommit.getId()).call();
        // end faking up merge op

        masterCommit4 = commit("masterCommit4");
    }

    private RevCommit commit(String message) {
        return geogig.command(CommitOp.class).setAllowEmpty(true).call();
    }

    private Optional<ObjectId> revParse(String refSpec) {
        return geogig.command(RevParse.class).setRefSpec(refSpec).call();
    }

    private void assertParsed(RevCommit expected, Optional<ObjectId> parsed) {
        assertParsed(expected.getId(), parsed);
    }

    private void assertParsed(ObjectId expected, Optional<ObjectId> parsed) {
        assertTrue("expected " + expected + " got absent", parsed.isPresent());
        assertEquals(expected, parsed.get());
    }

    private void assertAbsent(Optional<ObjectId> parsed) {
        assertFalse("expected absent, got " + parsed, parsed.isPresent());
    }

    private String partialId(RevCommit commit, int length) {
        return commit.getId().toString().substring(0, length);
    }

    @Test
    public void testPrecondition() {
        try {
            revParse(null);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("refSpec"));
        }
    }

    @Test
    public void testParseHeadNames() {
        assertParsed(masterCommit4, revParse(Ref.HEAD));
        assertParsed(masterCommit4, revParse("master"));
        assertParsed(masterCommit4, revParse("heads/master"));
        assertParsed(masterCommit4, revParse("refs/heads/master"));

        assertParsed(branchCommit2, revParse("BRANCH"));
        assertParsed(branchCommit2, revParse("heads/BRANCH"));
        assertParsed(branchCommit2, revParse("refs/heads/BRANCH"));
    }

    @Test
    public void testParseCommitIds() {
        assertParsed(masterCommit1, revParse(masterCommit1.getId().toString()));
        assertParsed(masterCommit2, revParse(masterCommit2.getId().toString()));
        assertParsed(masterCommit3, revParse(masterCommit3.getId().toString()));
        assertParsed(masterCommit4, revParse(masterCommit4.getId().toString()));

        assertParsed(branchCommit1, revParse(branchCommit1.getId().toString()));
        assertParsed(branchCommit2, revParse(branchCommit2.getId().toString()));

        assertParsed(mergeCommit.getId(), revParse(mergeCommit.getId().toString()));
    }

    @Test
    public void testParseHeadParentIndex() {
        // sanity check first
        assertEquals(mergeCommit.getId(), masterCommit4.getParentIds().get(0));

        ObjectId expected;

        expected = masterCommit4.parentN(0).get();
        assertParsed(expected, revParse("HEAD^"));
        assertParsed(expected, revParse("HEAD^1"));
        assertAbsent(revParse("HEAD^2"));
        assertParsed(masterCommit4, revParse("HEAD^0"));

        expected = mergeCommit.parentN(0).get();
        assertParsed(expected, revParse("HEAD^^"));
        assertParsed(expected, revParse("HEAD^1^"));
        assertParsed(expected, revParse("HEAD^1^1"));

        expected = mergeCommit.parentN(1).get();
        assertParsed(expected, revParse("HEAD^^2"));
        assertParsed(expected, revParse("HEAD^1^2"));

        assertParsed(branchCommit2, revParse("BRANCH"));
        assertParsed(branchCommit2, revParse("heads/BRANCH"));
        assertParsed(branchCommit2, revParse("refs/heads/BRANCH"));

        assertParsed(branchCommit1, revParse("BRANCH^"));
        assertParsed(branchCommit1, revParse("heads/BRANCH^"));
        assertParsed(branchCommit1, revParse("refs/heads/BRANCH^"));
        assertParsed(branchCommit1, revParse("BRANCH^1"));
        assertParsed(branchCommit1, revParse("heads/BRANCH^1"));
        assertParsed(branchCommit1, revParse("refs/heads/BRANCH^1"));
    }

    @Test
    public void testParseObjectIdParentIndex() {
        assertAbsent(revParse(ObjectId.NULL.toString() + "^1"));

        assertParsed(mergeCommit, revParse(masterCommit4.getId() + "^"));
        assertParsed(mergeCommit, revParse(masterCommit4.getId() + "^1"));
        assertAbsent(revParse(masterCommit4.getId() + "^2"));

        assertParsed(masterCommit3, revParse(mergeCommit.getId() + "^"));
        assertParsed(masterCommit3, revParse(mergeCommit.getId() + "^1"));

        assertParsed(branchCommit2, revParse(mergeCommit.getId() + "^2"));
        assertParsed(branchCommit1, revParse(mergeCommit.getId() + "^2^"));
        assertParsed(branchCommit1, revParse(mergeCommit.getId() + "^2^1"));
        assertParsed(masterCommit2, revParse(mergeCommit.getId() + "^2^^"));
        assertParsed(masterCommit2, revParse(mergeCommit.getId() + "^2^^1"));
        assertAbsent(revParse(mergeCommit.getId() + "^3"));
        assertAbsent(revParse(mergeCommit.getId() + "^33"));
        assertAbsent(revParse(mergeCommit.getId() + "^2^^2"));

        assertParsed(masterCommit2, revParse(masterCommit3.getId() + "^"));
        assertParsed(masterCommit2, revParse(masterCommit3.getId() + "^1"));

        assertParsed(masterCommit1, revParse(masterCommit2.getId() + "^"));
        assertParsed(masterCommit1, revParse(masterCommit2.getId() + "^1"));

        assertAbsent(revParse(masterCommit1.getId() + "^"));
        assertAbsent(revParse(masterCommit1.getId() + "^1"));
        assertAbsent(revParse(masterCommit1.getId() + "^2"));
    }

    @Test
    public void testParsePartialObjectIdParentIndex() {
        assertParsed(mergeCommit, revParse(partialId(masterCommit4, 5) + "^"));
        assertParsed(mergeCommit, revParse(partialId(masterCommit4, 7) + "^1"));
        assertAbsent(revParse(partialId(masterCommit4, 9) + "^2"));

        assertParsed(masterCommit3, revParse(partialId(mergeCommit, 7) + "^"));
        assertParsed(masterCommit3, revParse(partialId(mergeCommit, 5) + "^1"));

        assertParsed(branchCommit2, revParse(partialId(mergeCommit, 5) + "^2"));
        assertParsed(branchCommit1, revParse(partialId(mergeCommit, 6) + "^2^"));
        assertParsed(branchCommit1, revParse(partialId(mergeCommit, 7) + "^2^1"));
        assertParsed(masterCommit2, revParse(partialId(mergeCommit, 8) + "^2^^"));
        assertParsed(masterCommit2, revParse(partialId(mergeCommit, 9) + "^2^^1"));
        assertAbsent(revParse(partialId(mergeCommit, 10) + "^3"));
        assertAbsent(revParse(partialId(mergeCommit, 9) + "^33"));
        assertAbsent(revParse(partialId(mergeCommit, 8) + "^2^^2"));
    }

    @Test
    public void testParseAncestors() {
        assertParsed(masterCommit4, revParse(Ref.HEAD + "~0"));
        assertParsed(mergeCommit, revParse(Ref.HEAD + "~1"));
        assertParsed(masterCommit3, revParse(Ref.HEAD + "~2"));
        assertParsed(masterCommit2, revParse(Ref.HEAD + "~3"));
        assertParsed(masterCommit1, revParse(Ref.HEAD + "~4"));
        assertAbsent(revParse(Ref.HEAD + "~5"));

        assertParsed(branchCommit2, revParse("BRANCH~0"));
        assertParsed(branchCommit1, revParse("BRANCH~1"));
        assertParsed(masterCommit2, revParse("BRANCH~2"));
        assertParsed(masterCommit1, revParse("BRANCH~3"));
        assertAbsent(revParse("BRANCH~4"));
        assertAbsent(revParse("BRANCH~10"));
    }
}
