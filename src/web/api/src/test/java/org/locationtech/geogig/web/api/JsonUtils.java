/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 * Gabriel Roldan (Boundless) - refactored from TestData to its own class
 */
package org.locationtech.geogig.web.api;

import java.io.StringReader;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;

public class JsonUtils {

    public static JsonObject toJSON(String jsonString) {
        JsonReader jsonReader = Json.createReader(new StringReader(jsonString));
        return jsonReader.readObject();
    }

    public static JsonArray toJSONArray(String jsonString) {
        JsonReader jsonReader = Json.createReader(new StringReader(jsonString));
        return jsonReader.readArray();
    }

    public static boolean jsonEquals(JsonArray expected, JsonArray actual, boolean strict) {
        return compare(expected, actual, strict);
    }

    /**
     * Compare two JSON objects. Extra fields are allowed on the actual object if strict is set to
     * {@code false}. Additionally, array ordering does not matter when strict is set to
     * {@code false}.
     *
     * @param expected expected object
     * @param actual actual object
     * @param strict whether or not to perform a strict comparison
     * @return {@code true} if the objects are equal
     */
    public static boolean jsonEquals(JsonObject expected, JsonObject actual, boolean strict) {
        Iterator<String> expectedKeys = expected.keySet().iterator();
        if (strict && expected.size() != actual.size()) {
            return false;
        }
        while (expectedKeys.hasNext()) {
            String key = expectedKeys.next();
            if (!actual.containsKey(key)) {
                return false;
            }
            JsonValue expectedObject = expected.get(key);
            JsonValue actualObject = actual.get(key);
            if (!compare(expectedObject, actualObject, strict)) {
                return false;
            }
        }
        return true;
    }

    public static boolean compare(JsonValue expected, JsonValue actual, boolean strict) {
        switch (expected.getValueType()) {
        case OBJECT:
            if (actual.getValueType() == ValueType.OBJECT) {
                return jsonEquals((JsonObject) expected, (JsonObject) actual, strict);
            }
            break;
        case ARRAY:
            if (actual.getValueType() == ValueType.ARRAY) {
                JsonArray expectedArray = (JsonArray) expected;
                JsonArray actualArray = (JsonArray) actual;
                if (expectedArray.size() != actualArray.size()) {
                    return false;
                }
                if (strict) {
                    for (int i = 0; i < expectedArray.size(); i++) {
                        if (!compare(expectedArray.get(i), actualArray.get(i), strict)) {
                            return false;
                        }
                    }
                } else {
                    List<JsonValue> expectedSet = new LinkedList<JsonValue>();
                    List<JsonValue> actualSet = new LinkedList<JsonValue>();
                    for (int i = 0; i < expectedArray.size(); i++) {
                        expectedSet.add(expectedArray.get(i));
                        actualSet.add(actualArray.get(i));
                    }
                    Iterator<JsonValue> expectedIter = expectedSet.iterator();
                    while (expectedIter.hasNext()) {
                        boolean found = false;
                        JsonValue expectedObject = expectedIter.next();
                        for (JsonValue actualObject : actualSet) {
                            if (compare(expectedObject, actualObject, strict)) {
                                found = true;
                                actualSet.remove(actualObject);
                                break;
                            }
                        }
                        if (!found) {
                            return false;
                        }
                    }
                }
                return true;
            }
            break;
        case STRING:
            if (actual.getValueType() == ValueType.STRING) {
                return expected.equals(actual);
            }
            break;
        case NUMBER:
            if (actual.getValueType() == ValueType.NUMBER) {
                return toDouble(expected).equals(toDouble(actual));
            }
            break;
        default:
            return expected.equals(actual);
        }

        return false;
    }

    public static Double toDouble(Object input) {
        return ((JsonNumber) input).doubleValue();
    }

}
