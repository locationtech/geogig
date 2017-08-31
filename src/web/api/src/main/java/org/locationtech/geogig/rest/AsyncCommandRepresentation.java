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

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.locationtech.geogig.rest.AsyncContext.AsyncCommand;
import org.locationtech.geogig.rest.AsyncContext.Status;
import org.locationtech.geogig.spring.dto.LegacyResponse;
import org.locationtech.geogig.web.api.RESTUtils;
import org.locationtech.geogig.web.api.StreamWriterException;
import org.locationtech.geogig.web.api.StreamingWriter;
import org.springframework.http.MediaType;

import com.google.common.base.Optional;
import com.google.common.base.Strings;

public abstract class AsyncCommandRepresentation<T> extends LegacyResponse {

    protected final AsyncCommand<T> cmd;

    private final boolean cleanup;

    private MediaType mediaType;

    protected String baseURL;

    public AsyncCommandRepresentation(AsyncCommand<T> cmd, boolean cleanup) {
        checkNotNull(cmd);
        this.cmd = cmd;
        this.cleanup = cleanup;
    }

    public void encodeInternal(StreamingWriter writer, MediaType format, String baseUrl) {
        this.mediaType = format;
        this.baseURL = baseUrl;
        write(writer);
    }

    public void write(StreamingWriter w) throws StreamWriterException {
        final String taskId = cmd.getTaskId();
        final Status status = cmd.getStatus();
        final String description = cmd.getDescription();
        final Optional<UUID> transactionId = cmd.getTransactionId();
        checkNotNull(taskId);
        checkNotNull(status);
        w.writeStartElement("task");

        w.writeElement("id", taskId);
        w.writeElement("status", status.toString());
        if (transactionId.isPresent()) {
            w.writeElement("transactionId", transactionId.get().toString());
        }
        w.writeElement("description", description);
        String link = "tasks/" + taskId;// relative to baseURL (e.g. /geoserver/geogig)
        RESTUtils.encodeAlternateAtomLink(mediaType, w,
                RESTUtils.buildHref(baseURL, link, mediaType));
        if (cmd.isDone()) {
            T result;
            try {
                result = cmd.get();
                writeResult(w, result);
            } catch (InterruptedException e) {
                writeError(w, e);
            } catch (ExecutionException e) {
                writeError(w, e.getCause());
            } catch (Exception e) {
                writeError(w, e);
            }
        } else if (cmd.getStatus() == Status.RUNNING) {
            String statusLine = cmd.getStatusLine();
            if (!Strings.isNullOrEmpty(statusLine)) {
                w.writeStartElement("progress");
                w.writeElement("task", String.valueOf(statusLine));
                w.writeElement("amount", String.valueOf(cmd.getProgress()));
                w.writeEndElement();
            }
        }
        w.writeEndElement();// task
        if (cleanup) {
            cmd.close();
        }
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    protected void writeResult(StreamingWriter w, T result) throws StreamWriterException {
        w.writeStartElement("result");
        writeResultBody(w, result);
        w.writeEndElement();
    }

    protected void writeError(StreamingWriter w, Throwable cause) throws StreamWriterException {
        w.writeStartElement("error");

        w.writeElement("message", cause.getMessage());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        cause.printStackTrace(new PrintStream(out));
        String statckTrace = out.toString();
        w.writeCDataElement("stackTrace", statckTrace);

        w.writeEndElement();
    }

    protected abstract void writeResultBody(StreamingWriter w, T result) throws StreamWriterException;

}