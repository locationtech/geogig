package org.locationtech.geogig.test;

import static java.lang.String.format;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;

import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.plumbing.ForEachRef;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class TestSupport {

    public static void verifySameRefs(Repository source, Repository copy) {
        Map<String, Ref> sourceRefs = new TreeMap<>();
        sourceRefs.putAll(
                Maps.uniqueIndex(source.command(ForEachRef.class).call(), (r) -> r.getName()));

        Map<String, Ref> copyRefs = new TreeMap<>();
        copyRefs.putAll(//
                Maps.uniqueIndex(//
                        copy.command(ForEachRef.class)//
                                .setFilter((r) -> !r.getName().startsWith(Ref.REMOTES_PREFIX))
                                .call(),
                        (r) -> r.getName())//
        );

        assertEquals(sourceRefs.keySet(), copyRefs.keySet());
        assertEquals(sourceRefs, copyRefs);
    }

    public static void verifySameContents(Repository source, Repository copy) {
        Set<ObjectId> sourceIds = verifyRepositoryContents(source);
        Set<ObjectId> copyIds = verifyRepositoryContents(copy);

        if (!copyIds.containsAll(sourceIds)) {
            Set<ObjectId> missing = Sets.difference(sourceIds, copyIds);
            fail(missing.size() + " missing ids on copy:");
        }
    }

    /**
     * Verifies that all the reachable objects from all the refs in the repo exist in the repo's
     * database
     */
    public static Set<ObjectId> verifyRepositoryContents(Repository repo) {
        Map<String, Ref> allRefs = Maps.uniqueIndex(repo.command(ForEachRef.class).call(),
                (r) -> r.getName());

        allRefs = Maps.filterKeys(allRefs,
                (k) -> !k.equals(Ref.STAGE_HEAD) && !k.equals(Ref.WORK_HEAD));

        Set<ObjectId> allIds = Sets.newConcurrentHashSet();
        for (Ref ref : allRefs.values()) {
            if (ref instanceof SymRef) {
                Ref target = ref.peel();
                assertTrue(format("symref points to a non existent ref: %s", ref),
                        allRefs.containsKey(target.getName()));
            } else {
                Stack<String> pathToObject = new Stack<>();
                pathToObject.push(ref.getName());
                verifyAllReachableContents(repo, ref.getObjectId(), pathToObject, allIds);
                pathToObject.pop();
            }
        }
        return allIds;
    }

    public static void verifyAllReachableContents(Repository repo, ObjectId tip,
            Stack<String> pathToObject, Set<ObjectId> allIds) {
        assertNotNull(tip);
        if (tip.isNull()) {
            return;
        }
        ObjectDatabase store = repo.objectDatabase();
        RevObject obj = store.getIfPresent(tip);
        pathToObject.push(tip.toString());
        if (obj == null) {
            throw new NullPointerException(format("object %s does not exist at %s", tip, pathToObject));
        }
        allIds.add(obj.getId());

        switch (obj.getType()) {
        case TAG:
            verifyAllReachableContents(repo, ((RevTag) obj).getCommitId(), pathToObject, allIds);
            break;
        case COMMIT:
            assertTrue("no graph entry found for commit " + obj,
                    repo.graphDatabase().exists(obj.getId()));
            verifyAllReachableContents(repo, ((RevCommit) obj).getTreeId(), pathToObject, allIds);
            break;
        case TREE:
            verifyAllReachableContents(repo, (RevTree) obj, pathToObject, allIds);
            break;
        case FEATURE:
        case FEATURETYPE:
            break;
        default:
            throw new IllegalArgumentException("Object has no type: " + obj);
        }

        pathToObject.pop();
    }

    public static void verifyAllReachableContents(Repository repo, RevTree tree,
            Stack<String> pathToObject, Set<ObjectId> allIds) {

        ObjectStore store = repo.objectDatabase();
        Set<Node> nodes = RevObjectTestSupport.getTreeNodes(tree, store);
        for (Node node : nodes) {
            pathToObject.push(node.getName());
            Optional<ObjectId> metadataId = node.getMetadataId();
            if (metadataId.isPresent()) {
                pathToObject.push("metadata");
                verifyAllReachableContents(repo, metadataId.get(), pathToObject, allIds);
                pathToObject.pop();
            }
            verifyAllReachableContents(repo, node.getObjectId(), pathToObject, allIds);
            pathToObject.pop();
        }

        tree.buckets().forEach((index, bucket) -> {
            pathToObject.push("bucket[" + index + "]");
            verifyAllReachableContents(repo, bucket.getObjectId(), pathToObject, allIds);
            pathToObject.pop();
        });
    }
}
