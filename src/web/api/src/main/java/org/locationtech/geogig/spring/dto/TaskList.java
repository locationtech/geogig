/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://wwwriter.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.spring.dto;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.web.api.StreamingWriter;
import org.springframework.http.MediaType;

/**
 *
 */
@XmlRootElement
@XmlAccessorType(value = XmlAccessType.FIELD)
public class TaskList extends LegacyResponse {

    @XmlElement
    private List<TaskInfo> tasks;

    public TaskList() {
    }

    public TaskList(List<TaskInfo> tasks) {
        super();
        this.tasks = tasks;
    }

    public List<TaskInfo> getTasks() {
        return tasks;
    }

    public TaskList setTasks(List<TaskInfo> tasks) {
        this.tasks = tasks;
        return this;
    }

    @Override
    public void encodeInternal(StreamingWriter writer, MediaType format, String baseUrl) {
        writer.writeStartElement("tasks");
        if (tasks != null) {
            for (TaskInfo task : tasks) {
                String repoName = task.getName();
                writer.writeStartArrayElement("repo");
                writer.writeElement("name", repoName);
                encodeAlternateAtomLink(writer, baseUrl,
                        RepositoryProvider.BASE_REPOSITORY_ROUTE + "/" + repoName, format);
                writer.writeEndArrayElement();
            }
        }
        writer.writeEndArray();
        writer.writeEndElement();
    }
}
