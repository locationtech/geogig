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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 */
@XmlRootElement()
@XmlAccessorType(XmlAccessType.FIELD)
public class Depth extends LegacyRepoResponse {

    @XmlElement
    private Integer depth = null;

    public Integer getDepth() {
        return depth;
    }

    public Depth setDepth(Integer depth) {
        this.depth = depth;
        return this;
    }

    @Override
    protected void encode(Writer out) {
        PrintWriter writer = new PrintWriter(out);
        if (depth != null) {
            writer.write(depth.toString());
        }
        writer.flush();
    }

}
