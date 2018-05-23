package org.locationtech.geogig.model;

import java.io.Serializable;

import com.google.common.collect.Ordering;

public abstract class NodeOrdering extends Ordering<Node> implements Serializable {
    private static final long serialVersionUID = 1L;

    public abstract @Override int compare(Node left, Node right);

    public abstract int bucket(final Node ref, final int depth);
}
