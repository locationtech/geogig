/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest;

import static com.google.common.base.Preconditions.checkState;
import static org.locationtech.geogig.rest.Variants.getVariantByExtension;
import static org.locationtech.geogig.rest.repository.RESTUtils.getGeogig;

import java.util.UUID;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.GeogigTransaction;
import org.restlet.data.Form;
import org.restlet.data.Request;
import org.restlet.resource.Resource;
import org.restlet.resource.Variant;

import com.google.common.base.Optional;

/**
 * Base class for {@link Resource resources} that can be run inside a geogig
 * {@link GeogigTransaction transaction}.
 * <p>
 * The {@link Context} returned by {@link #getContext()} is a transactional context if the request
 * indicated a {@code transactionId}.
 */
public class TransactionalResource extends Resource {

    @Override
    public Variant getPreferredVariant() {
        return getVariantByExtension(getRequest(), getVariants()).or(super.getPreferredVariant());
    }

    protected Context getContext(Request request) {
        Optional<GeoGIG> geogig = getGeogig(request);
        checkState(geogig.isPresent());

        Context geogigContext = geogig.get().getContext();

        Form options = getRequest().getResourceRef().getQueryAsForm();
        String txId = options.getFirstValue("transactionId");
        if (txId != null) {
            UUID transactionId = UUID.fromString(txId);
            GeogigTransaction tx = new GeogigTransaction(geogigContext, transactionId);
            geogigContext = tx;
        }
        return geogigContext;
    }
}
