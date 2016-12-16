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

import javax.json.JsonArray;
import javax.json.JsonObject;

import org.junit.Test;

/**
 * Test JSON comparison functions.
 */
public class JSONCompareTest {
    @Test
    public void testCompare() {
        JsonObject expected1 = TestData.toJSON(
                "{\"key\":\"value\", \"arrayKey\":[\"arrayValue1\",\"arrayValue2\"], \"objectKey\":{\"subkey1\":1}, \"doubleKey\":2.5, \"longKey\":123456789000, \"booleanKey\":true}");
        JsonObject actual1 = TestData.toJSON(
                "{\"key\":\"value\", \"arrayKey\":[\"arrayValue1\",\"arrayValue2\"], \"objectKey\":{\"subkey1\":1, \"extraKey\":\"value\"}, \"extraKey\":\"value\", \"doubleKey\":2.5, \"longKey\":123456789000, \"booleanKey\":true}");
        JsonObject actual2 = TestData.toJSON(
                "{\"key\":\"value\", \"arrayKey\":[\"arrayValue1\"], \"objectKey\":{\"subkey1\":1}, \"doubleKey\":2.5, \"longKey\":123456789000, \"booleanKey\":true}");
        JsonObject actual3 = TestData.toJSON(
                "{\"key\":\"value\", \"arrayKey\":[\"arrayValue1\",\"arrayValue2\"], \"someOtherKey\":{\"subkey1\":1}, \"doubleKey\":2.5, \"longKey\":123456789000, \"booleanKey\":true}");

        // Identical to expected with only the value of each entry being different
        JsonObject actual4 = TestData.toJSON(
                "{\"key\":\"wrongValue\", \"arrayKey\":[\"arrayValue1\",\"arrayValue2\"], \"objectKey\":{\"subkey1\":1}, \"doubleKey\":2.5, \"longKey\":123456789000, \"booleanKey\":true}");
        JsonObject actual5 = TestData.toJSON(
                "{\"key\":\"value\", \"arrayKey\":[\"arrayValue1\",\"wrongValue\"], \"objectKey\":{\"subkey1\":1}, \"doubleKey\":2.5, \"longKey\":123456789000, \"booleanKey\":true}");
        JsonObject actual6 = TestData.toJSON(
                "{\"key\":\"value\", \"arrayKey\":[\"arrayValue1\",\"arrayValue2\"], \"objectKey\":{\"subkey1\":2}, \"doubleKey\":2.5, \"longKey\":123456789000, \"booleanKey\":true}");
        JsonObject actual7 = TestData.toJSON(
                "{\"key\":\"value\", \"arrayKey\":[\"arrayValue1\",\"arrayValue2\"], \"objectKey\":{\"subkey1\":1}, \"doubleKey\":8.5, \"longKey\":123456789000, \"booleanKey\":true}");
        JsonObject actual8 = TestData.toJSON(
                "{\"key\":\"value\", \"arrayKey\":[\"arrayValue1\",\"arrayValue2\"], \"objectKey\":{\"subkey1\":1}, \"doubleKey\":2.5, \"longKey\":123456789008, \"booleanKey\":true}");
        JsonObject actual9 = TestData.toJSON(
                "{\"key\":\"value\", \"arrayKey\":[\"arrayValue1\",\"arrayValue2\"], \"objectKey\":{\"subkey1\":1}, \"doubleKey\":2.5, \"longKey\":123456789000, \"booleanKey\":false}");

        // Identical to expected with only the type of each entry being different
        JsonObject actual10 = TestData.toJSON(
                "{\"key\":1, \"arrayKey\":[\"arrayValue1\",\"arrayValue2\"], \"objectKey\":{\"subkey1\":1}, \"doubleKey\":2.5, \"longKey\":123456789000, \"booleanKey\":true}");
        JsonObject actual11 = TestData.toJSON(
                "{\"key\":\"value\", \"arrayKey\":\"arrayValue1\", \"objectKey\":{\"subkey1\":1}, \"doubleKey\":2.5, \"longKey\":123456789000, \"booleanKey\":true}");
        JsonObject actual12 = TestData.toJSON(
                "{\"key\":\"value\", \"arrayKey\":[\"arrayValue1\",\"arrayValue2\"], \"objectKey\":\"object\", \"doubleKey\":2.5, \"longKey\":123456789000, \"booleanKey\":true}");
        JsonObject actual13 = TestData.toJSON(
                "{\"key\":\"value\", \"arrayKey\":[\"arrayValue1\",\"arrayValue2\"], \"objectKey\":{\"subkey1\":1}, \"doubleKey\":\"someString\", \"longKey\":123456789000, \"booleanKey\":true}");
        JsonObject actual14 = TestData.toJSON(
                "{\"key\":\"value\", \"arrayKey\":[\"arrayValue1\",\"arrayValue2\"], \"objectKey\":{\"subkey1\":1}, \"doubleKey\":2.5, \"longKey\":\"someString\", \"booleanKey\":true}");
        JsonObject actual15 = TestData.toJSON(
                "{\"key\":\"value\", \"arrayKey\":[\"arrayValue1\",\"arrayValue2\"], \"objectKey\":{\"subkey1\":1}, \"doubleKey\":2.5, \"longKey\":123456789000, \"booleanKey\":\"someString\"}");

        assertTrue(TestData.jsonEquals(expected1, expected1, true));
        assertTrue(TestData.jsonEquals(expected1, expected1, false));
        assertTrue(TestData.jsonEquals(actual1, actual1, true));
        assertTrue(TestData.jsonEquals(actual1, actual1, false));
        assertTrue(TestData.jsonEquals(expected1, actual1, false));
        assertFalse(TestData.jsonEquals(expected1, actual1, true));
        assertFalse(TestData.jsonEquals(expected1, actual2, true));
        assertFalse(TestData.jsonEquals(expected1, actual2, false));
        assertFalse(TestData.jsonEquals(expected1, actual3, true));
        assertFalse(TestData.jsonEquals(expected1, actual3, false));
        assertFalse(TestData.jsonEquals(expected1, actual4, true));
        assertFalse(TestData.jsonEquals(expected1, actual4, false));
        assertFalse(TestData.jsonEquals(expected1, actual5, true));
        assertFalse(TestData.jsonEquals(expected1, actual5, false));
        assertFalse(TestData.jsonEquals(expected1, actual6, true));
        assertFalse(TestData.jsonEquals(expected1, actual6, false));
        assertFalse(TestData.jsonEquals(expected1, actual7, true));
        assertFalse(TestData.jsonEquals(expected1, actual7, false));
        assertFalse(TestData.jsonEquals(expected1, actual8, true));
        assertFalse(TestData.jsonEquals(expected1, actual8, false));
        assertFalse(TestData.jsonEquals(expected1, actual9, true));
        assertFalse(TestData.jsonEquals(expected1, actual9, false));
        assertFalse(TestData.jsonEquals(expected1, actual10, true));
        assertFalse(TestData.jsonEquals(expected1, actual10, false));
        assertFalse(TestData.jsonEquals(expected1, actual11, true));
        assertFalse(TestData.jsonEquals(expected1, actual11, false));
        assertFalse(TestData.jsonEquals(expected1, actual12, true));
        assertFalse(TestData.jsonEquals(expected1, actual12, false));
        assertFalse(TestData.jsonEquals(expected1, actual13, true));
        assertFalse(TestData.jsonEquals(expected1, actual13, false));
        assertFalse(TestData.jsonEquals(expected1, actual14, true));
        assertFalse(TestData.jsonEquals(expected1, actual14, false));
        assertFalse(TestData.jsonEquals(expected1, actual15, true));
        assertFalse(TestData.jsonEquals(expected1, actual15, false));

    }

    @Test
    public void testArrayCompare() {
        JsonArray expected = TestData.toJSONArray("[\"arrayValue1\", {\"key\":\"value\"}]");

        JsonArray actual1 = TestData.toJSONArray("[\"arrayValue1\", {\"key\":\"value\"}, \"extra\"]");

        JsonArray actual2 = TestData.toJSONArray("[{\"key\":\"value\"}, \"arrayValue1\"]");

        assertTrue(TestData.jsonEquals(expected, expected, true));
        assertTrue(TestData.jsonEquals(expected, expected, false));
        assertFalse(TestData.jsonEquals(expected, actual1, true));
        assertFalse(TestData.jsonEquals(expected, actual1, false));
        assertFalse(TestData.jsonEquals(expected, actual2, true));
        assertTrue(TestData.jsonEquals(expected, actual2, false));
    }
}
