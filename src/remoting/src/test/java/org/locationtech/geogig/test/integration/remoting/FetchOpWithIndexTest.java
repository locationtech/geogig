/* Copyright (c) 2012-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.test.integration.remoting;

import static org.locationtech.geogig.test.TestData.copy;
import static org.locationtech.geogig.test.TestData.line1;
import static org.locationtech.geogig.test.TestData.line2;
import static org.locationtech.geogig.test.TestData.line3;
import static org.locationtech.geogig.test.TestData.point1;
import static org.locationtech.geogig.test.TestData.point2;
import static org.locationtech.geogig.test.TestData.point3;
import static org.locationtech.geogig.test.integration.remoting.RemotesIndexTestSupport.createIndexes;
import static org.locationtech.geogig.test.integration.remoting.RemotesIndexTestSupport.verifyClonedIndexes;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.Test;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.remotes.RemoteAddOp;
import org.locationtech.geogig.porcelain.index.Index;
import org.locationtech.geogig.remotes.CloneOp;
import org.locationtech.geogig.remotes.FetchOp;
import org.locationtech.geogig.remotes.RefDiff;
import org.locationtech.geogig.remotes.TransferSummary;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.AbstractGeoGigOp.CommandListener;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.test.TestData;
import org.opengis.feature.simple.SimpleFeature;

import com.google.common.base.Optional;

public class FetchOpWithIndexTest extends FetchOpTest {

    /**
     * Override to set setCloneIndexes(true) and add a command hook that creates the indexes on the
     * remote repo before cloning it
     */
    protected @Override CloneOp cloneOp() {
        CloneOp cloneOp = super.cloneOp().setCloneIndexes(true);
        cloneOp.addListener(createSpatialIndexBeforeCloneListener);
        return cloneOp;
    }

    protected @Override FetchOp fetchOp() throws RepositoryConnectionException {
        FetchOp fetchOp = super.fetchOp();
        fetchOp.addListener(verifyFetchedIndexesListener);
        return fetchOp.setFetchIndexes(true);
    }

    private CommandListener verifyFetchedIndexesListener = new CommandListener() {

        public @Override void preCall(AbstractGeoGigOp<?> command) {// nothing to do
            createIndexes(originRepo);
            createIndexes(upstreamRepo);
            String originURI = origin.getFetchURL();
            command.getClientData().put(originURI, originRepo);

            String upstreamURI = upstream.getFetchURL();
            command.getClientData().put(upstreamURI, upstreamRepo);
        }

        public @Override void postCall(AbstractGeoGigOp<?> command, @Nullable Object result,
                @Nullable RuntimeException exception) {
            if (exception != null) {
                return;
            }
            TransferSummary ts = (TransferSummary) result;
            verifyFetchedIndexes(command, ts);
        }

    };

    private CommandListener createSpatialIndexBeforeCloneListener = new CommandListener() {

        public @Override void preCall(AbstractGeoGigOp<?> command) {
            Repository remote = FetchOpWithIndexTest.this.originRepo;
            createIndexes(remote);
        }

        public @Override void postCall(AbstractGeoGigOp<?> command, @Nullable Object result,
                @Nullable RuntimeException exception) {
        }
    };

    private void verifyFetchedIndexes(AbstractGeoGigOp<?> command, TransferSummary ts) {
        Set<String> remotes = ts.getRefDiffs().keySet();
        for (String remoteURI : remotes) {
            FetchOp op = (FetchOp) command;
            Remote remoteObj = op.getRemotes().stream()
                    .filter(r -> remoteURI.equals(r.getFetchURL())).findFirst().get();

            String fetchURL = remoteObj.getFetchURL();
            Repository remote = (Repository) command.getClientData().get(fetchURL);

            Collection<RefDiff> collection = ts.getRefDiffs().get(remoteURI);
            for (RefDiff rd : collection) {
                if (rd.isDelete()) {
                    continue;
                }
                Ref newRef = rd.getNewRef();
                Repository local = command.context().repository();
                String localRef = newRef.getName();
                String remoteBranch = remoteObj.mapToRemote(localRef).orElse(null);
                verifyClonedIndexes(local, remote, Optional.of(localRef),
                        Optional.of(remoteBranch));
            }
        }
    }

    /**
     * Verifies that indexes are fetched if {@link FetchOp#setFetchIndexes(boolean)
     * setFetchIndexes(true)} both when the remote was first fetched and afterwards
     * 
     * <pre>
     * <code>
     *             (adds Points/2, Lines/2, Polygons/2)
     *    branch1 o-------------------------------------
     *           /                                      \
     *          /                                        \  no ff merge
     *  master o------------------------------------------o-----------------o
     *          \  (initial commit has                                     / no ff merge
     *           \     Points/1, Lines/1, Polygons/1)                     /
     *            \                                                      /
     *             \                                                    /
     *     branch2  o--------------------------------------------------
     *             (adds Points/3, Lines/3, Polygons/3)
     *
     * </code>
     * </pre>
     */
    public @Test void testMergeCommitIndexesFetchedWhenInSync() throws Exception {
        GeogigContainer c1 = new GeogigContainer("repo1");
        GeogigContainer c2 = new GeogigContainer("repo2");

        Repository repo1 = c1.repo;
        Repository repo2 = c2.repo;

        Remote remote = repo2.command(RemoteAddOp.class).setURL(repo1.getLocation().toString())
                .setName("repo1").call();
        c2.addRemoteOverride(remote, repo1);

        TestData support = new TestData(repo1);
        support.loadDefaultData();
        createIndexes(repo1);

        FetchOp command = repo2.command(FetchOp.class);
        command.getClientData().put(remote.getFetchURL(), repo1);
        TransferSummary ts = command.setAllRemotes(true)//
                .setFetchIndexes(true)// FETCHING INDEXES FROM SCRATCH
                .call();
        verifyFetchedIndexes(command, ts);

        support.checkout("branch1")//
                .branchAndCheckout("newbranch")//
                .insert(//
                        copy(line1, "l1"), copy(line2, "l2"), copy(line3, "l3"), //
                        copy(point1, "p1"), copy(point2, "p2"), copy(point3, "p3")//
                )//
                .add().commit("new commit")//
                .checkout("master")//
                .mergeNoFF("newbranch", "merge newbranch onto master");

        command = repo2.command(FetchOp.class);
        command.getClientData().put(remote.getFetchURL(), repo1);
        ts = command.setAllRemotes(true)//
                .setFetchIndexes(true)// SHOULD KEEP WORKING AFTERWARDS
                .call();
        verifyFetchedIndexes(command, ts);
    }

    /**
     * <pre>
     * <code>
     *             (adds Points/2, Lines/2, Polygons/2)
     *    branch1 o-------------------------------------
     *           /                                      \
     *          /                                        \  no ff merge
     *  master o------------------------------------------o-----------------o
     *          \  (initial commit has                                     / no ff merge
     *           \     Points/1, Lines/1, Polygons/1)                     /
     *            \                                                      /
     *             \                                                    /
     *     branch2  o--------------------------------------------------
     *             (adds Points/3, Lines/3, Polygons/3)
     *
     * </code>
     * </pre>
     */
    public @Test void testMergeCommitIndexesFetchedWhenNotInSync() throws Exception {
        GeogigContainer c1 = new GeogigContainer("repo1");
        GeogigContainer c2 = new GeogigContainer("repo2");

        Repository repo1 = c1.repo;
        Repository repo2 = c2.repo;

        Remote remote = repo2.command(RemoteAddOp.class).setURL(repo1.getLocation().toString())
                .setName("repo1").call();
        c2.addRemoteOverride(remote, repo1);

        TestData support = new TestData(repo1);
        support.loadDefaultData();
        Map<Ref, List<Index>> indexesByBranch = createIndexes(repo1);
        System.err.println(indexesByBranch);

        FetchOp command = repo2.command(FetchOp.class);
        command.getClientData().put(remote.getFetchURL(), repo1);
        command.setAllRemotes(true)//
                .setFetchIndexes(false)// NOT FETCHING INDEXES INITIALLY
                .call();

        List<SimpleFeature> points = IntStream.range(0, 1000).mapToObj(i -> copy(point1, "p" + i))
                .collect(Collectors.toList());

        List<SimpleFeature> lines = IntStream.range(0, 1000).mapToObj(i -> copy(line1, "l" + i))
                .collect(Collectors.toList());

        support.checkout("branch1")//
                .branchAndCheckout("newbranch")//
                .insert(points)//
                .add().commit("new points")//
                .insert(lines)//
                .add().commit("new lines")//
                .checkout("master")//
                .mergeNoFF("newbranch", "merge newbranch onto master");

        command = repo2.command(FetchOp.class);
        command.getClientData().put(remote.getFetchURL(), repo1);
        TransferSummary ts = command.setAllRemotes(true)//
                .setFetchIndexes(true)// BUT FETCHING AFTERWARDS, SHOULD GET THEM ALL
                .call();
        verifyFetchedIndexes(command, ts);
    }
}
