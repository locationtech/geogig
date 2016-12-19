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

import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.PropertyDescriptor;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

/**
 * Retrieves the set of property descriptors for the given feature type.
 */
public class DescribeFeatureType extends AbstractGeoGigOp<ImmutableSet<PropertyDescriptor>> {

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
    @Override
    protected ImmutableSet<PropertyDescriptor> _call() {
        Preconditions.checkState(featureType != null, "FeatureType has not been set.");

        FeatureType type = featureType.type();

        ImmutableSet.Builder<PropertyDescriptor> propertySetBuilder = new ImmutableSet.Builder<PropertyDescriptor>();

        propertySetBuilder.addAll(type.getDescriptors());

        return propertySetBuilder.build();
    }
}
