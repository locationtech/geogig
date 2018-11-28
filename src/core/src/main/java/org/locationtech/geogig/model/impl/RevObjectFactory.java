package org.locationtech.geogig.model.impl;

import java.util.List;
import java.util.SortedMap;

import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevPerson;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.opengis.feature.type.FeatureType;

import lombok.NonNull;

public interface RevObjectFactory {

    public static RevObjectFactory defaultInstance() {
        return RevObjectFactoryImpl.INSTANCE;
    }

    public @NonNull RevCommit createCommit(@NonNull ObjectId id, @NonNull ObjectId treeId,
            @NonNull List<ObjectId> parents, @NonNull RevPerson author,
            @NonNull RevPerson committer, @NonNull String message);

    public @NonNull RevTree createTree(@NonNull ObjectId id, long size, @NonNull List<Node> trees,
            @NonNull List<Node> features);

    public @NonNull RevTree createTree(@NonNull ObjectId id, long size, int childTreeCount,
            @NonNull SortedMap<Integer, Bucket> buckets);

    public @NonNull RevTag createTag(@NonNull ObjectId id, @NonNull String name,
            @NonNull ObjectId commitId, @NonNull String message, @NonNull RevPerson tagger);

    public @NonNull RevFeatureType createFeatureType(@NonNull ObjectId id,
            @NonNull FeatureType ftype);
}
