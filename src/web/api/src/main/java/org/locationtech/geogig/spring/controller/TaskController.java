/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.spring.controller;

import static org.locationtech.geogig.rest.repository.RepositoryProvider.GEOGIG_ROUTE_PREFIX;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_XML_VALUE;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.locationtech.geogig.rest.AsyncCommandRepresentation;
import org.locationtech.geogig.rest.AsyncContext;
import org.locationtech.geogig.rest.AsyncContext.AsyncCommand;
import org.locationtech.geogig.rest.Representations;
import org.locationtech.geogig.rest.Variants;
import org.locationtech.geogig.rest.repository.ParameterSetFactory;
import org.locationtech.geogig.spring.dto.LegacyResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.StreamingWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.base.Optional;
import com.google.common.io.Files;

/**
 * Controller for Task related endpoints.
 * <pre>
 * /tasks
 * /tasks/{taskId}
 * /tasks/{taskId}/download
 * </pre>
 */
@RestController
@RequestMapping(path = GEOGIG_ROUTE_PREFIX + "/tasks",
        produces = {APPLICATION_XML_VALUE, APPLICATION_JSON_VALUE})
public class TaskController extends AbstractController {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskController.class);

    @GetMapping
    public void getTaskList(HttpServletRequest request, HttpServletResponse response) {
        final AsyncContext asyncContext = AsyncContext.get();
        final Iterable<AsyncCommand<? extends Object>> all = asyncContext.getAll();

        encode(new LegacyResponse() {
            @Override
            public void encodeInternal(StreamingWriter writer, MediaType format, String baseUrl) {
                // the StreamingWriter interface is a little lacking and can't handle this case for
                // XML and JSON correctly, so we have to implement a small hack for now
                final boolean isJSON = MediaType.APPLICATION_JSON.isCompatibleWith(format);
                if (isJSON) {
                    writer.writeStartArray("tasks");
                } else {
                    writer.writeStartElement("tasks");
                }
                for (AsyncCommand<? extends Object> c : all) {
                    if (isJSON) {
                        writer.writeStartArrayElement("tasks");
                    }
                    AsyncCommandRepresentation<?> rep = Representations.newRepresentation(c, false);
                    rep.encodeInternal(writer, format, baseUrl);
                    if (isJSON) {
                        writer.writeEndArrayElement();
                    }
                }
                if (isJSON) {
                    writer.writeEndArray();
                } else {
                    writer.writeEndElement();
                }

            }
        }, request, response);

    }

    @GetMapping(path = "/{taskId}")
    public void getTaskStatus(@PathVariable String taskId,
            @RequestParam MultiValueMap<String, String> params, HttpServletRequest request,
            HttpServletResponse response, RequestEntity<String> entity) {
        ParameterSet options = ParameterSet.concat(getParamsFromEntity(entity),
                ParameterSetFactory.buildParameterSet(params));
        boolean prune = Boolean.parseBoolean(options.getFirstValue("prune", "false"));
        boolean cancel = Boolean.parseBoolean(options.getFirstValue("cancel", "false"));
        final AsyncContext asyncContext = AsyncContext.get();

        Optional<AsyncCommand<?>> cmd;

        if (prune) {
            cmd = asyncContext.getAndPruneIfFinished(taskId);
        } else {
            cmd = asyncContext.get(taskId);
        }
        if (!cmd.isPresent()) {
            throw new CommandSpecException("Task not found: " + taskId, HttpStatus.NOT_FOUND);
        }

        AsyncCommand<?> command = cmd.get();
        if (cancel) {
            command.tryCancel();
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                // ignore
            }
            if (prune) {
                asyncContext.getAndPruneIfFinished(taskId);
            }
        }
        AsyncCommandRepresentation<?> rep = Representations.newRepresentation(command, prune);
        encode(rep, request, response);
    }

    @GetMapping(path = "/{taskId}/download")
    public @ResponseBody
            HttpEntity<FileSystemResource> getDownload(@PathVariable String taskId)
                    throws IOException {
        final AsyncContext asyncContext = AsyncContext.get();

        Optional<AsyncCommand<?>> cmd = asyncContext.get(taskId);
        if (!cmd.isPresent()) {
            throw new CommandSpecException("Task not found: " + taskId, HttpStatus.NOT_FOUND);
        }

        AsyncCommand<?> command = cmd.get();

        if (!command.isDone()) {
            throw new CommandSpecException("Task is not yet finished: " + command.getStatus(),
                    HttpStatus.EXPECTATION_FAILED);
        }

        Object result;
        try {
            result = command.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new CommandSpecException("Error getting command result: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
        if (!(result instanceof File)) {
            throw new CommandSpecException("Task result is not a file", HttpStatus.BAD_REQUEST);
        }

        final File file = (File) result;
        if (!file.exists() || !file.canRead() || !file.isFile()) {
            throw new CommandSpecException("Result file does not exist or is not accessible",
                    HttpStatus.BAD_REQUEST);
        }

        MediaType mediaType = resolveMediaType(file);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);

        return new HttpEntity<FileSystemResource>(new FileSystemResource(file), headers);
    }

    private MediaType resolveMediaType(File file) {
        String extension = Files.getFileExtension(file.getName());

        if ("gpkg".equalsIgnoreCase(extension)) {
            return Variants.GEOPKG_MEDIA_TYPE;
        } else if ("xml".equalsIgnoreCase(extension)) {
            return MediaType.TEXT_XML;
        } else if ("json".equalsIgnoreCase(extension)) {
            return MediaType.APPLICATION_JSON;
        }

        return MediaType.APPLICATION_OCTET_STREAM;
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }
}
