/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.spring.dto;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.locationtech.geogig.web.api.StreamingWriter;
import org.springframework.http.MediaType;

@XmlRootElement(name = "response")
@XmlAccessorType(XmlAccessType.FIELD)
public class ConsoleRunCommandResponse extends LegacyResponse {
    @XmlElement
    private final String id;

    @XmlElement
    private final String result;

    @XmlElement
    private final ConsoleError error;

    public ConsoleRunCommandResponse(String id, String result, ConsoleError error) {
        this.id = id;
        this.result = result;
        this.error = error;
    }

    @Override
    protected void encodeInternal(StreamingWriter writer, MediaType format, String baseUrl) {
        writer.writeElement("id", id);
        if (result != null) {
            writer.writeElement("result", result);
        }
        if (error != null) {
            error.encodeInternal(writer, format, baseUrl);
        }

    }

    @XmlRootElement(name = "error")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ConsoleError extends LegacyResponse {
        @XmlElement
        private final Integer code;

        @XmlElement
        private final String message;

        public ConsoleError(Integer code, String message) {
            this.code = code;
            this.message = message;
        }

        @Override
        protected void encodeInternal(StreamingWriter writer, MediaType format, String baseUrl) {
            writer.writeStartElement("error");
            writer.writeElement("code", code);
            writer.writeElement("message", message);
            writer.writeEndElement();
        }

    }
}
