/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.web.api.commands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.Map.Entry;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.TestParams;
import org.locationtech.geogig.web.api.WebAPICommand;
import org.restlet.data.Method;
import org.skyscreamer.jsonassert.JSONAssert;

import com.google.common.base.Optional;

public class ConfigTest extends AbstractWebOpTest {

    @Override
    protected String getRoute() {
        return "config";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return Config.class;
    }

    @Override
    protected boolean requiresTransaction() {
        return false;
    }

    @Test
    public void testBuildParameters() {
        ParameterSet options = TestParams.of("name", "config.key", "value", "myValue");

        Config op = (Config) buildCommand(options);
        assertEquals("config.key", op.name);
        assertEquals("myValue", op.value);
    }

    @Test
    public void testConfigList() throws Exception {

        ConfigDatabase configDb = testContext.get().getRepository().configDatabase();

        configDb.put("config.key1", "value1");
        configDb.put("config.key2", "value2");

        Map<String, String> currentConfig = configDb.getAll();

        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (Entry<String, String> entry : currentConfig.entrySet()) {
            sb.append("{'name':'");
            sb.append(entry.getKey());
            sb.append("', 'value':");
            sb.append(entry.getValue());
            sb.append("},");
        }
        sb.append("]");
        String expected = sb.toString();

        ParameterSet options = TestParams.of();
        WebAPICommand cmd = buildCommand(options);
        cmd.run(testContext.get());

        JSONObject response = getJSONResponse().getJSONObject("response");
        JSONArray config = response.getJSONArray("config");
        JSONAssert.assertEquals(expected, config.toString(), false);
    }

    @Test
    public void testConfigGet() throws Exception {
        ConfigDatabase configDb = testContext.get().getRepository().configDatabase();

        configDb.put("config.key1", "value1");

        ParameterSet options = TestParams.of("name", "config.key1");
        WebAPICommand cmd = buildCommand(options);
        cmd.run(testContext.get());

        JSONObject response = getJSONResponse().getJSONObject("response");
        JSONAssert.assertEquals("{'value':'value1'}", response.toString(), false);
    }

    @Test
    public void testConfigGetNonexistent() throws Exception {
        ConfigDatabase configDb = testContext.get().getRepository().configDatabase();

        configDb.put("config.key1", "value1");

        ParameterSet options = TestParams.of("name", "config.nonexistent");
        WebAPICommand cmd = buildCommand(options);
        cmd.run(testContext.get());

        JSONObject response = getJSONResponse().getJSONObject("response");
        JSONAssert.assertEquals("{'success':true}", response.toString(), true);
    }

    @Test
    public void testConfigSet() throws Exception {
        ConfigDatabase configDb = testContext.get().getRepository().configDatabase();

        assertFalse(configDb.get("config.key1").isPresent());

        ParameterSet options = TestParams.of("name", "config.key1", "value", "myTestValue");
        WebAPICommand cmd = buildCommand(options);
        testContext.setRequestMethod(Method.POST);
        cmd.run(testContext.get());

        Optional<String> value = configDb.get("config.key1");

        assertTrue(value.isPresent());
        assertEquals("myTestValue", value.get());

        JSONObject response = getJSONResponse().getJSONObject("response");
        JSONAssert.assertEquals("{'success':true}", response.toString(), true);
    }

    @Test
    public void testConfigSetNoValue() throws Exception {
        ParameterSet options = TestParams.of("name", "config.key1");
        WebAPICommand cmd = buildCommand(options);
        testContext.setRequestMethod(Method.POST);

        ex.expect(IllegalArgumentException.class);
        ex.expectMessage("You must specify the value when setting a config key.");
        cmd.run(testContext.get());
    }

    @Test
    public void testConfigSetNoName() throws Exception {
        ParameterSet options = TestParams.of("value", "myTestValue");
        WebAPICommand cmd = buildCommand(options);
        testContext.setRequestMethod(Method.POST);

        ex.expect(IllegalArgumentException.class);
        ex.expectMessage("You must specify the key when setting a config key.");
        cmd.run(testContext.get());
    }
}
