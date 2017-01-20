package org.locationtech.geogig.plumbing.index;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.storage.IndexDatabase;

import com.google.common.base.Optional;

/**
 * Builds an indexed tree based on a canonical tree for a given {@link Index}
 */
public class BuildIndexOp extends AbstractGeoGigOp<ObjectId> {

    private IndexInfo index;

    private ObjectId canonicalTreeId;

    public BuildIndexOp setIndex(IndexInfo index) {
        this.index = index;
        return this;
    }

    public BuildIndexOp setCanonicalTreeId(ObjectId canonicalTreeId) {
        this.canonicalTreeId = canonicalTreeId;
        return this;
    }

    @Override
    protected ObjectId _call() {
        IndexDatabase indexDatabase = indexDatabase();

        Optional<ObjectId> existingTreeId = indexDatabase.resolveIndexedTree(index,
                canonicalTreeId);
        if (existingTreeId.isPresent()) {
            return existingTreeId.get();
        }

        RevTree indexedTree = null;
        switch (index.getIndexType()) {
        case QUADTREE:
            indexedTree = buildQuadTree();
            break;
        }

        if (indexedTree != null) {
            indexDatabase.addIndexedTree(index, canonicalTreeId, indexedTree.getId());
        }

        return indexedTree.getId();
    }

    private RevTree buildQuadTree() {
        CreateQuadTree command = command(CreateQuadTree.class);
        command.setFeatureTree(canonicalTreeId);

        ProgressListener listener = getProgressListener();
        RevTree quadTree = command.setProgressListener(listener).call();

        return quadTree;
    }
}

