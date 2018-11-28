package org.locationtech.geogig.model.impl;

import java.util.List;
import java.util.SortedMap;

import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.RevTreeImpl.LeafTree;
import org.locationtech.geogig.model.impl.RevTreeImpl.NodeTree;

import lombok.NonNull;

public class RevObjectFactoryImpl implements RevObjectFactory {

    static final RevObjectFactory INSTANCE = new RevObjectFactoryImpl();

    public @Override RevTree createTree(final @NonNull ObjectId id, final long size,
            @NonNull List<Node> trees, @NonNull List<Node> features) {

        Node[] f = features.isEmpty() ? null : features.toArray(new Node[features.size()]);
        Node[] t = trees.isEmpty() ? null : trees.toArray(new Node[trees.size()]);
        return new LeafTree(id, size, f, t);
    }

    public @Override RevTree createTree(final @NonNull ObjectId id, final long size,
            final int childTreeCount, @NonNull SortedMap<Integer, Bucket> buckets) {

        return new NodeTree(id, size, childTreeCount, buckets);
    }
}
