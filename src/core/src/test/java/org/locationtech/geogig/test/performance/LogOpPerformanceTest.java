/* Copyright (c) 2012-2014 Boundless and others.
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

import org.junit.Ignore;
import org.junit.Test;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.porcelain.BranchCreateOp;
import org.locationtech.geogig.api.porcelain.CheckoutOp;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.api.porcelain.LogOp;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;

public class LogOpPerformanceTest extends RepositoryTestCase {

    @Override
    protected void setUpInternal() throws Exception {
    }

    @Ignore
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

    @Ignore
    @Test
    public void testBranches() throws Exception {
        createAndLogMultipleBranches(200, 200);
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
        System.err.println("***********\nCreating " + numberFormat.format(numCommits)
                + " commits...");

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
        int largeStep = numCommits / 10;
        int smallStep = numCommits / 100;

        RevCommit commit = null;
        for (int i = 1; i <= numCommits; i++) {
            if (i % largeStep == 0) {
                System.err.print(i);
                System.err.flush();
            } else if (i % smallStep == 0) {
                System.err.print('.');
                System.err.flush();
            }
            commit = geogig.command(CommitOp.class).setAllowEmpty(true)
                    .setMessage("Commit " + i + " in branch " + branchName).call();
        }
        System.err.print('\n');
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
            // System.err.println("branch " + Integer.toString(i));
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
