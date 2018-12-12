/* Copyright (c) 2016 Boundless and others.
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

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.data.DataStore;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.plumbing.LsTreeOp;
import org.locationtech.geogig.plumbing.LsTreeOp.Strategy;
import org.locationtech.geogig.plumbing.ResolveFeatureType;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.ProgressListener;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.base.Supplier;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * Exports feature trees (layers) from the repository to a GeoTools {@link DataStore}.
 * <p>
 * Which repository snapshot features are exported from is controlled by the
 * {@link #setSourceCommitish(String) source ref spec}, which is a refSpec that must resolve to a
 * root tree (e.g. {@code "HEAD"}, {@code "refs/heads/master"}, {@code "myBrnach"}, {@code "HEAD~1"}
 * , etc). If no source refspec is provided, then the current {@code HEAD} is used.
 * 
 * <p>
 * Which layers to export is controlled by the {@link #setSourceTreePaths(List) source tree paths}
 * argument. If not specified, all layers in the resolved root tree are exported.
 * <p>
 * Feature trees are exported to FeatureTypes in the provided DataStore using the Feature tree's
 * schema and the tree name as FeatureType name.
 * <p>
 * The provided DataStore must support the {@link DataStore#createSchema(SimpleFeatureType)} method.
 * <p>
 * NOTE the DataStore argument, being a {@link Supplier}, can be lazily created, at the discretion
 * of the caller, but in any case, executing this operation will get the DataStore instance from the
 * supplier and {@link DataStore#dispose() dispose} it before returning from {@link #call()}.
 * 
 * @see ExportOp
 */
public abstract class DataStoreExportOp<T> extends AbstractGeoGigOp<T> {

    private Supplier<DataStore> dataStore;

    @Nullable
    private String commitIsh;

    @Nullable
    private List<String> treePaths;

    @Nullable
    private ReferencedEnvelope bboxFilter;

    public DataStoreExportOp<T> setTarget(Supplier<DataStore> supplier) {
        this.dataStore = supplier;
        return this;
    }

    /**
     * @param commitIsh Optional ref spec that resolves to the origin root tree for the export,
     *        default to {@code WORK_HEAD} if not provided.
     */
    public DataStoreExportOp<T> setSourceCommitish(final String commitIsh) {
        this.commitIsh = commitIsh;
        return this;
    }

    public String getSourceCommitish() {
        return this.commitIsh;
    }

    /**
     * @param treePaths Optional list of feature tree names to export, if not provided, exports all
     *        feature trees in the resolved commit
     */
    public DataStoreExportOp<T> setSourceTreePaths(List<String> treePaths) {
        this.treePaths = treePaths;
        return this;
    }

    /**
     * @param bboxFilter Optional bounding box filter to apply to all exported layers
     */
    public DataStoreExportOp<T> setBBoxFilter(@Nullable ReferencedEnvelope bboxFilter) {
        this.bboxFilter = bboxFilter;
        return this;
    }

    @Override
    protected T _call() {

        final ProgressListener progress = getProgressListener();
        final Set<String> layerRefSpecs = resolveExportLayerRefSpecs();

        final DataStore targetStore = dataStore.get();
        final T exportResult;
        try {
            for (String treeSpec : layerRefSpecs) {
                String tableName = Splitter.on(':').splitToList(treeSpec).get(1);
                export(treeSpec, targetStore, tableName, progress);
                if (progress.isCanceled()) {
                    break;
                }
            }
            exportResult = buildResult(targetStore);
        } finally {
            targetStore.dispose();
        }

        return exportResult;
    }

    protected abstract T buildResult(DataStore targetStore);

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
            throw new IllegalStateException(
                    "Unable to obtain feature type once created: " + treeSpec);
        }
        checkState(featureSource instanceof SimpleFeatureStore,
                "FeatureSource is not writable: " + featureType.getName().getLocalPart());

        SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;

        ExportOp cmd = command(ExportOp.class)//
                .setFeatureStore(featureStore)//
                .setPath(treeSpec)//
                .setTransactional(true)//
                .setBBoxFilter(this.bboxFilter);//

        Function<Feature, Optional<Feature>> transformingFunction = getTransformingFunction(
                featureType);

        if (transformingFunction != null) {
            cmd.setFeatureTypeConversionFunction(transformingFunction);
        }

        cmd.setProgressListener(progress).call();//
    }

    private Set<String> resolveExportLayerRefSpecs() {

        final String refSpec = fromNullable(commitIsh).or(Ref.HEAD);
        Optional<RevCommit> commit = command(RevObjectParse.class).setRefSpec(refSpec)
                .call(RevCommit.class);

        checkArgument(commit.isPresent(), "RefSpec doesn't resolve to a commit: '%s'", refSpec);

        final List<NodeRef> featureTreeRefs = Lists.newArrayList(
                command(LsTreeOp.class).setReference(commit.get().getTreeId().toString())
                        .setStrategy(Strategy.TREES_ONLY).call());

        final Set<String> exportLayers;

        // (n) -> n.name()
        Function<NodeRef, String> fn =  new Function<NodeRef, String>() {
            @Override
            public String apply(NodeRef n) {
                return  n.name();
            }};

        final Set<String> repoLayers = Sets
                .newHashSet(Iterables.transform(featureTreeRefs, fn));

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

        final String commitId = commit.get().getId().toString() + ":";

        //  (s) -> commitId + s
        Function<String, String> fn2 =  new Function<String, String>() {
            @Override
            public String apply(String s) {
                return  commitId + s;
            }};

        return Sets.newHashSet(Iterables.transform(exportLayers,fn2));
    }

    /**
     * @param featureType the feature type of the features to transform
     * @return a transform function to modify the features being exported
     */
    protected abstract Function<Feature, Optional<Feature>> getTransformingFunction(
            final SimpleFeatureType featureType);

}
