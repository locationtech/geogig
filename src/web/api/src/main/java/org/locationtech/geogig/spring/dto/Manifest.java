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
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.SymRef;

/**
 *
 */
@XmlRootElement()
@XmlAccessorType(XmlAccessType.FIELD)
public class Manifest extends LegacyRepoResponse {

    @XmlElement
    private Ref currentHead;

    @XmlElement
    private List<Ref> branchList;

    @XmlElement
    private List<RevTag> tagList;

    public Ref getCurrentHead() {
        return currentHead;
    }

    public Manifest setCurrentHead(Ref currentHead) {
        this.currentHead = currentHead;
        return this;
    }

    public List<Ref> getBranchList() {
        return branchList;
    }

    public Manifest setBranchList(List<Ref> branchList) {
        this.branchList = branchList;
        return this;
    }

    public List<RevTag> getTagList() {
        return tagList;
    }

    public Manifest setTagList(List<RevTag> tagList) {
        this.tagList = tagList;
        return this;
    }

    @Override
    public void encode(Writer out) {
        PrintWriter w = new PrintWriter(out);
        // Print out HEAD first
        if (!currentHead.getObjectId().equals(ObjectId.NULL)) {
            w.write(currentHead.getName() + " ");
            if (currentHead instanceof SymRef) {
                w.write(((SymRef) currentHead).getTarget());
            }
            w.write(" ");
            w.write(currentHead.getObjectId().toString());
            w.write("\n");
        }

        // Print out the local branches
        for (Ref ref : branchList) {
            if (!ref.getObjectId().equals(ObjectId.NULL)) {
                w.write(ref.getName());
                w.write(" ");
                w.write(ref.getObjectId().toString());
                w.write("\n");
            }
        }
        // Print out the tags
        for (RevTag tag : tagList) {
            w.write("refs/tags/");
            w.write(tag.getName());
            w.write(" ");
            w.write(tag.getId().toString());
            w.write("\n");
        }
        w.flush();
    }
}
