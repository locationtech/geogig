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

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 */
@XmlRootElement()
@XmlAccessorType(XmlAccessType.FIELD)
public class MergeFeatureRequest {

    @XmlElement
    private String ours;

    @XmlElement
    private String theirs;

    @XmlElement
    private String path;

    @XmlElement
    private List<Merge> merges;

    public String getOurs() {
        return ours;
    }

    public MergeFeatureRequest setOurs(String ours) {
        this.ours = ours;
        return this;
    }

    public String getTheirs() {
        return theirs;
    }

    public MergeFeatureRequest setTheirs(String theirs) {
        this.theirs = theirs;
        return this;
    }

    public String getPath() {
        return path;
    }

    public MergeFeatureRequest setPath(String path) {
        this.path = path;
        return this;
    }

    public List<Merge> getMerges() {
        return merges;
    }

    public MergeFeatureRequest setMerges(List<Merge> merges) {
        this.merges = merges;
        return this;
    }
}
