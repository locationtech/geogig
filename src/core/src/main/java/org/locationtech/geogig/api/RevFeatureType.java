/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api;

import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.Name;
import org.opengis.feature.type.PropertyDescriptor;

import com.google.common.collect.ImmutableList;

public interface RevFeatureType extends RevObject {

    public abstract FeatureType type();

    /**
     * @return the sorted {@link PropertyDescriptor}s of the feature type
     */
    public abstract ImmutableList<PropertyDescriptor> sortedDescriptors();

    /**
     * @return the name of the feature type
     */
    public abstract Name getName();

}