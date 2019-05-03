/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.data;

import org.geotools.data.store.ContentEntry;
import org.geotools.data.store.ContentState;
import org.locationtech.geogig.model.RevFeatureType;

/**
 * {@link ContentState} specialization to store geogig specific state among the regular state
 */
class GeogigContentState extends ContentState {

    private RevFeatureType nativeType;

    public GeogigContentState(ContentEntry entry) {
        super(entry);
    }

    protected GeogigContentState(GeogigContentState state) {
        super(state);
        this.nativeType = state.getNativeType();
    }

    @Override
    public GeogigContentState copy() {
        return new GeogigContentState(this);
    }

    public RevFeatureType getNativeType() {
        return nativeType;
    }

    public void setNativeType(RevFeatureType nativeType) {
        this.nativeType = nativeType;
    }

}
