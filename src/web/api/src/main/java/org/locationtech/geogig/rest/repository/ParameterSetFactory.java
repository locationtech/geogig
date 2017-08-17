/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 * 
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest.repository;

import java.io.File;

import javax.json.JsonObject;

import org.locationtech.geogig.web.api.ParameterSet;
import org.springframework.util.MultiValueMap;
import org.w3c.dom.Document;

import com.google.common.collect.ArrayListMultimap;

/**
 * Factory for building {@link ParameterSet} instances from various Request entities.
 */
public final class ParameterSetFactory {

    private static final ParameterSet EMPTY_SET = new ParameterSet() {
        @Override
        public String getRequiredValue(String key) {
            throw new IllegalArgumentException("Key \" " + key + "\" is not present in the set");
        }

        @Override
        public String getFirstValue(String key) {
            return null;
        }

        @Override
        public String getFirstValue(String key, String defaultValue) {
            return defaultValue;
        }

        @Override
        public String[] getValuesArray(String key) {
            return new String[0];
        }

        @Override
        public File getUploadedFile() {
            return null;
        }
    };

    public static ParameterSet buildParameterSet(JsonObject json) {
        return new JsonParams(json);
    }

    public static ParameterSet buildParameterSet(JsonObject json, File uploadedFile) {
        return new JsonParams(json, uploadedFile);
    }

    public static ParameterSet buildParameterSet(ArrayListMultimap<String, String> options,
            File uploadedFile) {
        return new MultiMapParams(options, uploadedFile);
    }

    public static ParameterSet buildParameterSet(ArrayListMultimap<String, String> options) {
        return new MultiMapParams(options);
    }

    public static ParameterSet buildParameterSet(MultiValueMap<String, String> options) {
        return new MultiValueMapParams(options);
    }

    public static ParameterSet buildParameterSet(MultiValueMap<String, String> options,
            File uploadedFile) {
        return new MultiValueMapParams(options, uploadedFile);
    }

    public static ParameterSet buildParameterSet(Document options) {
        return new XmlParams(options);
    }

    public static ParameterSet buildParameterSet(Document options, File uploadedFile) {
        return new XmlParams(options, uploadedFile);
    }

    public static ParameterSet buildEmptyParameterSet() {
        return EMPTY_SET;
    }
}
