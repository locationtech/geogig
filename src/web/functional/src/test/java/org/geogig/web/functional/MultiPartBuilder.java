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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.UUID;

import com.google.common.io.ByteStreams;

/**
 * Utility class to create a {@code multipart/form-data} request body as defined in
 * <a href="https://www.ietf.org/rfc/rfc2388.txt">RFC2388</a>
 *
 */
public class MultiPartBuilder {

    /**
     * Must not occur in any other part of the request body
     */
    final String boundary = "===" + UUID.randomUUID().toString() + "===";

    private static final String CRLF = "\r\n";

    private ByteArrayOutputStream out;

    private String characterSet;

    public MultiPartBuilder(String charset) {
        this.characterSet = charset;
        out = new ByteArrayOutputStream();
    }

    public void addField(String name, String value) throws IOException {
        Writer writer = new OutputStreamWriter(out, characterSet);
        writer.append("--" + boundary).append(CRLF);
        writer.append("Content-Disposition: form-data; name=\"" + name + "\"").append(CRLF);
        writer.append("Content-Type: text/plain; charset=" + characterSet).append(CRLF);
        writer.append(CRLF);
        writer.append(value).append(CRLF);
        writer.flush();
    }

    public void addFile(final File file, final String fieldName) throws IOException {

        Writer writer = new OutputStreamWriter(out, characterSet);
        String fileName = file.getName();

        writer.append("--")//
                .append(boundary)//
                .append(CRLF);

        writer.append("Content-Disposition: form-data; name=\"")//
                .append(fieldName)//
                .append("\"; filename=\"")//
                .append(fileName)//
                .append("\"")//
                .append(CRLF);

        writer.append("Content-Type: application/octect-stream")//
                .append(CRLF);

        writer.append("Content-Transfer-Encoding: binary")//
                .append(CRLF);
        writer.append(CRLF);
        writer.flush();

        try (FileInputStream in = new FileInputStream(file)) {
            ByteStreams.copy(in, out);
        }
        out.flush();

        writer.append(CRLF);
        writer.flush();
    }

    public InputStream build() throws IOException {
        Writer writer = new OutputStreamWriter(out, characterSet);
        writer.append(CRLF)//
                .append("--")//
                .append(boundary)//
                .append("--")//
                .append(CRLF);
        writer.close();

        return new ByteArrayInputStream(out.toByteArray());
    }

}
