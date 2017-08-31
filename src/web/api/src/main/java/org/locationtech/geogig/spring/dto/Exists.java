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
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.locationtech.geogig.web.api.CommandSpecException;
import org.springframework.http.HttpStatus;

/**
 *
 */
@XmlRootElement()
@XmlAccessorType(XmlAccessType.FIELD)
public class Exists implements LegacyStatsResponse {

    @XmlElement
    private boolean exists;

    public boolean isExists() {
        return exists;
    }

    public Exists setExists(boolean exists) {
        this.exists = exists;
        return this;
    }

    @Override
    public void encode(OutputStream out) {
        try (OutputStreamWriter osw = new OutputStreamWriter(out)) {
            osw.write((exists) ? "1" : "0");
        } catch (IOException ioe) {
            throw new CommandSpecException("Unexpected error writing to output stream",
                    HttpStatus.INTERNAL_SERVER_ERROR, ioe);
        }
    }
}
