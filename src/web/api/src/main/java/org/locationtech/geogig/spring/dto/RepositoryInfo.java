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
 * Bean for a Repository response.
 */
@XmlRootElement(name = "repo")
@XmlAccessorType(XmlAccessType.FIELD)
public class RepositoryInfo {

    @XmlElement
    private String id = java.util.UUID.randomUUID().toString();

    @XmlElement
    private String name;

    @XmlElement
    private String location = "file:///tmp/geoig/repo";

    public String getName() {
        return name;
    }

    public RepositoryInfo setName(String name) {
        this.name = name;
        return this;
    }

    public String getLocation() {
        return location;
    }

    public RepositoryInfo setLocation(String location) {
        this.location = location;
        return this;
    }

    public String getId() {
        return id;
    }

    public RepositoryInfo setId(String id) {
        this.id = id;
        return this;
    }
}
