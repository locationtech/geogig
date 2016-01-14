/* Copyright (c) 2014 OpenPlans. All rights reserved.
 * This code is licensed under the BSD New License, available at the root
 * application directory.
 */
package org.geogit.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.geogit.storage.ObjectDatabase;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.vividsolutions.jts.geom.Envelope;

/**
 * Implementation of the HRPlus tree described in {@link http://www.cs.ust.hk/faculty/dimitris/PAPERS/ssdbm01.pdf}
 * We chose the HRPlus tree for the spatial index because it provides:
 * <ul>
 * <li>Partial persistence. Provides access to past versions.
 * <li>Multiple roots. (Entry points to past versions)
 * <li>Data sharing between version trees (TODO not implemented yet!)
 * <li>Improved query performance over regular R-Tree or Historical R-Tree
 * <p>
 * <h4>Implementation Notes
 * TODO build HR trees for different layers. 
 * Currently only have one tree for one layer/feature.
 * <p>
 * TODO add data sharing between versions. An important part of HR+ trees is
 * that data for old versions may appear under the root of a new version these
 * nodes are invisible to queries made on the newer version, but exist
 * nonetheless to save space. However this complicates lookups and splits so we
 * implement the more space-inefficient technique of keeping all versions
 * separate first.
 * 
 */
public class HRPlusTree extends HRPlusTreeUtils {

    /**
     * Connection to geogit database.
     * TODO never initialized! We don't know how to connect.
     */
    private ObjectDatabase db;

    /**
     * Id for this tree. Required by object database. Currently unused.
     */
    private ObjectId objectId;

    /**
     * Entry points to the tree. There will be one for each version.
     */
    private Map<ObjectId, HRPlusContainerNode> rootMap = new HashMap<ObjectId, HRPlusContainerNode>();

    public HRPlusTree() {
        // TODO constructor should prepare object database
    }

    /**
     * Initializes a HR+ tree of consisting of nodes of the given feature type
     * in the revTree
     * 
     * @param revTree  RevTree to build this HRPlusTree from
     * @param featureType  type of feature we want to extract from {@param revTree}. Other features are ignored.
     */
    public HRPlusTree(RevTreeImpl revTree, RevFeatureType featureType) {
        ImmutableList<Node> featureNodes = revTree.features().get();
        if (featureNodes != null){
            for (Node featureNode : featureNodes) {
                Envelope e = new Envelope();
                featureNode.expand(e);
                objectId = featureNode.getObjectId();
                // TODO: How do we get the version id?
                ObjectId versionId = ObjectId.NULL;
                this.insert(e, versionId);
            }
        }
    }

    /**
     * Insert a node into this HR+ tree.
     * Finds the correct container node to insert into (@method chooseSubtree),
     * performs the insert, checking for overflow,
     * rebalances envelopes, simultaneously adding and re-placing nodes in the case of overflow. 
     * 
     * @param bounds  The data itself. A region of a map.
     * @param versionId  Timestamp. Associates this node with a particular version of this tree
     */
    public void insert(Envelope bounds, final ObjectId versionId) {
        // Create node from params
        HRPlusNode newNode = new HRPlusNode(bounds, versionId);
        // Find appropriate container to insert into
        HRPlusContainerNode containerNode = chooseSubtree(newNode, versionId);
        // Check for edge condition: root map didn't contain {@param versionId}
        if (containerNode == null) {
            // adding a new root to the tree.
            containerNode = new HRPlusContainerNode();
            containerNode.addNode(newNode);
            this.addRootTableEntry(containerNode);
            return;
        }
        // Perform insert
        containerNode.addNode(newNode);
        // Now check if we have a degree overflow.
        HRPlusContainerNode newContainerNode = null;
        if (containerNode.getNumNodes() > this.getMaxDegree()) {
            // Shoot, we have overflow. Split the old container.
            newContainerNode = treatOverflow(containerNode, versionId);
        }
        // Update envelopes in the tree, rebound from overflow if necessary.
        adjustTree(containerNode, newContainerNode);
    }

    /**
     * Bounding box query.
     * Recursively search the entire tree for nodes within the given envelope.
     * 
     * @param env  The bounding box we restrict results to.
     * @return A list of nodes within {@param env}
     */
    public List<HRPlusNode> query(Envelope env) {
        List<HRPlusNode> matches = new ArrayList<HRPlusNode>();
        // Search all container nodes in {@field rootMap}
        for (HRPlusContainerNode root : this.rootMap.values()) {
            root.query(env, matches);
        }
        return matches;
    }

    /**
     * Bounding box query limited to one version of the tree.
     * Similar to {@method query}, but search is limited to one version of the tree.
     * Returns null if {@param versionId }does not appear as a key in {@param rootMap}.
     * 
     * @param versionId  The version of the tree we wish to search
     * @param env  Bounding box
     * @return A list of nodes within {@param env}
     */
    public List<HRPlusNode> queryHistorical(ObjectId versionId, Envelope env) {
        // Give up if version doesn't exist
        if (!this.hasVersion(versionId)) {
            return null;
        }
        List<HRPlusNode> matches = new ArrayList<HRPlusNode>();
        // Search all container nodes in {@field rootMap}
        HRPlusContainerNode root = this.rootMap.get(versionId);
        root.query(env, matches);
        return matches;
    }

    /**
     * Check if a version exists within this HR+ tree.
     * 
     * @param versionId  A timestamp, may match a root of this tree.
     * @return boolean indicating whether there is a subtree associated with {@param versionId}
     */
    public boolean hasVersion(ObjectId versionId) {
        return this.rootMap.containsKey(versionId);
    }

    /**
     * Add a new root (new versionid) to this tree's root map.
     * Either add to an existing entry or create a new one.
     * 
     * @param newRoot
     *            The node to insert into {@field rootTable}. This roots versionId
     *            must not already appear in the tree
     */
    private void addRootTableEntry(HRPlusContainerNode newRoot) {
        ObjectId versionId = newRoot.getVersionIds().get(0);
        this.rootMap.put(versionId, newRoot);

    }

    /**
     * Update an entry of this tree's root map.
     * We basically need to modify an existing version tree of a root-map. Do
     * this by deleting it from the root-map and inserting the new root.
     * 
     * @param root  new root to add
     * @param versionId  version of the new root
     */
    private void replaceRootTableEntry(HRPlusContainerNode root,
            ObjectId versionId, HRPlusContainerNode newRoot) {
        Preconditions.checkNotNull(root);
        this.rootMap.remove(versionId);
        this.rootMap.put(newRoot.getVersionIds().get(0), newRoot);
    }

    /**
     * Distribute the children of one container into two containers.
     * <p>
     * TODO: do a version split. This comes after we share nodes between
     * versions of the tree. For now, the split is only spatial.
     * 
     * @param containerNode
     *            Node we want to split due to overflow
     * @param versionId
     *            The version of the tree we're working with. Momentarily
     *            unused.
     * @return the new container node. (the old one is modified in keySplit)
     */
    private HRPlusContainerNode treatOverflow(
            HRPlusContainerNode containerNode, ObjectId versionId) {
        return keySplitContainerNode(containerNode);
    }

    /**
     * Spatially divide one container node. 
     * Minimize the perimeter/margin and overlap of subsets of nodes.
     * (This heuristic helps eliminate search paths during queries.)
     * <p>
     * Algorithm computes and compares the 'goodness' of margin (perimeter) values. 
     * See @method sumOfMargins for details.
     * 
     * @param containerNode  The container to split
     * @return The newly-created container node
     */
    public HRPlusContainerNode keySplitContainerNode(
            HRPlusContainerNode containerNode) {
        int numNodesExpected = this.getMaxDegree() + 1;
        Preconditions.checkArgument(
                containerNode != null && containerNode.getNumNodes() == numNodesExpected,
                "keySplitContainerNode must be called on a non-null container with [%d] nodes",
                numNodesExpected);
        // Uses R* splitting algorithm
        List<HRPlusNode> minXSort = minXSort(containerNode.getNodes());
        List<HRPlusNode> maxXSort = maxXSort(containerNode.getNodes());
        List<HRPlusNode> minYSort = minYSort(containerNode.getNodes());
        List<HRPlusNode> maxYSort = maxYSort(containerNode.getNodes());
        // Get total perimeters
        double xMarginSum = sumOfMargins(minXSort) + sumOfMargins(maxXSort);
        double yMarginSum = sumOfMargins(minYSort) + sumOfMargins(maxYSort);
        // partition is a subset of nodes inside the container. A spatially-close subset.
        List<HRPlusNode> partition;
        // choose the split axis based on the min margin sum (aka smallest perimeter)
        // after choosing axis, choose distribution with the minimum overlap value
        if (xMarginSum <= yMarginSum) {
            partition = partitionByMinOverlap(minXSort, maxXSort);
        } else {
            partition = partitionByMinOverlap(minYSort, maxYSort);
        }
        // Create new container, move each node in partition from old container to new one.
        // New container has same versionId as old one, for now. (Should maybe get a brand new timestamp.)
        // The newContainerNode is currently disconnected from the tree.
        // This is fixed in {@code HRPlusTree#adjustEnvelopes}
        HRPlusContainerNode newContainerNode = new HRPlusContainerNode(null,
                containerNode.getVersionIds().get(0));
        for (HRPlusNode node : partition) {
            containerNode.removeNode(node);
            newContainerNode.addNode(node);
        }
        return newContainerNode;
    }

    /**
     * Determine whether a container is a root.
     * Assumes the container is already part of the tree.
     * 
     * @param containerNode
     * @return true if {@param containerNode} is contained in {@field rootMap}
     */
    private boolean isRoot(HRPlusContainerNode containerNode) {
        if (containerNode == null || this.rootMap == null) {
            // Edge case: the container is empty.
            return false;
        }
        return this.hasVersion(containerNode.getVersionIds().get(0))
            && containerNode.getParentNode() == null;
    }

    /**
     * Re-distribute a tree's nodes among roots. 
     * This may happen after a regular insert or after an insert where an old container node was split.
     * 
     * @param containerNode  container to begin adjusting from, non null
     * @param newContainerNode  new node to add to the tree. may be null.
     */
    private void adjustTree(HRPlusContainerNode containerNode,
            HRPlusContainerNode newContainerNode) {
        HRPlusNode parentNode;
        if (!this.isRoot(containerNode)) {
            parentNode = containerNode.getParentNode();
            HRPlusContainerNode parentContainer = parentNode
                .getParentContainer();
            Envelope containerMBR = containerNode.getMBR();
            parentNode.setBounds(containerMBR);

            if (newContainerNode != null) {
                HRPlusNode newNode = new HRPlusNode(newContainerNode.getMBR(),
                        newContainerNode.getVersionIds().get(0));
                newNode.setChild(newContainerNode);
                parentContainer.addNode(newNode);
            }

            HRPlusContainerNode splitParent = null;

            if (parentContainer.getNumNodes() > this.getMaxDegree()) {
                // Overflow. Split the parent container.
                splitParent = treatOverflow(parentContainer, parentContainer
                        .getVersionIds().get(0));
            }
            adjustTree(parentContainer, splitParent);
        } else if (newContainerNode == null) {
            return;
        } else {
            // Here the root needs splitting
            // containerNode and newContainerNode both are not null! assert?
            HRPlusNode newNode1 = new HRPlusNode(containerNode.getMBR(),
                    containerNode.getVersionIds().get(0));
            HRPlusNode newNode2 = new HRPlusNode(newContainerNode.getMBR(),
                    newContainerNode.getVersionIds().get(0));

            newNode1.setChild(containerNode);
            newNode2.setChild(newContainerNode);

            HRPlusContainerNode newRoot = new HRPlusContainerNode();
            newRoot.addNode(newNode1);
            newRoot.addNode(newNode2);

            this.replaceRootTableEntry(containerNode, containerNode
                    .getVersionIds().get(0), newRoot);
        }
    }

    /**
     * Get a container from the object database.
     * @param objectId  Key for the lookup
     * @return the container associated with {@param objectId}
     */
    public HRPlusContainerNode lookupHRPlusContainerNode(ObjectId objectId) {
        // TODO unguarded cast
        return (HRPlusContainerNode) this.db.get(objectId);
    }

    /**
     * Get a node from the object database
     * 
     * @param objectId  Key for the lookup
     * @return node associated with {@param objectId}
     */
    public HRPlusNode lookupHRPlusNode(ObjectId objectId) {
        // TODO unguarded cast
        return (HRPlusNode) this.db.get(objectId);
    }

    /**
     * Get the root (if any) associated with a given version id.
     * @param versionId
     * @return entry points associated with @param layerId
     */
    public HRPlusContainerNode getRootForVersionId(ObjectId versionId) {
        return rootMap.get(versionId);
    }

    /**
     * Choose the best leaf container to add a new node to.
     *
     * @param newNode  node we want to find a container for
     * @param versionId  version of the new node
     * @return a leaf container node from this HR+ tree that is the best fit for the new node
     */
    private HRPlusContainerNode chooseSubtree(final HRPlusNode newNode,
            ObjectId versionId) {
        // First, find an entry point for this node. Return null if not present.
        HRPlusContainerNode containerRoot = getRootForVersionId(versionId);
        if (containerRoot == null) {
            return null;
        }
        return (helpChooseContainer(containerRoot, newNode));
    }

    /**
     * Recursive helper method for {@code chooseSubtree}.
     * Follows the R* algorithm as discussed in: {@link http://infolab.usc.edu/csci587/Fall2011/papers/p322-beckmann.pdf}
     * 
     * @param newNode  Node we want to find a container for
     * @param container  Current container node we are searching
     * @return a leaf container node that is the best fit
     */
    private HRPlusContainerNode helpChooseContainer(
            HRPlusContainerNode containerNode, HRPlusNode newNode) {
        if (containerNode.isLeaf()) {
            return containerNode;
        } else {
            List<HRPlusNode> nodesForContainer = containerNode.getNodes();
            HRPlusNode insertionNode = nodesForContainer.get(0);
            double minOverlap = Double.MAX_VALUE;
            double newOverlap;
            double minArea = Double.MAX_VALUE;
            double newArea;
            if (containerNode.isOneStepAboveLeafLevel()) {
                // Find the sub-container with the minimum overlap enlargement
                for (HRPlusNode node : nodesForContainer) {
                    newOverlap = getOverlap(node, newNode);
                    newArea = getAreaEnlargement(node, newNode);
                    if (newOverlap < minOverlap) {
                        minArea = newArea;
                        minOverlap = newOverlap;
                        insertionNode = node;
                    } else if (newOverlap == minOverlap) {
                        // Minimize area enlargement to break ties
                        newArea = getAreaEnlargement(node, newNode); 
                        if (newArea < minArea) {
                            minArea = newArea;
                            insertionNode = node;
                        }
                    }
                }
            } else {
                // Find the sub-container with the minimum area enlargement
                for (HRPlusNode node : nodesForContainer) {
                    newArea = getAreaEnlargement(node, newNode);
                    if (newArea < minArea) {
                        minArea = newArea;
                        insertionNode = node;
                    }
                }
            }
            return helpChooseContainer(insertionNode.getChild(), newNode);
        }
    }

    /**
     * Get all nodes contained within the root.
     * Does not search recursively! Only gets the nodes whose parent is the root.
     * @param container  The root container to search
     * @return list of nodes with {@param container} as parent.
     */
    private List<HRPlusNode> getNodesForRoot(HRPlusContainerNode container) {
        List<HRPlusNode> nodes = new ArrayList<HRPlusNode>();
        nodes.addAll(container.getNodesForContainer());
        return nodes;
    }

    /**
     * Get all nodes in this HR+ tree. 
     * 
     * @return all the nodes in this HRPlusTree
     */
    public List<HRPlusNode> getNodes() {
        List<HRPlusNode> nodes = new ArrayList<HRPlusNode>();
        for (HRPlusContainerNode root : this.rootMap.values()) {
            nodes.addAll(getNodesForRoot(root));
        }
        return nodes;
    }

    /**
     * Gets the number of roots in this tree.
     * 
     * @return the size of the root-map of this HRPlusTree
     */
    public int getNumRoots() {
        return this.rootMap.size();
    }

    /**
     * Gets all nodes associated with a version id.
     * For now, this is all the nodes in the tree (because each tree has just one version id).
     * Use this for testing.
     * 
     * @param versionId  version id to filter search by
     * @return List of nodes belonging to the given version Id
     */
    public List<HRPlusNode> getNodes(ObjectId versionId) {
        List<HRPlusNode> result = new ArrayList<HRPlusNode>();
        if (this.rootMap.containsKey(versionId)) {
            result = this.getNodesForRoot(this.rootMap.get(versionId));
        }
        return result;

    }

    /**
     * Gets all leaves of this tree.
     * 
     * @param versionId  the version id of the root node to search
     * @return list of all nodes whose parent container is a leaf
     */
    public List<HRPlusNode> getLeaves(ObjectId versionId) {
        List<HRPlusNode> leaves = new ArrayList<HRPlusNode>();
        HRPlusContainerNode root = this.getRootForVersionId(versionId);

        if (root.isLeaf()) {
            leaves.addAll(root.getNodes());
        } else {
            for (HRPlusNode node : root.getNodes()) {
                leaves.addAll(node.getLeafNodes());
            }
        }
        return leaves;
    }

    /**
     * Gets all container nodes under a root node.
     * 
     * 
     * @param versionId  id of the root in {@field rootMap}
     * @return a list of all container nodes in the tree
     */
    public List<HRPlusContainerNode> getContainersForRoot(ObjectId versionId) {
        List<HRPlusContainerNode> containers = new ArrayList<HRPlusContainerNode>();
        // Keep a stack of containers to visit
        List<HRPlusContainerNode> unvisited = new ArrayList<HRPlusContainerNode>();
        unvisited.add(this.rootMap.get(versionId));
        HRPlusContainerNode curr;
        while (!unvisited.isEmpty()) {
            curr = unvisited.remove(0);
            // Add current container to total list
            containers.add(curr);
            // Add all child nodes' containers to unvisited list
            for (HRPlusNode n : curr.getNodes()) {
                if (n.getChild() != null) {
                    unvisited.add(n.getChild());
                }
            }
        }
        return containers;
    }

}
