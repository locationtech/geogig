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

import java.io.IOException;
import java.io.Writer;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.commons.io.output.WriterOutputStream;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.storage.datastream.DataStreamSerializationFactoryV1;
import org.locationtech.geogig.storage.impl.ObjectSerializingFactory;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.springframework.http.HttpStatus;

/**
 *
 */
@XmlRootElement()
@XmlAccessorType(XmlAccessType.FIELD)
public class Objects extends LegacyRepoResponse {

    private static final ObjectSerializingFactory SERIALIZER =
            DataStreamSerializationFactoryV1.INSTANCE;

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
    public void encode(Writer out) {
        try (WriterOutputStream outStream = new WriterOutputStream(out)) {
            SERIALIZER.write(revObject, outStream);
        } catch (IOException ioe) {
            throw new CommandSpecException("Unexepcted error serializing object",
                    HttpStatus.INTERNAL_SERVER_ERROR, ioe);
        }
    }
}
