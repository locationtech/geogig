/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.web;

import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.restlet.data.Request;

import com.google.common.base.Optional;

public class SingleRepositoryProvider implements RepositoryProvider {

    private GeoGIG geogig;

    public SingleRepositoryProvider(GeoGIG geogig) {
        this.geogig = geogig;
    }

    @Override
    public Optional<GeoGIG> getGeogig(Request request) {
        return Optional.fromNullable(geogig);
    }

}
