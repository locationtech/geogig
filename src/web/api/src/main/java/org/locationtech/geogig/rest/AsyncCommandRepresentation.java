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
import org.locationtech.geogig.web.api.StreamWriterException;
import org.restlet.data.MediaType;

import com.google.common.base.Optional;
import com.google.common.base.Strings;

import org.locationtech.geogig.web.api.StreamingWriter;

public abstract class AsyncCommandRepresentation<T> extends StreamingWriterRepresentation {

    protected final AsyncCommand<T> cmd;

    public AsyncCommandRepresentation(MediaType mediaType, AsyncCommand<T> cmd, String baseURL) {
        super(mediaType, baseURL);
        checkNotNull(mediaType);
        checkNotNull(cmd);
        this.cmd = cmd;
    }

    @Override
    protected void write(StreamingWriter w) throws StreamWriterException {
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
        encodeAlternateAtomLink(w, link);
        if (cmd.isDone()) {
            T result;
            try {
                result = cmd.get();
                writeResult(w, result);
            } catch (InterruptedException e) {
                writeError(w, e);
            } catch (ExecutionException e) {
                writeError(w, e.getCause());
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
        w.writeLargeElement("stackTrace", statckTrace);

        w.writeEndElement();
    }

    protected abstract void writeResultBody(StreamingWriter w, T result) throws StreamWriterException;

}