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
import java.util.ArrayList;

import org.locationtech.geogig.spring.dto.TaskInfo;
import org.locationtech.geogig.spring.dto.TaskList;
import org.locationtech.geogig.spring.dto.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

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
    public TaskList getTaskList() {
        ArrayList<TaskInfo> tasks = new ArrayList<>();
        tasks.add(new TaskInfo(1, "first task"));
        tasks.add(new TaskInfo(2, "second task"));
        return new TaskList(tasks);
    }

    @GetMapping(path = "/{taskId}")
    public TaskStatus getTaskStatus(@PathVariable String taskId) {
        return new TaskStatus().setTaskid(taskId);
    }

    @GetMapping(path = "/{taskId}/download")
    public @ResponseBody
    File getDownload(@PathVariable String taskId) throws IOException {
        return File.createTempFile("Spring", "tmp");
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }
}
