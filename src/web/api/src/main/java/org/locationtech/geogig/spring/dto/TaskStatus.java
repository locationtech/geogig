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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 */
@XmlRootElement
@XmlAccessorType(value = XmlAccessType.FIELD)
public class TaskStatus {

    @XmlElement
    private String taskid;

    public TaskStatus() {
        super();
    }

    public String getTaskid() {
        return taskid;
    }

    public TaskStatus setTaskid(String taskid) {
        this.taskid = taskid;
        return this;
    }
}
