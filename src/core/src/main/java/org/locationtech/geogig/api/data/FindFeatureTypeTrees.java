/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api.data;

import java.util.Iterator;
import java.util.List;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.plumbing.LsTreeOp;
import org.locationtech.geogig.api.plumbing.RevParse;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;

/**
 * 
 */
public class FindFeatureTypeTrees extends AbstractGeoGigOp<List<NodeRef>> {

    private String refSpec;

    /**
     * @param refSpec a ref spec, as supported by {@link RevParse}, that resolves to the root tree
     *        that's to be inspected for leaf trees with metadata ids set; most common use being of
     *        the type {@code <head name>[:<path>]}
     */
    public FindFeatureTypeTrees setRootTreeRef(String refSpec) {
        this.refSpec = refSpec;
        return this;
    }

    @Override
    protected  List<NodeRef> _call() {
        Preconditions.checkNotNull(refSpec, "refSpec was not provided");
        Iterator<NodeRef> allTrees;
        try {
            allTrees = context.command(LsTreeOp.class).setReference(refSpec)
                    .setStrategy(LsTreeOp.Strategy.DEPTHFIRST_ONLY_TREES).call();
        } catch (IllegalArgumentException noWorkHead) {
            return ImmutableList.of();
        }
        Iterator<NodeRef> typeTrees = Iterators.filter(allTrees, new Predicate<NodeRef>() {
            @Override
            public boolean apply(NodeRef input) {
                ObjectId metadataId = input.getMetadataId();
                return !metadataId.isNull();
            }
        });

        return ImmutableList.copyOf(typeTrees);
    }

}
