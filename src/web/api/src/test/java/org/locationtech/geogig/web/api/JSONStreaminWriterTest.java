/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.web.api;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.locationtech.geogig.web.api.JsonUtils.toJSON;

import java.io.IOException;
import java.io.Writer;

import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;

/**
 *
 */
public class JSONStreaminWriterTest extends AbstractStreamingWriterTest {

    @Override
    protected StreamingWriter createWriter(Writer sink) {
        return new JSONStreamingWriter(sink);
    }

    private JsonValue getJsonValue(String[] paths, String actualBuffer) throws IOException {
        // build a JsonObject from the buffer
        JsonObject obj = toJSON(actualBuffer);
        // drill down the path
        for (int i = 0; i < (paths.length - 1); ++i) {
            obj = obj.getJsonObject(paths[i]);
        }
        // get the last one
        return obj.get(paths[paths.length-1]);
    }

    private boolean jsonValueMatches(JsonValue jsonValue, String expectedValue) {
        switch (jsonValue.getValueType()) {
            case FALSE:
                return !Boolean.valueOf(expectedValue);
            case TRUE:
                return Boolean.valueOf(expectedValue);
            case STRING:
                return expectedValue.equals(((JsonString)jsonValue).getString());
            case NUMBER:
                return expectedValue.equals(((JsonNumber)jsonValue).toString());
            case NULL:
                return "null".equals(expectedValue);
        }
        return false;
    }

    private void assertJsonArrayContainsValue(JsonArray jsonArray, String expectedValue) {
        for (JsonValue jsonValue : jsonArray) {
            if (jsonValueMatches(jsonValue, (expectedValue !=null) ? expectedValue : "null")) {
                // found it
                return;
            }
        }
        fail("Expected value \"" + expectedValue + "\" not found");
    }

    @Override
    protected void verifyInternal(String[] paths, String expectedValue, String actualBuffer) throws IOException {
        // get the JsonValue from the paths
        JsonValue jsonValue = getJsonValue(paths, actualBuffer);
        jsonValueMatches(jsonValue, expectedValue);
    }

    @Override
    protected void verifyArrayInternal(String[] paths, String[] expectedValues, String actualBuffer)
            throws IOException {
        // get the JsonValue from the paths
        JsonValue jsonValue = getJsonValue(paths, actualBuffer);
        assertEquals(JsonValue.ValueType.ARRAY, jsonValue.getValueType());
        JsonArray array = JsonArray.class.cast(jsonValue);
        // verify values
        assertEquals(expectedValues.length, array.size());
        for (String value : expectedValues) {
            assertJsonArrayContainsValue(array, value);
        }
    }

    @Override
    protected void verifyAttributeInternal(String[] paths, String value, String actualBuffer) throws IOException {
        // JSON attributes are just regular elements
        verifyInternal(paths, value, actualBuffer);
    }

    @Override
    protected void verifyArrayElementInternal(String[] paths, String value, String actualBuffer) throws IOException {
        // the last element of the path is an array element
        final String elementName = paths[paths.length - 1];
        String[] newPaths = new String[paths.length - 1];
        System.arraycopy(paths, 0, newPaths, 0, newPaths.length);
        // get the JsonValue
        JsonValue jsonValue = getJsonValue(newPaths, actualBuffer);
        assertEquals(JsonValue.ValueType.ARRAY, jsonValue.getValueType());
        JsonArray array = JsonArray.class.cast(jsonValue);
        // ensure the element exists and is in the array
        for (JsonObject arrayObj : array.getValuesAs(JsonObject.class)) {
            JsonValue tmpVal = arrayObj.get(elementName);
            if (tmpVal != null) {
                assertTrue(jsonValueMatches(tmpVal, value));
                // done
                return;
            }
        }
        fail("JsonArray does not contain an element named \"" + elementName + "\" with a value of \"" + value + "\"");
    }

}
