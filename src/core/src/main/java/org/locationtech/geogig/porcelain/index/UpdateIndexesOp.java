package org.locationtech.geogig.porcelain.index;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.geometry.jts.JTS;
import org.locationtech.geogig.data.FindFeatureTypeTrees;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.index.BuildIndexOp;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.jts.geom.Envelope;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.Maps;

import lombok.extern.slf4j.Slf4j;

/**
 * Given a {@code refSpec} that resolves to a root tree, finds out which indexes are defined for all
 * its feature type trees, checks whether they need to be updated, and updates the indexes to match
 * the current canonical tree versions.
 *
 */
@Slf4j
public class UpdateIndexesOp extends AbstractGeoGigOp<List<Index>> {

    private Ref rootRefSpec;

    /**
     * @param branchRef the refSpec that resolves to a root tree
     * @return {@code this}
     */
    public UpdateIndexesOp setRef(final Ref branchRef) {
        checkNotNull(branchRef);
        checkArgument(!(branchRef instanceof SymRef),
                "Update indexes does not support symbolic references");
        checkArgument(branchRef.getName().startsWith(Ref.REFS_PREFIX),
                branchRef.getName() + " is not a branch ref");
        this.rootRefSpec = branchRef;
        return this;
    }

    /**
     * Performs the operation.
     * 
     * @return a list of {@link Index} objects that represent updated indexes
     */
    @Override
    protected List<Index> _call() {
        checkNotNull(rootRefSpec, "rootRefSpec not provided");

        final Ref branchRef = this.rootRefSpec;

        final List<NodeRef> featureTypeTreeRefs;
        featureTypeTreeRefs = command(FindFeatureTypeTrees.class)
                .setRootTreeRef(branchRef.getName()).call();

        String previousRefSpec = branchRef.getName() + "^";

        final List<NodeRef> previousVersionTrees = command(FindFeatureTypeTrees.class)
                .setRootTreeRef(previousRefSpec).call();

        // NodeRef::path, but friendly for Fortify
        Function<NodeRef, String> fn_path = new Function<NodeRef, String>() {
            @Override
            public String apply(NodeRef noderef) {
                return noderef.path();
            }
        };

        final Map<String, NodeRef> previousTreeRefs = Maps.uniqueIndex(previousVersionTrees,
                fn_path);

        final IndexDatabase indexDatabase = indexDatabase();

        List<Index> updatedIndexes = new ArrayList<>();

        for (NodeRef treeRef : featureTypeTreeRefs) {
            final String treePath = treeRef.path();
            final List<IndexInfo> layerIndexes = indexDatabase.getIndexInfos(treePath);
            if (!layerIndexes.isEmpty()) {
                final @Nullable NodeRef oldTreeRef = previousTreeRefs.get(treePath);

                List<Index> updated = updateIndexes(oldTreeRef, treeRef, layerIndexes);
                if (getProgressListener().isCanceled()) {
                    return null;
                }
                updatedIndexes.addAll(updated);
            }
        }
        return updatedIndexes;
    }

    private List<Index> updateIndexes(final @Nullable NodeRef oldTreeRef, final NodeRef newTreeRef,
            List<IndexInfo> indexes) {

        List<Index> updated = new ArrayList<>(indexes.size());

        final IndexDatabase indexDatabase = indexDatabase();

        final ObjectId newCanonicalTreeId = newTreeRef.getObjectId();
        Optional<ObjectId> indexTreeId;

        final ProgressListener progress = getProgressListener();
        for (IndexInfo index : indexes) {
            indexTreeId = indexDatabase.resolveIndexedTree(index, newCanonicalTreeId);
            if (indexTreeId.isPresent()) {
                log.debug("Index for tree {}({}) exists: {}", newTreeRef.path(), newCanonicalTreeId,
                        indexTreeId.get());
            } else {
                if (progress.isCanceled()) {
                    return null;
                }
                progress.setDescription(String.format("Updating index %s(%s) on %s...",
                        index.getAttributeName(), index.getIndexType(), newTreeRef.path()));

                final ObjectId revTypeId = newTreeRef.getMetadataId();

                final RevTree oldCanonicalTree;
                if (oldTreeRef != null && indexDatabase
                        .resolveIndexedTree(index, oldTreeRef.getObjectId()).isPresent()) {
                    oldCanonicalTree = objectDatabase().getTree(oldTreeRef.getObjectId());
                } else {
                    oldCanonicalTree = RevTree.EMPTY;
                }
                final RevTree newCanonicalTree = newCanonicalTreeId.equals(RevTree.EMPTY_TREE_ID)
                        ? RevTree.EMPTY
                        : objectDatabase().getTree(newCanonicalTreeId);

                BuildIndexOp cmd = command(BuildIndexOp.class);
                cmd.setIndex(index);
                cmd.setOldCanonicalTree(oldCanonicalTree);
                cmd.setNewCanonicalTree(newCanonicalTree);
                cmd.setRevFeatureTypeId(revTypeId);
                RevTree indexTree = cmd.call();
                if (progress.isCanceled()) {
                    return null;
                }

                String id = indexTree.getId().toString().substring(0, 8);
                long size = indexTree.size();
                Envelope env = SpatialOps.boundsOf(indexTree);
                String bounds = env == null ? "null" : JTS.toGeometry(env).toString();
                progress.setDescription(String.format("Updated index: %s, size: %,d, bounds: %s",
                        id, size, bounds));

                updated.add(new Index(index, indexTree.getId(), indexDatabase));
            }
        }
        if (progress.isCanceled()) {
            return null;
        }
        return updated;
    }
}
