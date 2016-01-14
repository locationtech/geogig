/* Copyright (c) 2014 OpenPlans. All rights reserved.
 * This code is licensed under the BSD New License, available at the root
 * application directory.
 */
package org.geogit.api;

import java.util.List;

import com.vividsolutions.jts.geom.Envelope;

/**
 * Contains data within an HR+ tree.
 * A node contains an envelope, a piece of the map.
 * Nodes have a parent container in the HR+ tree.
 * Nodes optionally have one container as a child.
 * <p>
 * Basically, a container is used for tree organization and a node is the data in the tree that the outside world cares about.
 */
public class HRPlusNode implements RevObject {

    /**
     * Unique id for this node. TODO never set
     */
    private ObjectId objectId;

    /**
     * Unique id for the parent of this node.
     * This field is set by {@link HRPlusContainerNode#addNode}.
     */
    private HRPlusContainerNode parentContainer;

    /**
     * Id corresponding to the map version this node belongs to.
     */
    private ObjectId versionId;

    /**
     * Bounds for the spatial data stored by this node.
     */
    protected Envelope bounds;

    /**
     * Container node underneath this node.
     */
    private HRPlusContainerNode child;

    public HRPlusNode(ObjectId objectId, Envelope bounds, ObjectId versionId) {
        this.bounds = bounds;
        this.objectId = objectId;
        this.versionId = versionId;
    }

    public HRPlusNode(Envelope bounds, ObjectId versionId) {
        this.objectId = ObjectId.forString(this.toString());
        this.bounds = bounds;
        this.versionId = versionId;
    }

    /**
     * Gets the unique object id of this node.
     * 
     * @return the unique id of this node
     */
    public ObjectId getObjectId() {
        return this.objectId;
    }

    /**
     * Gets the minimum X boundary of this node's envelope.
     * 
     * @return the minimum X bound of this node's envelope.
     */
    public double getMinX() {
        return this.bounds.getMinX();
    }

    /**
     * Gets the minimum Y boundary of this node's envelope.
     * 
     * @return the minimum Y bound of this node's envelope.
     */
    public double getMinY() {
        return this.bounds.getMinY();
    }

    /**
     * Gets the maximum X boundary of this node's envelope.
     * 
     * @return the maximum X bound of this node's envelope.
     */
    public double getMaxX() {
        return this.bounds.getMaxX();
    }

    /**
     * Gets the maximum Y boundary of this node's envelope.
     * 
     * @return the maximum Y bound of this node's envelope.
     */
    public double getMaxY() {
        return this.bounds.getMaxY();
    }

    /**
     * Increase the size of argument to include the bounds of this node.
     * 
     * @param env  envelope to expand
     */
    public void expand(Envelope env) {
        env.expandToInclude(this.getMinX(), this.getMinY());
        env.expandToInclude(this.getMaxX(), this.getMaxY());
    }

    /**
     * Gets the child container of this node.
     * 
     * @return the child container of this node, null if no child exists
     */
    public HRPlusContainerNode getChild() {
        return this.child;
    }

    /**
     * Sets the child container of this node.
     * 
     * @param child  container node to set as child
     */
    public void setChild(HRPlusContainerNode child) {
        this.child = child;
        child.setParentNode(this);
    }

    /**
     * Determine whether this node is a leaf.
     * Leaf nodes do not have a child container.
     * 
     * @return boolean indicating whether this node has a child
     */
    public boolean isLeaf() {
        return this.child == null;
    }

    /**
     * Gets the object id of this node's container.
     * 
     * @return the unique object id of this node's parent container
     */
    public HRPlusContainerNode getParentContainer() {
        return this.parentContainer;
    }

    /**
     * Gets the version id of this node.
     * 
     * @return the version id of this node
     */
    public ObjectId getVersionId() {
        return this.versionId;
    }

    /**
     * Sets the id of this node's parent.
     * 
     * @param containerId  unique object id of this nodes parent container.
     */
    public void setParentContainer(HRPlusContainerNode container) {
        this.parentContainer = container;
    }

    /**
     * Create a new envelope with bounds equal to this node's boundaries.
     * 
     * @return an envelope with bounds identical to the bounds of this node
     */
    public Envelope getBounds() {
        return new Envelope(this.getMinX(), this.getMaxX(), this.getMinY(),
                this.getMaxY());
    }

    /**
     * Sets the bounding envelope of this node.
     * 
     * @param env  new envelope to replace the bounds of the node with
     */
    public void setBounds(Envelope env) {
        this.bounds = env;
    }

    /**
     * Calculates the overlap between this node and the param envelope.
     * 
     * @param env  envelope to overlap this node's envelope with
     * @return the envelope obtained by intersecting
     */
    public Envelope getOverlap(Envelope env) {
        if (isLeaf()) {
            return this.getBounds().intersection(env);
        } else {
            // 2014-04-17: Not certain why we move to the child. Why not use
            // this node's bounds?
            return this.child.getOverlap(env);
        }
    }

    /**
     * Bounding box query. If this node fits in container, add it to list and
     * recurse on its child (if it has a child). Else end search.
     * 
     * @param env  envelope we restrict the search to
     * @param matches  list of nodes found so far, propagated downward
     */
    public void query(Envelope env, List<HRPlusNode> matches) {
        if (this.isLeaf()) {
            // We need to us contains() here. The node should be completely inside the query BBOX
            if (env.contains(this.bounds)) {
                // A match!
                matches.add(this);
            }
        }
        else {
            // Not a leaf, continue searching child if node's bbox intersects query bbox
            if (this.getBounds().intersects(env)) {
                this.getChild().query(env, matches);
            }
        }
    }

    /**
     * This method is called when this node is not a leaf node.
     * We look at all the leaf nodes pointed by its child container.
     * @return
     */
    public List<HRPlusNode> getLeafNodes(){
        return this.getChild().getLeafNodes();
    }

    @Override
        public boolean equals(Object node) {
            if (!(node instanceof HRPlusNode)) {
                return false;
            } else {
                HRPlusNode that = (HRPlusNode) node;
                return this.getObjectId().equals(that.getObjectId())
                    && this.getVersionId().equals(that.getVersionId())
                    && this.getBounds().equals(that.getBounds());
            }
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
