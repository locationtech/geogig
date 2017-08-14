/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.web.api;

import java.io.IOException;
import java.io.Writer;

import org.locationtech.geogig.rest.WriterRepresentation;
import org.restlet.data.MediaType;

public class CommandResponseStreamingWriterRepresentation extends WriterRepresentation {

    final CommandResponse impl;

    String callback;

    public CommandResponseStreamingWriterRepresentation(MediaType mediaType, CommandResponse impl,
            String callback) {
        super(mediaType);
        this.impl = impl;
        this.callback = callback;
    }

    private StreamingWriter createWriter(Writer writer) {
        return StreamingWriterFactory.getStreamWriter(getMediaType(), writer);
    }

    @Override
    public void write(Writer writer) throws IOException {
        if (callback != null) {
            writer.write(callback);
            writer.write('(');
        }
        try {
            final StreamingWriter streamWriter = createWriter(writer);
            // impl.write(new ResponseWriter(streamWriter, getMediaType()));
            streamWriter.flush();
            impl.close();
        } catch (Exception ex) {
            throw new IOException(ex);
        }
        if (callback != null) {
            writer.write(");");
        }
    }
}