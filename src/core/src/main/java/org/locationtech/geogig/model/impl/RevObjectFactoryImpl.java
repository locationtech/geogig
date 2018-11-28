package org.locationtech.geogig.model.impl;

import java.util.List;
import java.util.SortedMap;

import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevPerson;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.RevTreeImpl.LeafTree;
import org.locationtech.geogig.model.impl.RevTreeImpl.NodeTree;

import com.google.common.collect.ImmutableList;

import lombok.NonNull;

public class RevObjectFactoryImpl implements RevObjectFactory {

    static final RevObjectFactory INSTANCE = new RevObjectFactoryImpl();

    public @Override @NonNull RevCommit createCommit(@NonNull ObjectId id, @NonNull ObjectId treeId,
            @NonNull List<ObjectId> parents, @NonNull RevPerson author,
            @NonNull RevPerson committer, @NonNull String message) {

        return new RevCommitImpl(id, treeId, ImmutableList.copyOf(parents), author, committer,
                message);
    }

    public @Override @NonNull RevTree createTree(final @NonNull ObjectId id, final long size,
            @NonNull List<Node> trees, @NonNull List<Node> features) {

        Node[] f = features.isEmpty() ? null : features.toArray(new Node[features.size()]);
        Node[] t = trees.isEmpty() ? null : trees.toArray(new Node[trees.size()]);
        return new LeafTree(id, size, f, t);
    }

    public @Override @NonNull RevTree createTree(final @NonNull ObjectId id, final long size,
            final int childTreeCount, @NonNull SortedMap<Integer, Bucket> buckets) {

        return new NodeTree(id, size, childTreeCount, buckets);
    }

    public @Override @NonNull RevTag createTag(@NonNull ObjectId id, @NonNull String name,
            @NonNull ObjectId commitId, @NonNull String message, @NonNull RevPerson tagger) {
        return new RevTagImpl(id, name, commitId, message, tagger);
    }

}
