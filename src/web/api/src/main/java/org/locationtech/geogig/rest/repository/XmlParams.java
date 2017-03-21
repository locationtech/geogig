/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.rest.repository;

import java.io.File;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.beust.jcommander.internal.Lists;

/**
 *
 */
class XmlParams extends AbstractParameterSet {

    private Document options;

    /**
     * @param options
     */
    public XmlParams(Document options) {
        this(options, null);
    }

    public XmlParams(Document options, File uploadedFile) {
        super(uploadedFile);
        this.options = options;
    }

    @Override
    public String[] getValuesArray(String key) {
        NodeList nodes = options.getElementsByTagName(key);
        List<String> values = Lists.newLinkedList();
        if (nodes != null) {
            for (int i = 0; i < nodes.getLength(); i++) {
                Node item = nodes.item(i);
                values.add(item.getTextContent());
            }
        }
        return values.toArray(new String[0]);
    }

    @Override
    public String getFirstValue(String key, String defaultValue) {
        NodeList nodes = options.getElementsByTagName(key);
        String value = null;
        if (nodes != null) {
            if (nodes.getLength() > 0) {
                value = nodes.item(0).getTextContent();
            }
        }
        return value != null ? value.toString() : defaultValue;
    }
}
