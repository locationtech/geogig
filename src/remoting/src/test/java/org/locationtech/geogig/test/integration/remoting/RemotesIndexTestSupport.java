/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.test.integration.remoting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.locationtech.geogig.data.FindFeatureTypeTrees;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.RevParse;
import org.locationtech.geogig.porcelain.BranchListOp;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.porcelain.index.CreateQuadTree;
import org.locationtech.geogig.porcelain.index.Index;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.IndexDatabase.IndexTreeMapping;
import org.locationtech.jts.geom.Envelope;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RemotesIndexTestSupport {

    public static Map<Ref, List<Index>> createIndexes(Repository repo) {
        Map<Ref, List<Index>> indexes = new HashMap<>();
        ImmutableList<Ref> branches = repo.command(BranchListOp.class).call();
        branches.forEach(ref -> indexes.put(ref, createIndexes(repo, ref)));
        return indexes;
    }

    public static List<Index> createIndexes(Repository repo, Ref ref) {
        Envelope bounds = new Envelope(-180, 180, -90, 90);
        List<NodeRef> types = repo.command(FindFeatureTypeTrees.class).setRootTreeRef(ref.getName())
                .call();

        List<Index> indexes = new ArrayList<>();
        for (NodeRef treeRef : types) {
            Map<String, IndexInfo> typeIndexes = getIndexes(repo, treeRef.path());
            if (typeIndexes.containsKey(treeRef.path())) {
                IndexInfo indexInfo = typeIndexes.get(treeRef.path());
                IndexDatabase indexdb = repo.indexDatabase();
                Optional<ObjectId> indexedTree = indexdb.resolveIndexedTree(indexInfo,
                        treeRef.getObjectId());
                if (indexedTree.isPresent()) {
                    indexes.add(new Index(indexInfo, indexedTree.get(), indexdb));
                }
            } else {
                Index index = repo.command(CreateQuadTree.class).setBounds(bounds)
                        .setIndexHistory(true).setTypeTreeRef(treeRef).call();
                log.info("Created index {} before cloning", index);
                indexes.add(index);
            }
        }
        return indexes;
    }

    public static void verifyClonedIndexes(Repository local, Repository remote,
            Optional<String> branch) {
        verifyClonedIndexes(local, remote, branch, branch);
    }

    public static void verifyClonedIndexes(Repository local, Repository remote,
            Optional<String> localRef, Optional<String> remoteRef) {
        Map<String, IndexInfo> remoteIndexes = getIndexes(remote, remoteRef);
        Map<String, IndexInfo> localIndexes = getIndexes(local, localRef);

        assertEquals(remoteIndexes.keySet(), localIndexes.keySet());
        assertEquals(remoteIndexes, localIndexes);
        for (String treename : remoteIndexes.keySet()) {
            IndexInfo indexInfo = remoteIndexes.get(treename);
            verifySameIndexContents(local, remote, indexInfo, localRef, remoteRef);
        }
    }

    public static void verifySameIndexContents(Repository local, Repository remote,
            IndexInfo indexInfo, Optional<String> localRef, Optional<String> remoteRef) {

        Set<IndexTreeMapping> remoteIndexMappings = getIndexMappings(indexInfo, remote, remoteRef);
        Set<IndexTreeMapping> localIndexMappings = getIndexMappings(indexInfo, local, localRef);

        Map<ObjectId, ObjectId> remoteByCanonical = remoteIndexMappings.stream()
                .collect(Collectors.toMap(m -> m.featureTree, m -> m.indexTree));

        Map<ObjectId, ObjectId> localByCanonical = localIndexMappings.stream()
                .collect(Collectors.toMap(m -> m.featureTree, m -> m.indexTree));

        assertEquals(remoteByCanonical.size(), localByCanonical.size());
        assertEquals(remoteByCanonical, localByCanonical);
        localByCanonical.forEach((canonicalId, indexId) -> {
            Set<RevTree> allRemoteIndexContents = RevObjectTestSupport
                    .getAllTrees(remote.indexDatabase(), indexId);
            Set<RevTree> allLocalIndexContents = RevObjectTestSupport
                    .getAllTrees(local.indexDatabase(), indexId);

            assertEquals(allRemoteIndexContents.size(), allLocalIndexContents.size());
            if (!allRemoteIndexContents.equals(allLocalIndexContents)) {
                System.err.println("wtf: " + allRemoteIndexContents.equals(allLocalIndexContents));
            }
            assertEquals(allRemoteIndexContents, allLocalIndexContents);
        });
        log.info("Index {} cloned correctly", indexInfo);
    }

    private static Set<IndexTreeMapping> getIndexMappings(IndexInfo indexInfo, Repository repo,
            Optional<String> ref) {

        if (!ref.isPresent()) {
            return Sets.newHashSet(repo.indexDatabase().resolveIndexedTrees(indexInfo));
        }

        Set<IndexTreeMapping> mappings = new HashSet<>();
        List<Ref> branches = getBranches(repo, ref);
        for (Ref branch : branches) {
            List<RevCommit> commits = Lists.newArrayList(repo.command(LogOp.class)
                    .addPath(indexInfo.getTreeName()).setUntil(branch.getObjectId()).call());
            for (RevCommit c : commits) {
                String treeRef = String.format("%s:%s", c.getId(), indexInfo.getTreeName());
                ObjectId canonicalTreeId = repo.command(RevParse.class).setRefSpec(treeRef).call()
                        .get();
                Optional<ObjectId> indexedTree = repo.indexDatabase().resolveIndexedTree(indexInfo,
                        canonicalTreeId);
                String msg = String.format("Expected index at %s:%s", branch.getName(),
                        indexInfo.getTreeName());
                assertTrue(msg, indexedTree.isPresent());
                mappings.add(new IndexTreeMapping(canonicalTreeId, indexedTree.get()));
            }
        }
        return mappings;
    }

    public static Map<String, IndexInfo> getIndexes(Repository repo, Optional<String> branch) {

        Map<String, IndexInfo> allIndexes = new HashMap<>();

        List<Ref> branches = getBranches(repo, branch);
        if (branches.isEmpty()) {
            return allIndexes;
        }
        for (Ref ref : branches) {
            Map<String, IndexInfo> branchIndexes = getIndexes(repo, ref);
            allIndexes.putAll(branchIndexes);
        }
        return allIndexes;
    }

    public static List<Ref> getBranches(Repository repo, Optional<String> branch) {
        List<Ref> branches;
        if (branch.isPresent()) {
            Optional<Ref> r = repo.command(RefParse.class).setName(branch.get()).call();
            if (r.isPresent()) {
                branches = Collections.singletonList(r.get());
            } else {
                return Collections.emptyList();
            }
        } else {
            branches = repo.command(BranchListOp.class).call();
        }
        return branches;
    }

    public static Map<String, IndexInfo> getIndexes(Repository repo, Ref branch) {

        List<NodeRef> types = repo.command(FindFeatureTypeTrees.class)
                .setRootTreeRef(branch.getName()).call();

        Map<String, IndexInfo> branchIndexes = new HashMap<>();
        for (NodeRef typeRef : types) {
            Map<String, IndexInfo> indexes = getIndexes(repo, typeRef.path());
            branchIndexes.putAll(indexes);
        }
        return branchIndexes;
    }

    public static Map<String, IndexInfo> getIndexes(Repository repo, String featureTreePath) {
        List<IndexInfo> indexInfos;
        indexInfos = repo.indexDatabase().getIndexInfos(featureTreePath);

        Map<String, IndexInfo> typeIndexes = indexInfos.stream()
                .collect(Collectors.toMap(i -> i.getTreeName(), i -> i));
        return typeIndexes;
    }

}
