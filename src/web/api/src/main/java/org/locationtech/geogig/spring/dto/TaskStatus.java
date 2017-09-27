/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.spring.dto;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.locationtech.geogig.rest.AsyncContext.AsyncCommand;
import org.locationtech.geogig.rest.AsyncContext.Status;

import com.google.common.base.Optional;
import com.google.common.base.Strings;

/**
 *
 */
@XmlRootElement(name = "task")
@XmlAccessorType(value = XmlAccessType.FIELD)
public class TaskStatus {

    @XmlElement
    private String id;

    @XmlElement
    private String status;

    @XmlElement
    private String transactionId;

    @XmlElement
    private String description;

    @XmlElement
    private AtomLink link;

    @XmlElement
    private TaskProgress progress;

    public <T> TaskStatus(AsyncCommand<T> cmd) {
        this.id = cmd.getTaskId();
        this.status = cmd.getStatus().toString();
        this.description = cmd.getDescription();
        final Optional<UUID> transactionId = cmd.getTransactionId();
        if (transactionId.isPresent()) {
            this.transactionId = transactionId.get().toString();
        }

        if (cmd.isDone()) {
            T result;
            try {
                result = cmd.get();
                // writeResult(w, result);
            } catch (InterruptedException e) {
                // writeError(w, e);
            } catch (ExecutionException e) {
                // writeError(w, e.getCause());
            }
        } else if (cmd.getStatus() == Status.RUNNING) {
            String statusLine = cmd.getStatusLine();
            if (!Strings.isNullOrEmpty(statusLine)) {
                this.progress = new TaskProgress(String.valueOf(statusLine), cmd.getProgress());
            }
        }
    }

    public String getTaskid() {
        return id;
    }

    public TaskStatus setTaskid(String taskid) {
        this.id = taskid;
        return this;
    }

    public static class TaskProgress {
        @XmlElement
        private String task;

        @XmlElement
        private float amount;

        public TaskProgress(String task, float amount) {
            this.task = task;
            this.amount = amount;
        }
    }
}
