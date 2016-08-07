package org.locationtech.geogig.repository;

import java.util.Iterator;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.data.FeatureSource;
import org.geotools.data.Query;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevTree;
import org.opengis.feature.Feature;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;

import com.google.common.base.Function;
import com.google.common.base.Optional;

public interface WorkingTree {

    /**
     * Updates the WORK_HEAD ref to the specified tree.
     * 
     * @param newTree the tree to be set as the new WORK_HEAD
     */
    void updateWorkHead(ObjectId newTree);

    /**
     * @return the tree represented by WORK_HEAD. If there is no tree set at WORK_HEAD, it will
     *         return the HEAD tree (no unstaged changes).
     */
    RevTree getTree();

    /**
     * Deletes a single feature from the working tree and updates the WORK_HEAD ref.
     * 
     * @param path the path of the feature
     * @param featureId the id of the feature
     * @return true if the object was found and deleted, false otherwise
     */
    boolean delete(String path, String featureId);

    /**
     * @param path the path to the tree to truncate
     * @return the new {@link ObjectId} for the root tree in the {@link Ref#WORK_HEAD working tree}
     */
    ObjectId truncate(String path);

    /**
     * Deletes a tree and the features it contains from the working tree and updates the WORK_HEAD
     * ref.
     * <p>
     * Note this methods completely removes the tree from the working tree. If the tree pointed out
     * to by {@code path} should be left empty, use {@link #truncate} instead.
     * 
     * @param path the path to the tree to delete
     * @return
     * @throws Exception
     */
    ObjectId delete(String path);

    /**
     * Deletes a collection of features of the same type from the working tree and updates the
     * WORK_HEAD ref.
     * 
     * @param typeName feature type
     * @param filter - currently unused
     * @param affectedFeatures features to remove
     * @throws Exception
     */
    void delete(Name typeName, Filter filter, Iterator<Feature> affectedFeatures) throws Exception;

    /**
     * Deletes a feature type from the working tree and updates the WORK_HEAD ref.
     * 
     * @param typeName feature type to remove
     * @throws Exception
     */
    void delete(Name typeName) throws Exception;

    /**
     * 
     * @param features the features to delete
     */
    void delete(Iterator<String> features);

    void delete(Iterator<String> features, ProgressListener progress);

    NodeRef createTypeTree(String treePath, FeatureType featureType);

    void insert(FeatureInfo featureInfo);

    void insert(Iterator<FeatureInfo> featureInfos, ProgressListener progress);

    /**
     * Insert a single feature into the working tree and updates the WORK_HEAD ref.
     * 
     * @param parentTreePath path of the parent tree to insert the feature into
     * @param feature the feature to insert
     */
    Node insert(String parentTreePath, Feature feature);

    void insert(String treePath, FeatureSource source, Query query, ProgressListener listener);

    /**
     * Inserts a collection of features into the working tree and updates the WORK_HEAD ref.
     * 
     * @param treePath the path of the tree to insert the features into
     * @param features the features to insert
     * @param listener a {@link ProgressListener} for the current process
     * @param insertedTarget if provided, inserted features will be added to this list
     * @param collectionSize number of features to add
     * @throws Exception
     */
    void insert(String treePath, Iterator<? extends Feature> features, ProgressListener listener,
            @Nullable List<Node> insertedTarget, @Nullable Integer collectionSize);

    /**
     * Inserts the given {@code features} into the working tree, using the {@code treePathResolver}
     * function to determine to which tree each feature is added.
     * 
     * @param treePathResolver a function that determines the path of the tree where each feature
     *        node is stored
     * @param features the features to insert, possibly of different schema and targetted to
     *        different tree paths
     * @param listener a progress listener
     * @param insertedTarget if provided, all nodes created will be added to this list. Beware of
     *        possible memory implications when inserting a lot of features.
     * @param collectionSize if given, used to determine progress and notify the {@code listener}
     * @return the total number of inserted features
     */
    void insert(Function<Feature, String> treePathResolver, Iterator<? extends Feature> features,
            ProgressListener listener, @Nullable List<Node> insertedTarget,
            @Nullable Integer collectionSize);

    /**
     * Updates a collection of features in the working tree and updates the WORK_HEAD ref.
     * 
     * @param treePath the path of the tree to insert the features into
     * @param features the features to insert
     * @param listener a {@link ProgressListener} for the current process
     * @param collectionSize number of features to add
     * @throws Exception
     */
    void update(String treePath, Iterator<Feature> features, ProgressListener listener,
            @Nullable Integer collectionSize) throws Exception;

    /**
     * Determines if a specific feature type is versioned (existing in the main repository).
     * 
     * @param typeName feature type to check
     * @return true if the feature type is versioned, false otherwise.
     */
    boolean hasRoot(Name typeName);

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
     * Returns true if there are no unstaged changes, false otherwise
     */
    boolean isClean();

    /**
     * @param path finds a {@link Node} for the feature at the given path in the index
     * @return the Node for the feature at the specified path if it exists in the work tree,
     *         otherwise Optional.absent()
     */
    Optional<Node> findUnstaged(String path);

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