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

import static org.locationtech.geogig.rest.repository.RESTUtils.getStringAttribute;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.ExecutionException;

import org.locationtech.geogig.rest.AsyncContext.AsyncCommand;
import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.FileRepresentation;
import org.restlet.resource.Representation;
import org.restlet.resource.Resource;
import org.restlet.resource.Variant;

import com.google.common.base.Optional;
import com.google.common.io.Files;

/**
 * Resource for {@code /tasks/<tasksId>/download}, sends the {@link File} result of an
 * {@link AsyncCommand} as a binary stream and deletes the file.
 * <p>
 * Output {@link MediaType} is resolved by file extension. Supported formats are
 * {@link Variants#JSON JSON}, {@link Variants#XML XML}, and {@link Variants#GEOPKG GEOPKG}. If the
 * file extension doesn't match any of the supported formats, the returned media type is the generic
 * {@link MediaType#APPLICATION_OCTET_STREAM}
 * <p>
 * Preconditions:
 * <ul>
 * <li>The {@link AsyncCommand} for {@code <taskId>} exists and is {@link AsyncCommand#isDone()
 * finished}
 * <li>The {@link AsyncCommand}'s result is a {@link File} object
 * </ul>
 * <p>
 * Postconditions:
 * <ul>
 * <li>The file returned by the async command is deleted after successfully serving it.
 * </p>
 */
public class TaskResultDownloadResource extends Resource {

    @Override
    public void init(Context context, Request request, Response response) {
        super.init(context, request, response);
        getVariants().add(Variants.XML);
        getVariants().add(Variants.JSON);
        getVariants().add(Variants.GEOPKG);
    }

    @Override
    public Variant getPreferredVariant() {
        return Variants.XML;
    }

    @Override
    public void handleGet() {
        try {
            super.handleGet();
        } catch (RestletException e) {
            Response response = getResponse();
            response.setStatus(e.getStatus(), e.getMessage());
            response.setEntity(e.getRepresentation());
        }
    }

    @Override
    public Representation getRepresentation(Variant variant) {
        final Request request = getRequest();

        final String taskId = getStringAttribute(request, "taskId");

        final AsyncContext asyncContext = AsyncContext.get();

        Optional<AsyncCommand<?>> cmd = asyncContext.get(taskId);
        if (!cmd.isPresent()) {
            throw new RestletException("Task not found: " + taskId, Status.CLIENT_ERROR_NOT_FOUND);
        }

        AsyncCommand<?> command = cmd.get();

        if (!command.isDone()) {
            throw new RestletException("Task is not yet finished: " + command.getStatus(),
                    Status.CLIENT_ERROR_EXPECTATION_FAILED);
        }

        Object result;
        try {
            result = command.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RestletException("Error getting command result: " + e.getMessage(),
                    Status.SERVER_ERROR_INTERNAL, e);
        }
        if (!(result instanceof File)) {
            throw new RestletException("Task result is not a file", Status.CLIENT_ERROR_BAD_REQUEST);
        }

        final File file = (File) result;
        if (!file.exists() || !file.canRead() || !file.isFile()) {
            throw new RestletException("Result file does not exist or is not accessible",
                    Status.CLIENT_ERROR_BAD_REQUEST);
        }

        final MediaType mediaType = resolveMediaType(file);
        return new FileRepresentation(file, mediaType, 0/* seconds */) {
            @Override
            public void write(OutputStream outputStream) throws IOException {
                try {
                    super.write(outputStream);
                } finally {
                    file.delete();
                }
            }

            @Override
            public void write(WritableByteChannel writableChannel) throws IOException {
                try {
                    super.write(writableChannel);
                } finally {
                    file.delete();
                }
            }
        };
    }

    private MediaType resolveMediaType(File file) {
        String extension = Files.getFileExtension(file.getName());

        if ("gpkg".equalsIgnoreCase(extension)) {
            return Variants.GEOPKG_MEDIA_TYPE;
        } else if ("xml".equalsIgnoreCase(extension)) {
            return MediaType.TEXT_XML;
        } else if ("json".equalsIgnoreCase(extension)) {
            return Variants.JSON.getMediaType();
        }

        return MediaType.APPLICATION_OCTET_STREAM;
    }

}
