/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.impl;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.plumbing.HashObject;
import org.opengis.feature.type.FeatureType;

public class RevFeatureTypeBuilder {

    public static RevFeatureType build(FeatureType featureType) {
        return RevFeatureTypeBuilder.build(null, featureType);
    }

    public static RevFeatureType build(@Nullable ObjectId id, FeatureType ftype) {
        if (id == null) {
            RevFeatureTypeImpl unnamed = new RevFeatureTypeImpl(ftype);
            id = new HashObject().setObject(unnamed).call();
        }

        return new RevFeatureTypeImpl(id, ftype);
    }

}
