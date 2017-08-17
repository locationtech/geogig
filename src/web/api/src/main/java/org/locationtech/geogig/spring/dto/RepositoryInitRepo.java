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

import org.locationtech.geogig.web.api.StreamingWriter;
import org.springframework.http.MediaType;

/**
 * Bean for Repository Init response.
 */
@XmlRootElement(name = "repo")
@XmlAccessorType(XmlAccessType.FIELD)
public class RepositoryInitRepo extends LegacyResponse {

    @XmlElement
    private String name = "reponame";

    @XmlElement
    private String link = "file:///tmp/geogig/repos";

    public String getName() {
        return name;
    }

    public RepositoryInitRepo setName(String name) {
        this.name = name;
        return this;
    }

    public String getLink() {
        return link;
    }

    public RepositoryInitRepo setLink(String link) {
        this.link = link;
        return this;
    }

    @Override
    public void encode(StreamingWriter writer, MediaType format, String baseUrl) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
