/* Copyright (c) 2014 OpenPlans. All rights reserved.
 * This code is licensed under the BSD New License, available at the root
 * application directory.
 */
package org.geogit.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vividsolutions.jts.geom.Envelope;

/**
 * The core building block of HR+ Trees. A container node surrounds data
 * envelopes, representing them by their minimum bounding rectangle (MBR). Nodes
 * within a container should be close, spatially, and every sub-container of
 * this container should have an MBR contained within this MBR.
 * <p>
 * We use containers rather than pure nodes as the basic unit within an HR+ Tree
 * to track overflow. Ideally, data will be spread evenly throughout the data
 * structure; containers ensure this property. If, during insert, we add too
 * many nodes to one container, the container is split into two and its nodes
 * repartitioned. The method {@link HRPlusTree#insert} performs the check and
 * repartitioning.
 * 
 * <h4>Notes</h4>
 * <ul>
 * <li>Need to instantiate object id. Currently, it's null.
 * <ul>
 */
public class HRPlusContainerNode implements RevObject {

    /**
     * Map of nodes inhabiting this container. Keys are the unique
     * {@code ObjectId} for the nodes.
     */
    private List<HRPlusNode> childNodes = new ArrayList<HRPlusNode>();

    /**
     * Unique id describing this container. 
     * TODO never set
     */
    private ObjectId objectId;

    /**
     * Parent of this container.
     * Will be null if this container is a root.
     */
    private HRPlusNode parentNode;

    /**
     * Assuming a container contains nodes belonging to the same version.
     */
    private ObjectId versionId;

    public HRPlusContainerNode() {
        // TODO: Set objectid
    }

    public HRPlusContainerNode(HRPlusNode parent, ObjectId versionId) {
        super();
        this.parentNode = parent;
        this.versionId = versionId;
    }

    /**
     * Gets the objectId of this container.
     * 
     * @return this.objectId not null
     */
    public ObjectId getObjectId() {
        return this.objectId;
    }

    /**
     * Gets the parentNode of this container.
     * 
     * @return this.parentNode may be null
     */
    public HRPlusNode getParentNode() {
        return this.parentNode;
    }

    /**
     * Gets the versionId of this container 
     * 
     * @return this.versionId
     */
    public ObjectId getVersionId() {
        return this.versionId;
    }

    /**
     * Counts the number of nodes in this container. Does not count nodes within
     * sub-containers.
     * 
     * @return the number of direct child nodes of this container
     */
    public int getNumNodes() {
        return this.childNodes.size();
    }

    /**
     * Sets the parentNode of this container.
     */
    public void setParentNode(HRPlusNode parentNode) {
        this.parentNode = parentNode;
    }

    /**
     * Adds a node to this container. 
     * Simply inserts the node into {@code nodeMap}.
     * If the objectId of {@code node} is identical to an id existing in {@code nodeMap}, erase the existing node.
     *
     * @param node  the node to insert
     */
    public void addNode(HRPlusNode node) {
        this.childNodes.add(node);
        node.setParentContainer(this);
    }

    /**
     * Removes a node from this container.
     * Specifically, removes the node whose object id matches {@code objectId}
     * 
     * @param objectId
     * @return the node just removed, null if nothing in {@code nodeMap} matched {@code objectId}
     */
    public boolean removeNode(HRPlusNode node) {
        return this.childNodes.remove(node);
    }

    /**
     * Create a list of the nodes stored in this container.
     * 
     * @return a list containing the direct children of this container
     */
    public List<HRPlusNode> getNodes() {
        return new ArrayList<HRPlusNode>(this.childNodes);
    }


    /**
     * Return all the leaf nodes under this container.
     * If this is a leaf container return the nodes it contains,
     * otherwise, for each node that it contains, call recursively
     * 
     * @return All leaf nodes under this container
     */
    public List<HRPlusNode> getLeafNodes(){
        List<HRPlusNode> leafNodes = new ArrayList<HRPlusNode>() ;

        if(this.isLeaf())
            leafNodes.addAll(getNodes());
        else
        {
            List<HRPlusNode> nodes = this.getNodes();
            for(HRPlusNode n:nodes ){
                leafNodes.addAll(n.getLeafNodes());
            }
        }
        return leafNodes;
    }

    /**
     * Determine whether this container is a leaf.
     * A leaf has no sub-containers (a leaf may contain nodes).
     * <p>
     * An HR+ tree is balanced by construction, so if one node within this
     * container has no children, none of the nodes within this container do.
     * 
     * @return boolean indicating whether this container is a leaf
     */
    public boolean isLeaf() {
        return this.childNodes.isEmpty() || this.childNodes.get(0).isLeaf();
    }

    /**
     * Check if the container does not have any nodes.
     * 
     * @return boolean indicating whether this container has any nodes inside.
     */
    public boolean isEmpty() {
        return this.childNodes.isEmpty();
    }

    /**
     * Check whether any children of this node are leaves.
     * Used in {@link HRPlusTree#chooseSubtree}.
     * 
     * @return boolean indicating whether any sub-container of this container
     *         have leaves.
     */
    public boolean isOneStepAboveLeafLevel() {
        if (this.isLeaf()) {
            return false;
        }
        // Not a leaf, so getNodes will return a non-empty list.
        HRPlusContainerNode nextLevel = this.getNodes().get(0).getChild();
        return nextLevel.isLeaf();
    }

    /**
     * Gets the version ids of each node within this container.
     * 
     * @return list of version ids, one for each node in this container
     */
    public List<ObjectId> getVersionIds() {
        List<ObjectId> ids = new ArrayList<ObjectId>();
        for (HRPlusNode node : this.getNodes()) {
            ids.add(node.getVersionId());
        }
        return ids;
    }

    /**
     * Compute the minimum bounding rectangle for nodes in this container. 
     * MBR is empty if the container is empty. 
     * MBR implicitly covers all nodes in sub-containers; those nodes are not checked in this function.
     * 
     * @return minimum bounding rectangle surrounding contained nodes.
     */
    public Envelope getMBR() {
        Envelope env = new Envelope();
        for (HRPlusNode node : this.childNodes) {
            node.expand(env);
        }
        return env;
    }

    /**
     * Calculates overlap between this container's MBR and the argument envelope.
     * 
     * @param env  Envelope to compare to this container's MBR.
     * @return the envelope obtained by intersecting @param env with this
     *         container's MBR
     */
    public Envelope getOverlap(Envelope env) {
        return this.getMBR().intersection(env);
    }

    /**
     * Search this container for nodes within the argument envelope and recurse
     * into their containers.
     * 
     * @param env
     * @param matches  list of nodes across the entire tree that fit in this envelope
     */
    public void query(Envelope env, List<HRPlusNode> matches) {
        if (this.getMBR().intersects(env)) {
            List<HRPlusNode> nodes = this.getNodes();
            for (HRPlusNode n : nodes) {
                n.query(env, matches);
            }
        }
        return;
    }

    /**
     * Return all the nodes in this container and all the nodes in the child containers.
     * 
     * @return all the nodes at and below this container
     */
    public List<HRPlusNode> getNodesForContainer() {
        List<HRPlusNode> nodes = new ArrayList<HRPlusNode>();
        // add all the nodes in this container
        nodes.addAll(this.getNodes());

        if (this.isLeaf()) // if its a leaf we are done, otherwise recurse
            return nodes;
        else {
            for (HRPlusNode node : this.getNodes()) {
                // get the nodes for child container this node points to
                nodes.addAll(node.getChild().getNodesForContainer());
            }
        }
        return nodes;
    }

    @Override
        public TYPE getType() {
            // TODO Auto-generated method stub
            return null;
        }

    @Override
        public ObjectId getId() {
            // TODO Auto-generated method stub
            return null;
        }

}
