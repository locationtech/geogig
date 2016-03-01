/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.plumbing;

import static com.google.common.base.Optional.fromNullable;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.geotools.data.DataStore;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.ProgressListener;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.plumbing.LsTreeOp;
import org.locationtech.geogig.api.plumbing.LsTreeOp.Strategy;
import org.locationtech.geogig.api.plumbing.ResolveFeatureType;
import org.locationtech.geogig.api.plumbing.ResolveTreeish;
import org.opengis.feature.simple.SimpleFeatureType;

import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * 
 * @see ExportOp
 */
public class DataStoreExportOp extends AbstractGeoGigOp<Void> {

    private Supplier<DataStore> dataStore;

    private String commitIsh;

    private List<String> treePaths;

    public DataStoreExportOp setTarget(Supplier<DataStore> supplier) {
        this.dataStore = supplier;
        return this;
    }

    public DataStoreExportOp setDataStore(DataStore store) {
        this.dataStore = Suppliers.ofInstance(store);
        return this;
    }

    /**
     * @param commitIsh Optional ref spec that resolves to the origin root tree for the export,
     *        default to {@code WORK_HEAD} if not provided.
     */
    public DataStoreExportOp setSourceCommitish(final String commitIsh) {
        this.commitIsh = commitIsh;
        return this;
    }

    /**
     * @param treePaths Optional list of feature tree names to export, if not provided, exports all
     *        feature trees in the resolved commit
     */
    public DataStoreExportOp setSourceTreePaths(List<String> treePaths) {
        this.treePaths = treePaths;
        return this;
    }

    @Override
    protected Void _call() {

        final ProgressListener progress = getProgressListener();
        final Set<String> layerRefSpecs = resolveExportLayerRefSpecs();

        final DataStore targetStore = dataStore.get();
        try {
            for (String treeSpec : layerRefSpecs) {
                String tableName = Splitter.on(':').splitToList(treeSpec).get(1);
                export(treeSpec, targetStore, tableName, progress);
                if (progress.isCanceled()) {
                    break;
                }
            }
        } finally {
            targetStore.dispose();
        }

        return null;
    }

    protected void export(final String treeSpec, final DataStore targetStore,
            final String targetTableName, final ProgressListener progress) {

        Optional<RevFeatureType> opType = command(ResolveFeatureType.class).setRefSpec(treeSpec)
                .call();
        checkState(opType.isPresent());

        SimpleFeatureType featureType = (SimpleFeatureType) opType.get().type();

        try {
            targetStore.createSchema(featureType);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create feature type from " + treeSpec);
        }

        SimpleFeatureSource featureSource;
        try {
            featureSource = targetStore.getFeatureSource(featureType.getName().getLocalPart());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to obtain feature type once created: "
                    + treeSpec);
        }
        checkState(featureSource instanceof SimpleFeatureStore, "FeatureSource is not writable: "
                + featureType.getName().getLocalPart());

        SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;

        command(ExportOp.class).setFeatureStore(featureStore).setPath(treeSpec)
                .setTransactional(true).setProgressListener(progress).call();
    }

    private Set<String> resolveExportLayerRefSpecs() {

        final String refSpec = fromNullable(commitIsh).or(Ref.WORK_HEAD);
        final Optional<ObjectId> id = command(ResolveTreeish.class).setTreeish(refSpec).call();

        checkArgument(id.isPresent(), "RefSpec doesn't resolve to a tree: '%s'", refSpec);

        final List<NodeRef> featureTreeRefs = Lists.newArrayList(command(LsTreeOp.class)
                .setReference(id.get().toString()).setStrategy(Strategy.TREES_ONLY).call());

        final Set<String> exportLayers;

        final Set<String> repoLayers = Sets.newHashSet(Iterables.transform(featureTreeRefs,
                (n) -> n.name()));

        if (treePaths == null || treePaths.isEmpty()) {
            exportLayers = repoLayers;
        } else {

            final Set<String> requestedLayers = Sets.newHashSet(treePaths);

            final Set<String> nonExistentLayers = Sets.difference(requestedLayers, repoLayers);
            checkArgument(nonExistentLayers.isEmpty(),
                    "The following requested layers do not exist in %s: %s", refSpec,
                    nonExistentLayers);
            exportLayers = requestedLayers;
        }

        final String commitId = id.get().toString() + ":";
        return Sets.newHashSet(Iterables.transform(exportLayers, (s) -> commitId + s));
    }
}
