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

import org.restlet.data.MediaType;
import org.restlet.resource.FileRepresentation;

import com.google.common.base.Preconditions;

/**
 * Restlet representation for Asynchronous commands that produce binary file output, as opposed to
 * XML or JSON responses.
 */
public abstract class AsyncCommandBinaryRepresentation<T> extends FileRepresentation {

    protected final AsyncContext.AsyncCommand<T> command;

    public AsyncCommandBinaryRepresentation(File file, MediaType mediaType,
        AsyncContext.AsyncCommand<T> command) {
        super(file, mediaType, 0);
        Preconditions.checkNotNull(mediaType);
        this.command = command;
    }
}
