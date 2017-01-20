package org.locationtech.geogig.plumbing.index;

import java.util.Iterator;
import java.util.Map;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.plumbing.ResolveFeatureType;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.porcelain.BranchListOp;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.IndexInfo.IndexType;
import org.locationtech.geogig.storage.IndexDatabase;
import org.opengis.feature.type.GeometryType;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.feature.type.PropertyType;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * Creates an {@link Index}.
 */
public class CreateIndexInfoOp extends AbstractGeoGigOp<IndexInfo> {

    private String treeName;

    private String attributeName;

    private IndexType indexType = IndexType.QUADTREE;

    private Map<String, Object> metadata = null;

    private boolean indexHistory = false;

    public CreateIndexInfoOp setTreeName(String treeName) {
        this.treeName = treeName;
        return this;
    }

    public CreateIndexInfoOp setAttributeName(String attributeName) {
        this.attributeName = attributeName;
        return this;
    }

    public CreateIndexInfoOp setIndexType(IndexType indexType) {
        this.indexType = indexType;
        return this;
    }

    public CreateIndexInfoOp setIndexHistory(boolean indexHistory) {
        this.indexHistory = indexHistory;
        return this;
    }

    @Override
    protected IndexInfo _call() {
        IndexDatabase indexDatabase = indexDatabase();

        Preconditions.checkState(!indexDatabase.getIndex(treeName, attributeName).isPresent(),
                "An index has already been created on that tree and attribute.");

        Optional<RevFeatureType> featureTypeOpt = command(ResolveFeatureType.class)
                .setRefSpec(Ref.HEAD + ":" + treeName).call();

        Preconditions.checkState(featureTypeOpt.isPresent(),
                String.format("Can't resolve '%s' as a feature type", treeName));

        RevFeatureType treeType = featureTypeOpt.get();

        boolean attributeFound = false;
        PropertyType attributeType = null;
        for (PropertyDescriptor descriptor : treeType.descriptors()) {
            if (descriptor.getName().toString().equals(attributeName)) {
                attributeFound = true;
                attributeType = descriptor.getType();
                break;
            }
        }

        Preconditions.checkState(attributeFound, String.format(
                "Could not find an attribute named '%s' in the feature type.", attributeName));

        Preconditions.checkState(attributeType instanceof GeometryType,
                "Only indexes on spatial attributes are currently supported.");

        IndexInfo index = indexDatabase.createIndex(treeName, attributeName, indexType, metadata);

        if (indexHistory) {
            indexHistory(index);
        } else {
            indexCommit(index, Ref.HEAD);
        }

        return index;
    }

    private void indexHistory(IndexInfo index) {
        ImmutableList<Ref> branches = command(BranchListOp.class).setLocal(true).setRemotes(true)
                .call();

        for (Ref ref : branches) {
            Iterator<RevCommit> commits = command(LogOp.class).setUntil(ref.getObjectId()).call();
            while (commits.hasNext()) {
                RevCommit next = commits.next();
                indexCommit(index, next.getId().toString());
            }
        }
    }

    private void indexCommit(IndexInfo index, String committish) {
        String treeSpec = committish + ":" + treeName;
        Optional<ObjectId> treeId = command(ResolveTreeish.class).setTreeish(treeSpec).call();
        if (!treeId.isPresent()) {
            return;
        }
        command(BuildIndexOp.class).setIndex(index).setCanonicalTreeId(treeId.get())
                .setProgressListener(getProgressListener()).call();
    }
}

