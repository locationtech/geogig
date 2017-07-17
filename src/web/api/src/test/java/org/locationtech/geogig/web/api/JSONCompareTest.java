/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.web.api;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.locationtech.geogig.web.api.JsonUtils.jsonEquals;
import static org.locationtech.geogig.web.api.JsonUtils.toJSON;
import static org.locationtech.geogig.web.api.JsonUtils.toJSONArray;

import javax.json.JsonArray;
import javax.json.JsonObject;

import org.junit.Test;

/**
 * Test JSON comparison functions.
 */
public class JSONCompareTest {
    @Test
    public void testCompare() {
        JsonObject expected1 = toJSON(
                "{\"key\":\"value\", \"arrayKey\":[\"arrayValue1\",\"arrayValue2\"], \"objectKey\":{\"subkey1\":1}, \"doubleKey\":2.5, \"longKey\":123456789000, \"booleanKey\":true}");
        JsonObject actual1 = toJSON(
                "{\"key\":\"value\", \"arrayKey\":[\"arrayValue1\",\"arrayValue2\"], \"objectKey\":{\"subkey1\":1, \"extraKey\":\"value\"}, \"extraKey\":\"value\", \"doubleKey\":2.5, \"longKey\":123456789000, \"booleanKey\":true}");
        JsonObject actual2 = toJSON(
                "{\"key\":\"value\", \"arrayKey\":[\"arrayValue1\"], \"objectKey\":{\"subkey1\":1}, \"doubleKey\":2.5, \"longKey\":123456789000, \"booleanKey\":true}");
        JsonObject actual3 = toJSON(
                "{\"key\":\"value\", \"arrayKey\":[\"arrayValue1\",\"arrayValue2\"], \"someOtherKey\":{\"subkey1\":1}, \"doubleKey\":2.5, \"longKey\":123456789000, \"booleanKey\":true}");

        // Identical to expected with only the value of each entry being different
        JsonObject actual4 = toJSON(
                "{\"key\":\"wrongValue\", \"arrayKey\":[\"arrayValue1\",\"arrayValue2\"], \"objectKey\":{\"subkey1\":1}, \"doubleKey\":2.5, \"longKey\":123456789000, \"booleanKey\":true}");
        JsonObject actual5 = toJSON(
                "{\"key\":\"value\", \"arrayKey\":[\"arrayValue1\",\"wrongValue\"], \"objectKey\":{\"subkey1\":1}, \"doubleKey\":2.5, \"longKey\":123456789000, \"booleanKey\":true}");
        JsonObject actual6 = toJSON(
                "{\"key\":\"value\", \"arrayKey\":[\"arrayValue1\",\"arrayValue2\"], \"objectKey\":{\"subkey1\":2}, \"doubleKey\":2.5, \"longKey\":123456789000, \"booleanKey\":true}");
        JsonObject actual7 = toJSON(
                "{\"key\":\"value\", \"arrayKey\":[\"arrayValue1\",\"arrayValue2\"], \"objectKey\":{\"subkey1\":1}, \"doubleKey\":8.5, \"longKey\":123456789000, \"booleanKey\":true}");
        JsonObject actual8 = toJSON(
                "{\"key\":\"value\", \"arrayKey\":[\"arrayValue1\",\"arrayValue2\"], \"objectKey\":{\"subkey1\":1}, \"doubleKey\":2.5, \"longKey\":123456789008, \"booleanKey\":true}");
        JsonObject actual9 = toJSON(
                "{\"key\":\"value\", \"arrayKey\":[\"arrayValue1\",\"arrayValue2\"], \"objectKey\":{\"subkey1\":1}, \"doubleKey\":2.5, \"longKey\":123456789000, \"booleanKey\":false}");

        // Identical to expected with only the type of each entry being different
        JsonObject actual10 = toJSON(
                "{\"key\":1, \"arrayKey\":[\"arrayValue1\",\"arrayValue2\"], \"objectKey\":{\"subkey1\":1}, \"doubleKey\":2.5, \"longKey\":123456789000, \"booleanKey\":true}");
        JsonObject actual11 = toJSON(
                "{\"key\":\"value\", \"arrayKey\":\"arrayValue1\", \"objectKey\":{\"subkey1\":1}, \"doubleKey\":2.5, \"longKey\":123456789000, \"booleanKey\":true}");
        JsonObject actual12 = toJSON(
                "{\"key\":\"value\", \"arrayKey\":[\"arrayValue1\",\"arrayValue2\"], \"objectKey\":\"object\", \"doubleKey\":2.5, \"longKey\":123456789000, \"booleanKey\":true}");
        JsonObject actual13 = toJSON(
                "{\"key\":\"value\", \"arrayKey\":[\"arrayValue1\",\"arrayValue2\"], \"objectKey\":{\"subkey1\":1}, \"doubleKey\":\"someString\", \"longKey\":123456789000, \"booleanKey\":true}");
        JsonObject actual14 = toJSON(
                "{\"key\":\"value\", \"arrayKey\":[\"arrayValue1\",\"arrayValue2\"], \"objectKey\":{\"subkey1\":1}, \"doubleKey\":2.5, \"longKey\":\"someString\", \"booleanKey\":true}");
        JsonObject actual15 = toJSON(
                "{\"key\":\"value\", \"arrayKey\":[\"arrayValue1\",\"arrayValue2\"], \"objectKey\":{\"subkey1\":1}, \"doubleKey\":2.5, \"longKey\":123456789000, \"booleanKey\":\"someString\"}");

        assertTrue(jsonEquals(expected1, expected1, true));
        assertTrue(jsonEquals(expected1, expected1, false));
        assertTrue(jsonEquals(actual1, actual1, true));
        assertTrue(jsonEquals(actual1, actual1, false));
        assertTrue(jsonEquals(expected1, actual1, false));
        assertFalse(jsonEquals(expected1, actual1, true));
        assertFalse(jsonEquals(expected1, actual2, true));
        assertFalse(jsonEquals(expected1, actual2, false));
        assertFalse(jsonEquals(expected1, actual3, true));
        assertFalse(jsonEquals(expected1, actual3, false));
        assertFalse(jsonEquals(expected1, actual4, true));
        assertFalse(jsonEquals(expected1, actual4, false));
        assertFalse(jsonEquals(expected1, actual5, true));
        assertFalse(jsonEquals(expected1, actual5, false));
        assertFalse(jsonEquals(expected1, actual6, true));
        assertFalse(jsonEquals(expected1, actual6, false));
        assertFalse(jsonEquals(expected1, actual7, true));
        assertFalse(jsonEquals(expected1, actual7, false));
        assertFalse(jsonEquals(expected1, actual8, true));
        assertFalse(jsonEquals(expected1, actual8, false));
        assertFalse(jsonEquals(expected1, actual9, true));
        assertFalse(jsonEquals(expected1, actual9, false));
        assertFalse(jsonEquals(expected1, actual10, true));
        assertFalse(jsonEquals(expected1, actual10, false));
        assertFalse(jsonEquals(expected1, actual11, true));
        assertFalse(jsonEquals(expected1, actual11, false));
        assertFalse(jsonEquals(expected1, actual12, true));
        assertFalse(jsonEquals(expected1, actual12, false));
        assertFalse(jsonEquals(expected1, actual13, true));
        assertFalse(jsonEquals(expected1, actual13, false));
        assertFalse(jsonEquals(expected1, actual14, true));
        assertFalse(jsonEquals(expected1, actual14, false));
        assertFalse(jsonEquals(expected1, actual15, true));
        assertFalse(jsonEquals(expected1, actual15, false));

    }

    @Test
    public void testArrayCompare() {
        JsonArray expected = toJSONArray("[\"arrayValue1\", {\"key\":\"value\"}]");

        JsonArray actual1 = toJSONArray("[\"arrayValue1\", {\"key\":\"value\"}, \"extra\"]");

        JsonArray actual2 = toJSONArray("[{\"key\":\"value\"}, \"arrayValue1\"]");

        assertTrue(jsonEquals(expected, expected, true));
        assertTrue(jsonEquals(expected, expected, false));
        assertFalse(jsonEquals(expected, actual1, true));
        assertFalse(jsonEquals(expected, actual1, false));
        assertFalse(jsonEquals(expected, actual2, true));
        assertTrue(jsonEquals(expected, actual2, false));
    }
}
