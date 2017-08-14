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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.IndexInfo.IndexType;
import org.locationtech.geogig.web.api.StreamingWriter;
import org.springframework.http.MediaType;

/**
 * Bean for each Index inside a {@link IndexList} response.
 */
@XmlRootElement(name = "index")
@XmlAccessorType(XmlAccessType.FIELD)
public class IndexInfoBean extends LegacyResponse {

    @XmlElement
    private String treeName;

    @XmlElement
    private String attributeName;

    @XmlElement
    private String indexType;

    @XmlElement
    private @Nullable String bounds;

    @XmlElement
    private @Nullable List<String> extraAttributes;

    private String tagName = "index";

    private boolean arrayItem = false;

    public IndexInfoBean(IndexInfo sourceIndex) {
        this.treeName = sourceIndex.getTreeName();
        this.attributeName = sourceIndex.getAttributeName();
        this.indexType = sourceIndex.getIndexType().toString();
        Map<String, Object> metadata = sourceIndex.getMetadata();
        if (metadata.containsKey(IndexInfo.MD_QUAD_MAX_BOUNDS)) {
            this.bounds = metadata.get(IndexInfo.MD_QUAD_MAX_BOUNDS).toString();
        } else {
            this.bounds = null;
        }
        if (metadata.containsKey(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA)) {
            String[] extraAttributes = (String[]) metadata
                    .get(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA);
            this.extraAttributes = Arrays.asList(extraAttributes);
        } else {
            this.extraAttributes = null;
        }
    }

    public String getTreeName() {
        return treeName;
    }

    public void setTreeName(String treeName) {
        this.treeName = treeName;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public void setAttributeName(String attributeName) {
        this.attributeName = attributeName;
    }

    public String getIndexType() {
        return indexType;
    }

    public void setIndexType(IndexType type) {
        this.indexType = type.toString();
    }

    public @Nullable String getBounds() {
        return bounds;
    }

    public void setBounds(@Nullable String bounds) {
        this.bounds = bounds;
    }

    public @Nullable List<String> getExtraAttributes() {
        return extraAttributes;
    }

    public void setExtraAttributes(@Nullable List<String> extraAttributes) {
        this.extraAttributes = extraAttributes;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public void setArrayItem(boolean isArrayItem) {
        this.arrayItem = isArrayItem;
    }

    @Override
    public void encode(StreamingWriter writer, MediaType format, String baseUrl) {
        if (this.arrayItem) {
            writer.writeStartArrayElement(tagName);
        } else {
            writer.writeStartElement(tagName);
        }
        writer.writeElement("treeName", this.treeName);
        writer.writeElement("attributeName", this.attributeName);
        writer.writeElement("indexType", this.indexType);
        if (this.bounds != null) {
            writer.writeElement("bounds", this.bounds);
        }
        if (this.extraAttributes != null) {
            writer.writeStartArray("extraAttribute");
            for (String extraAttribute : this.extraAttributes) {
                writer.writeArrayElement("extraAttribute", extraAttribute);
            }
            writer.writeEndArray();
        }
        if (this.arrayItem) {
            writer.writeEndArrayElement();
        } else {
            writer.writeEndElement();
        }
    }

}