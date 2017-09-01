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

import java.io.PrintWriter;
import java.io.Writer;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.locationtech.geogig.model.ObjectId;

/**
 *
 */
@XmlRootElement()
@XmlAccessorType(XmlAccessType.FIELD)
public class Parents extends LegacyRepoResponse {

    @XmlElement
    private List<ObjectId> parents;

    public List<ObjectId> getParents() {
        return parents;
    }

    public Parents setParents(List<ObjectId> parents) {
        this.parents = parents;
        return this;
    }

    @Override
    public void encode(Writer out) {
        if (parents != null) {
            try (PrintWriter writer = new PrintWriter(out)) {
                for (ObjectId oid : parents) {
                    writer.println(oid.toString());
                }
            }
        }
    }
}
