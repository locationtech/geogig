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

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.locationtech.geogig.web.api.StreamingWriter;
import org.springframework.http.MediaType;

import com.google.common.collect.Lists;

/**
 * Index list response bean.
 */
@XmlRootElement(name = "indexes")
@XmlAccessorType(XmlAccessType.FIELD)
public class IndexList extends LegacyResponse {

    @XmlElement(name = "index")
    private List<IndexInfoBean> indexes;

    public List<IndexInfoBean> getIndexes() {
        return indexes;
    }

    public IndexList setIndexes(List<IndexInfoBean> indexes) {
        this.indexes = indexes;
        return this;
    }

    public IndexList addIndex(IndexInfoBean index) {
        if (indexes == null) {
            indexes = Lists.newArrayList();
        }
        index.setArrayItem(true);
        indexes.add(index);
        return this;
    }

    @Override
    public void encode(StreamingWriter writer, MediaType format, String baseUrl) {
        writer.writeStartArray("index");
        if (indexes != null) {
            for (IndexInfoBean indexInfo : indexes) {
                indexInfo.encode(writer, format, baseUrl);
            }
        }
        writer.writeEndArray();
    }
}
