/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.data;

import java.util.Iterator;
import java.util.List;

import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.plumbing.LsTreeOp;
import org.locationtech.geogig.plumbing.RevParse;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.NodeRef;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;

/**
 * 
 */
@CanRunDuringConflict
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
    protected List<NodeRef> _call() {
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
