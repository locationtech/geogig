/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.repository;

import java.util.Iterator;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.opengis.feature.type.FeatureType;

import com.google.common.base.Optional;

/**
 * Provides an interface for a working tree of a GeoGig repository.
 * <p>
 * <ul>
 * <li>A WorkingTree represents the current working copy of the versioned feature types
 * <li>You perform work on the working tree (insert/delete/update features)
 * <li>Then you commit to the current Repository's branch
 * <li>You can checkout a different branch from the Repository and the working tree will be updated
 * to reflect the state of that branch
 * </ul>
 * 
 * @see Repository
 * @since 1.0
 */
public interface WorkingTree {

    /**
     * Updates the WORK_HEAD ref to the specified tree.
     * 
     * @param newTree the tree to be set as the new WORK_HEAD
     * @return {@code newTree}
     */
    ObjectId updateWorkHead(ObjectId newTree);

    /**
     * @return the tree represented by WORK_HEAD. If there is no tree set at WORK_HEAD, it will
     *         return the HEAD tree (no unstaged changes).
     */
    RevTree getTree();

    /**
     * Deletes a single feature from the working tree and updates the WORK_HEAD ref.
     * 
     * @param treePath the path of the feature
     * @param featureId the id of the feature
     * @return true if the object was found and deleted, false otherwise
     */
    boolean delete(String treePath, String featureId);

    /**
     * @param treePath the path to the tree to truncate
     * @return the new {@link ObjectId} for the root tree in the {@link Ref#WORK_HEAD working tree}
     */
    ObjectId truncate(String treePath);

    /**
     * Deletes a tree and the features it contains from the working tree and updates the WORK_HEAD
     * ref.
     * <p>
     * Note this method completely removes the tree from the working tree. If the tree pointed out
     * to by {@code path} should be left empty, use {@link #truncate} instead.
     * 
     * @param treePath the path to the tree to delete
     * @return the new {@link ObjectId} for the root tree in the {@link Ref#WORK_HEAD working tree}
     */
    ObjectId delete(String treePath);

    /**
     * Deletes a collection of features from the working tree and updates the provided
     * {@link ProgressListener} as it does so.
     * 
     * @param features the features to delete
     * @param progress the progress listener to update
     * @return the new {@link ObjectId} for the root tree in the {@link Ref#WORK_HEAD working tree}
     */
    ObjectId delete(Iterator<String> features, ProgressListener progress);

    /**
     * Creates a new type tree in the working tree with the provided path and feature type.
     * 
     * @param treePath the path of the new feature type tree
     * @param featureType the feature type
     * @return the {@link NodeRef} of the new type tree
     */
    NodeRef createTypeTree(String treePath, FeatureType featureType);

    /**
     * Insert a single feature into the working tree.
     * 
     * @apiNote the {@link RevFeatureType} pointed out by {@link FeatureInfo#getFeatureTypeId()}
     *          must already exist in the repository.
     * @apiNote whenever possible avoid using this method in favor of
     *          {@link #insert(Iterator, ProgressListener)} which is a bulk operation and will upate
     *          the working tree once for all the inserted features. That is, if you need to insert
     *          more than one feature, don't call this method repeteadly, as it'll leave {@code N-1}
     *          dangling tree objects in the database with {@code N} being the number of features
     *          inserted.
     * @param featureInfo the {@link FeatureInfo} of the feature to insert
     * @return the new {@link ObjectId} for the root tree in the {@link Ref#WORK_HEAD working tree}
     */
    ObjectId insert(FeatureInfo featureInfo);

    /**
     * Inserts the {@link RevFeature}s provided by the {@link FeatureInfo} iterator at the trees
     * indicated by each {@code FeatureInfo}'s {@link FeatureInfo#getPath()}.
     * <p>
     * The tree each feature belongs to is determined by the feature info path's
     * {@link NodeRef#parentPath(String) parent}, and the feature id by the feature info path's
     * {@link NodeRef#nodeFromPath(String) node} name.
     * <p>
     * In the event that a target feature tree does not exist for a feature info, it will first be
     * created using the {@link FeatureInfo#getFeatureTypeId() featuretype id} as the tree node's
     * default metadata id.
     * 
     * @apiNote all {@link RevFeatureType} pointed out by all {@link FeatureInfo#getFeatureTypeId()}
     *          must already exist in the repository.
     * @param featureInfos the stream of {@link RevFeature}s to insert into the working tree
     * @param progress the progress listener where to report progress and to check whether the
     *        operation should be aborted.
     * @return the new {@link ObjectId} for the root tree in the {@link Ref#WORK_HEAD working tree}
     */
    ObjectId insert(Iterator<FeatureInfo> featureInfos, ProgressListener progress);

    /**
     * Determines if a feature tree exists at {@code treePath} in the current working tree.
     * 
     * @param treePath feature type to check
     * @return true if the feature type exists, false otherwise.
     */
    boolean hasRoot(String treePath);

    /**
     * @param pathFilter if specified, only changes that match the filter will be returned
     * @return an iterator for all of the differences between the work tree and the index based on
     *         the path filter.
     */
    AutoCloseableIterator<DiffEntry> getUnstaged(@Nullable String pathFilter);

    /**
     * @param pathFilter if specified, only changes that match the filter will be counted
     * @return the number differences between the work tree and the index based on the path filter.
     */
    DiffObjectCount countUnstaged(@Nullable String pathFilter);

    /**
     * @return {@code true} if there are no unstaged changes, false otherwise
     */
    boolean isClean();

    /**
     * @param featurePath finds a {@link Node} for the feature at the given path in the index
     * @return the Node for the feature at the specified path if it exists in the work tree,
     *         otherwise Optional.absent()
     */
    Optional<Node> findUnstaged(String featurePath);

    /**
     * @return a list of all the feature type names in the working tree
     * @see FindFeatureTypeTrees
     */
    List<NodeRef> getFeatureTypeTrees();

    /**
     * Updates the definition of a Feature type associated as default feature type to a given path.
     * It also modifies the metadataId associated to features under the passed path, which used the
     * previous default feature type.
     * 
     * @param path the path
     * @param featureType the new feature type definition to set as default for the passed path
     */
    NodeRef updateTypeTree(String treePath, FeatureType featureType);

}