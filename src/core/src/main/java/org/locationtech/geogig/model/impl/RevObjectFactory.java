package org.locationtech.geogig.model.impl;

import java.util.List;
import java.util.SortedMap;

import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;

import lombok.NonNull;

public interface RevObjectFactory {

    public static RevObjectFactory defaultInstance() {
        return RevObjectFactoryImpl.INSTANCE;
    }

    public RevTree createTree(@NonNull ObjectId id, long size, @NonNull List<Node> trees,
            @NonNull List<Node> features);

    public RevTree createTree(@NonNull ObjectId id, long size, int childTreeCount,
            @NonNull SortedMap<Integer, Bucket> buckets);
}
