/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest;

import java.io.File;

import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.restlet.data.MediaType;

/**
 * SPI Factory interface for asynchronous commands that produce binary file output.
 */
public interface CommandBinaryRepresentationFactory<R> {

    public boolean supports(Class<? extends AbstractGeoGigOp<?>> cmdClass);

    public AsyncCommandBinaryRepresentation<R> newRepresentation(AsyncContext.AsyncCommand<R> cmd,
            MediaType mediaType, File binary, boolean cleanup);
}
