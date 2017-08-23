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
import org.locationtech.geogig.spring.dto.LegacyResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.StreamingWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.base.Optional;

/**
 * Controller for Task related endpoints.
 * <pre>
 * /tasks
 * /tasks/{taskId}
 * /tasks/{taskId}/download
 * </pre>
 */
@RestController
@RequestMapping(path = "/tasks",
        produces = {APPLICATION_XML_VALUE, APPLICATION_JSON_VALUE})
public class TaskController extends AbstractController {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskController.class);

    @GetMapping
    public void getTaskList(HttpServletRequest request, HttpServletResponse response) {
        final AsyncContext asyncContext = AsyncContext.get();
        final Iterable<AsyncCommand<? extends Object>> all = asyncContext.getAll();

        encode(new LegacyResponse() {
            @Override
            public void encode(StreamingWriter writer, MediaType format, String baseUrl) {
                writer.writeStartElement("tasks");
                for (AsyncCommand<? extends Object> c : all) {
                    AsyncCommandRepresentation<?> rep = Representations.newRepresentation(c, false);
                    rep.encode(writer, format, baseUrl);
                }
                writer.writeEndElement();

            }
        }, request, response);

    }

    @GetMapping(path = "/{taskId}")
    public void getTaskStatus(@PathVariable String taskId,
            @RequestAttribute(required = false) Boolean prune,
            @RequestAttribute(required = false) Boolean cancel, HttpServletRequest request,
            HttpServletResponse response) {
        final AsyncContext asyncContext = AsyncContext.get();

        Optional<AsyncCommand<?>> cmd;

        if (Boolean.TRUE.equals(prune)) {
            cmd = asyncContext.getAndPruneIfFinished(taskId);
        } else {
            cmd = asyncContext.get(taskId);
        }
        if (!cmd.isPresent()) {
            throw new CommandSpecException("Task not found: " + taskId, HttpStatus.NOT_FOUND);
        }

        AsyncCommand<?> command = cmd.get();
        if (Boolean.TRUE.equals(cancel)) {
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
        AsyncCommandRepresentation<?> rep = Representations.newRepresentation(command,
                Boolean.TRUE.equals(prune));
        encode(rep, request, response);
    }

    @GetMapping(path = "/{taskId}/download")
    public @ResponseBody
    File getDownload(@PathVariable String taskId) throws IOException {
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

        return file;
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }
}
