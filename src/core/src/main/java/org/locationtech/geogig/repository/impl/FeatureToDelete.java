/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository.impl;

import java.util.Iterator;

import org.locationtech.geogig.feature.Feature;
import org.locationtech.geogig.feature.FeatureType;

import lombok.NonNull;

/**
 * An object representing a feature to be deleted. When this is inserted into the working tree of a
 * repository, the feature with the specified path and name will be deleted instead
 * 
 */
public class FeatureToDelete extends org.locationtech.geogig.feature.Feature {

    public FeatureToDelete(@NonNull String id, @NonNull FeatureType type) {
        super(id, type);
    }

    public @Override String getVersion() {
        return null;
    }

    public @Override Iterator<Object> iterator() {
        throw new UnsupportedOperationException();
    }

    public @Override Object getAttribute(int index) {
        throw new UnsupportedOperationException();
    }

    public @Override void setAttribute(int index, Object value) {
        throw new UnsupportedOperationException();
    }

    public @Override Feature createCopy(@NonNull String newId) {
        throw new UnsupportedOperationException();
    }
}
