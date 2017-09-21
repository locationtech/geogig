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
 * Bean for each Repository inside a {@link RepositoryList} response.
 */
@XmlRootElement(name = "repo")
@XmlAccessorType(XmlAccessType.FIELD)
public class RepositoryListRepo {

    @XmlElement
    private String id;

    @XmlElement
    private String name;

    @XmlElement(namespace = "http://www.w3.org/2005/Atom")
    private AtomLink link = new AtomLink();

    public String getName() {
        return name;
    }

    public RepositoryListRepo setName(String name) {
        this.name = name;
        return this;
    }

    public AtomLink getLink() {
        return link;
    }

    public RepositoryListRepo setLink(AtomLink link) {
        this.link = link;
        return this;
    }

    public String getId() {
        return id;
    }

    public RepositoryListRepo setId(String id) {
        this.id = id;
        return this;
    }
}
