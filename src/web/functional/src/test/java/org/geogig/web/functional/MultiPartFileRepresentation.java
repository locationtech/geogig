/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.geogig.web.functional;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.restlet.data.CharacterSet;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.resource.StreamRepresentation;

import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;

public class MultiPartFileRepresentation extends StreamRepresentation {

    private MultiPartBuilder builder;

    public MultiPartFileRepresentation(final File file, final String fieldName) throws IOException {

        super(MediaType.MULTIPART_FORM_DATA);

        this.builder = new MultiPartBuilder(Charsets.UTF_8.name());
        this.builder.addFile(file, fieldName);

        final String boundary = this.builder.boundary;

        Form parameters = new Form();
        parameters.add("boundary", boundary);
        MediaType mediaType = new MediaType(MediaType.MULTIPART_FORM_DATA.getName(), parameters);
        super.setMediaType(mediaType);

        super.setCharacterSet(CharacterSet.UTF_8);
    }

    @Override
    public void write(OutputStream outputStream) throws IOException {
        try (InputStream in = getStream()) {
            ByteStreams.copy(in, outputStream);
        }
    }

    @Override
    public InputStream getStream() throws IOException {
        InputStream stream = this.builder.build();
        return stream;
    }
}
