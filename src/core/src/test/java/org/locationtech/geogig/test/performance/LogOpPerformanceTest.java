/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.test.performance;

import java.text.NumberFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.junit.ClassRule;
import org.junit.Test;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;

public class LogOpPerformanceTest extends RepositoryTestCase {

    @ClassRule
    public static EnablePerformanceTestRule enabler = new EnablePerformanceTestRule();

    @Override
    protected void setUpInternal() throws Exception {
    }

    @Test
    public void testCommits() throws Exception {
        System.err.println("############### Warming up....");
        createAndLogMultipleCommits(1000);
        System.err.println("############### Warm up done.");

        createAndLogMultipleCommits(1000);
        createAndLogMultipleCommits(1000 * 10);
        // createAndLogMultipleCommits(1000 * 100);
        // createAndLogMultipleCommits(1000 * 1000);
    }

    @Test
    public void testBranches() throws Exception {
        createAndLogMultipleBranches(50, 100);
    }

    private void createAndLogMultipleBranches(int numBranches, int numCommits) throws Exception {
        super.doSetUp();

        NumberFormat numberFormat = NumberFormat.getInstance(Locale.ENGLISH);
        System.err.println("***********\nCreating " + numberFormat.format(numBranches)
                + " branches with " + numberFormat.format(numCommits) + " commits each...");

        Stopwatch sw = Stopwatch.createStarted();
        List<ObjectId> ids = createBranches(numBranches, numCommits);
        sw.stop();
        System.err.println(numberFormat.format(numBranches) + " branches with "
                + numberFormat.format(numCommits) + " comits each created in " + sw.toString());
        System.err.flush();

        LogOp op = geogig.command(LogOp.class);
        for (ObjectId id : ids) {
            op.addCommit(id);
        }
        sw.reset().start();
        Iterator<RevCommit> commits = op.call();
        sw.stop();
        System.err.println("LogOp took " + sw.toString());

        benchmarkIteration(commits);

        op = geogig.command(LogOp.class).setTopoOrder(true);
        for (ObjectId id : ids) {
            op.addCommit(id);
        }
        sw.reset().start();
        commits = op.call();
        sw.stop();
        System.err.println("LogOp using --topo-order took " + sw.toString());
        benchmarkIteration(commits);

        super.tearDown();
    }

    private void createAndLogMultipleCommits(int numCommits) throws Exception {
        super.doSetUp();

        NumberFormat numberFormat = NumberFormat.getInstance(Locale.ENGLISH);
        System.err.println(
                "***********\nCreating " + numberFormat.format(numCommits) + " commits...");

        Stopwatch sw = Stopwatch.createStarted();
        createCommits(numCommits, "");
        sw.stop();
        System.err.println(numberFormat.format(numCommits) + " created in " + sw.toString());
        System.err.flush();

        sw.reset().start();
        Iterator<RevCommit> commits = geogig.command(LogOp.class).call();
        sw.stop();
        System.err.println("LogOp took " + sw.toString());

        benchmarkIteration(commits);

        super.tearDown();
    }

    private RevCommit createCommits(int numCommits, String branchName) {
        RevCommit commit = null;
        for (int i = 1; i <= numCommits; i++) {
            commit = geogig.command(CommitOp.class).setAllowEmpty(true)
                    .setMessage("Commit " + i + " in branch " + branchName).call();
        }
        return commit;
    }

    private List<ObjectId> createBranches(int numBranches, int numCommits) {
        List<ObjectId> list = Lists.newArrayList();
        for (int i = 1; i <= numBranches; i++) {
            String branchName = "branch" + Integer.toString(i);
            geogig.command(CommitOp.class).setAllowEmpty(true)
                    .setMessage("Commit before " + branchName).call();
            geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName(branchName).call();
            createCommits(numCommits / 2, branchName);
            geogig.command(CheckoutOp.class).setSource(Ref.MASTER).call();
            geogig.command(CommitOp.class).setAllowEmpty(true)
                    .setMessage("Commit during " + branchName).call();
            geogig.command(CheckoutOp.class).setSource(branchName).call();
            RevCommit lastCommit = createCommits(numCommits / 2, branchName);
            geogig.command(CheckoutOp.class).setSource(Ref.MASTER).call();
            list.add(lastCommit.getId());
        }
        return list;
    }

    private void benchmarkIteration(Iterator<RevCommit> commits) {
        NumberFormat numberFormat = NumberFormat.getInstance(Locale.ENGLISH);
        Stopwatch sw = Stopwatch.createStarted();
        sw.reset().start();
        int c = 0;
        while (commits.hasNext()) {
            c++;
            commits.next();
        }
        sw.stop();
        System.err.println("Iterated " + numberFormat.format(c) + " commits in " + sw.toString());
    }

}
