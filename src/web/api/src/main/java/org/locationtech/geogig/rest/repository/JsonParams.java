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

import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;

import com.google.common.collect.Lists;


/**
 *
 */
class JsonParams extends AbstractParameterSet {

    private JsonObject options;

    /**
     * @param options
     */
    public JsonParams(JsonObject options) {
        this(options, null);
    }

    public JsonParams(JsonObject options, File uploadedFile) {
        super(uploadedFile);
        this.options = options;
    }

    @Override
    public String[] getValuesArray(String key) {
        List<String> values = Lists.newLinkedList();
        JsonValue valueArray = options.get(key);
        if (valueArray != null) {
            if (valueArray.getValueType().equals(ValueType.ARRAY)) {
                JsonArray array = (JsonArray) valueArray;
                for (JsonValue value : array) {
                    values.add(value.toString());
                }
            } else {
                values.add(jsonValueToString(valueArray));
            }
        }
        return values.toArray(new String[0]);
    }

    @Override
    public String getFirstValue(String key, String defaultValue) {
        String value = jsonValueToString(options.get(key));

        return value != null ? value : defaultValue;
    }

    private static String jsonValueToString(JsonValue value) {
        if (value != null) {
            switch (value.getValueType()) {
            case FALSE:
                return "false";
            case TRUE:
                return "true";
            case STRING:
                return ((JsonString) value).getString();
            case NUMBER:
                return ((JsonNumber) value).toString();
            default:
                return null;
            }
        }
        return null;
    }
}
