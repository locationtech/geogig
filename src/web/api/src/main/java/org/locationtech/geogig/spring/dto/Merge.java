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
@XmlAccessorType(XmlAccessType.FIELD)
public class Merge {

    @XmlElement
    private String attribute;

    // These 3 attributes are mutually exclusive, exaclty one must be set.
    @XmlElement(nillable = true)
    private Boolean ours;
    @XmlElement(nillable = true)
    private Boolean theirs;
    @XmlElement(nillable = true)
    private Object value;

    public String getAttribute() {
        return attribute;
    }

    public Merge setAttribute(String attribute) {
        this.attribute = attribute;
        return this;
    }

    public Merge setOurs(Boolean ours) {
        if (Boolean.FALSE.equals(ours)) {
            // False needs to just be null
            this.ours = null;
            return this;
        }
        if (Boolean.TRUE.equals(ours) && (theirs != null || value != null)) {
            throw new IllegalArgumentException("Can not set 'ours' to 'true' with 'theirs' or 'value' set.");
        }
        this.ours = ours;
        return this;
    }

    public Boolean getOurs() {
        return ours;
    }

    public Boolean getTheirs() {
        return theirs;
    }

    public Merge setTheirs(boolean theirs) {
        if (Boolean.FALSE.equals(theirs)) {
            // False needs to just be null
            this.theirs = null;
            return this;
        }
        if (Boolean.TRUE.equals(theirs) && (ours != null || value != null)) {
            throw new IllegalArgumentException("Can not set 'theirs' to 'true' with 'ours' or 'value' set.");
        }
        this.theirs = theirs;
        return this;
    }

    public Object getValue() {
        return value;
    }

    public Merge setValue(Object value) {
        if (value!= null && (theirs != null || ours != null)) {
            throw new IllegalArgumentException("Can not set 'value' to 'true' with 'theirs' or 'ours' set.");
        }
        this.value = value;
        return this;
    }

}
