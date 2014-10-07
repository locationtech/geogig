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

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.rest.AsyncContext.AsyncCommand;
import org.restlet.data.MediaType;
import org.restlet.resource.Representation;

/**
 * SPI interface to lookup {@link Representation} factories for concrete {@link AbstractGeoGigOp}
 * classes.
 *
 */
public interface CommandRepresentationFactory<R> {

    public boolean supports(Class<? extends AbstractGeoGigOp<?>> cmdClass);

    public AsyncCommandRepresentation<R> newRepresentation(AsyncCommand<R> cmd,
            MediaType mediaType, String baseURL);
}
