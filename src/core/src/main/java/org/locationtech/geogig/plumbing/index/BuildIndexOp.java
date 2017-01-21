package org.locationtech.geogig.plumbing.index;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.ResolveFeatureType;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.locationtech.geogig.storage.IndexDatabase;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.base.Optional;
import com.vividsolutions.jts.geom.Envelope;

/**
 * Builds an indexed tree based on a canonical tree for a given {@link IndexInfo}
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
        final Envelope maxBounds = resolveMaxBounds();
        System.err.println("Max bounds: " + maxBounds);
        CreateQuadTree command = command(CreateQuadTree.class);
        command.setFeatureTree(canonicalTreeId);
        command.setMaxBounds(maxBounds);
        ProgressListener listener = getProgressListener();
        RevTree quadTree = command.setProgressListener(listener).call();

        return quadTree;
    }

    private Envelope resolveMaxBounds() {
        final String typeName = index.getTreeName();
        final String geomPropertyName = index.getAttributeName();
        Optional<RevFeatureType> revType = command(ResolveFeatureType.class).setRefSpec(typeName)
                .call();
        checkArgument(revType.isPresent(), "FeatureType %s not found", typeName);
        FeatureType featureType = revType.get().type();
        PropertyDescriptor descriptor = featureType.getDescriptor(geomPropertyName);
        checkArgument(descriptor != null, "FeatureType %s has no geometry property %s", typeName,
                geomPropertyName);
        checkArgument(descriptor instanceof GeometryDescriptor,
                "Property %s is not a geometry attribute", geomPropertyName);

        CoordinateReferenceSystem crs = ((GeometryDescriptor) descriptor)
                .getCoordinateReferenceSystem();

        Envelope maxBounds;
        try {
            maxBounds = SpatialOps.boundsOf(crs);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Error computing max bounds for the CRS of " + geomPropertyName, e);
        }
        checkState(maxBounds != null && !maxBounds.isNull(),
                "Unable to compute the area of validity for the CRS of %s", geomPropertyName);
        return maxBounds;
    }
}
