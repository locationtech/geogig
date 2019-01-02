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

import java.io.OutputStream;
import java.io.Writer;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.storage.RevObjectSerializer;
import org.locationtech.geogig.storage.datastream.DataStreamRevObjectSerializerV1;
import org.springframework.http.MediaType;

/**
 *
 */
@XmlRootElement()
@XmlAccessorType(XmlAccessType.FIELD)
public class Objects extends LegacyRepoResponse {

    private static final RevObjectSerializer SERIALIZER =
            DataStreamRevObjectSerializerV1.INSTANCE;

    @XmlElement
    private RevObject revObject;

    public RevObject getRevObject() {
        return revObject;
    }

    public Objects setRevObject(RevObject revObject) {
        this.revObject = revObject;
        return this;
    }

    @Override
    public MediaType resolveMediaType(MediaType defaultMediaType) {
        // for Object responses, the mediatype should be octet-stream
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    @Override
    public void encode(Writer out) {
        throw new UnsupportedOperationException(
                "Objects does not support java.io.Writer. Use java.io.OutputStream instead");
    }

    @Override
    protected void encode(OutputStream out) {
        try {
            SERIALIZER.write(revObject, out);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
