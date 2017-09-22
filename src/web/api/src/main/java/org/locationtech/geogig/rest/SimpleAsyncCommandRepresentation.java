/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest;

import org.locationtech.geogig.rest.AsyncContext.AsyncCommand;
import org.locationtech.geogig.web.api.StreamWriterException;
import org.locationtech.geogig.web.api.StreamingWriter;

public class SimpleAsyncCommandRepresentation<T> extends AsyncCommandRepresentation<T> {

    public SimpleAsyncCommandRepresentation(AsyncCommand<T> c, boolean cleanup) {
        super(c, cleanup);
    }

    @Override
    protected void writeResult(StreamingWriter w, Object result) throws StreamWriterException {
        // do nothing
    }

    @Override
    protected void writeResultBody(StreamingWriter w, Object result) throws StreamWriterException {
        // do nothing
    }
}