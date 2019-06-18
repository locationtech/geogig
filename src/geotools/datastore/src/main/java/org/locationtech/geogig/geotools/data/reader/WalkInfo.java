/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.data.reader;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.DiffTree;
import org.locationtech.geogig.porcelain.index.Index;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;

public class WalkInfo {
    public SimpleFeatureType fullSchema;

    // query filter in native CRS
    public Filter nativeFilter;

    public Filter preFilter;

    public Filter postFilter;

    @Nullable
    Set<String> requiredProperties;

    // properties present in the RevTree nodes' extra data
    public Set<String> materializedIndexProperties = Collections.emptySet();

    // whether the RevTree nodes contain all required properties (hence no need to fetch
    // RevFeatures from the database)
    public boolean indexContainsAllRequiredProperties;

    // whether the filter is fully supported by the NodeRef filtering (hence no need for
    // pos-processing filtering). This is the case if the filter is a simple BBOX, Id, or
    // INCLUDE, or all the required properties are present in the index Nodes
    public boolean filterIsFullySupportedByIndex;

    public boolean diffUsesIndex;

    public DiffTree diffOp;

    public ScreenMapPredicate screenMapFilter;

    public Optional<Index> leftIndex, rightIndex;

    public ObjectId leftTree, rightTree;

    public Optional<Ref> leftRef, rightRef;

}