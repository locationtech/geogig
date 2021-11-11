/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import java.util.List;

import org.locationtech.geogig.base.Preconditions;
import org.locationtech.geogig.feature.PropertyDescriptor;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;

/**
 * Retrieves the set of property descriptors for the given feature type.
 */
public class DescribeFeatureType extends AbstractGeoGigOp<List<PropertyDescriptor>> {

    private RevFeatureType featureType;

    /**
     * @param featureType the {@link RevFeatureType} to describe
     */
    public DescribeFeatureType setFeatureType(RevFeatureType featureType) {
        this.featureType = featureType;
        return this;
    }

    /**
     * Retrieves the set of property descriptors for the given feature type.
     * 
     * @return a sorted set of all the property descriptors of the feature type.
     */
    protected @Override List<PropertyDescriptor> _call() {
        Preconditions.checkState(featureType != null, "FeatureType has not been set.");
        return featureType.descriptors();
    }
}
