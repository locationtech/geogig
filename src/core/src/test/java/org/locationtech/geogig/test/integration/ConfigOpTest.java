/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Michael Fawcett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.test.integration;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.porcelain.ConfigOp;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigScope;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConfigException;
import org.locationtech.geogig.storage.ConfigException.StatusCode;

import com.google.common.base.Optional;

// TODO: Not sure if this belongs in porcelain or integration

public class ConfigOpTest extends RepositoryTestCase {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public final void setUpInternal() {
    }

    @After
    public final void tearDownInternal() {
    }

    private void test(ConfigOp.ConfigScope scope) {
        final ConfigOp config = geogig.command(ConfigOp.class);
        config.setScope(scope);

        config.setAction(ConfigAction.CONFIG_SET).setName("section.string").setValue("1").call();

        Map<String, String> result = config.setAction(ConfigAction.CONFIG_GET)
                .setName("section.string").setValue(null).call().or(new HashMap<String, String>());
        assertEquals("1", result.get("section.string"));

        // Test overwriting a value that already exists
        config.setAction(ConfigAction.CONFIG_SET).setName("section.string").setValue("2").call();

        result = config.setAction(ConfigAction.CONFIG_GET).setName("section.string").setValue(null)
                .call().or(new HashMap<String, String>());
        assertEquals("2", result.get("section.string"));

        // Test unsetting a value that exists
        config.setAction(ConfigAction.CONFIG_UNSET).setName("section.string").setValue(null).call();
        result = config.setAction(ConfigAction.CONFIG_GET).setName("section.string").setValue(null)
                .call().or(new HashMap<String, String>());
        assertNull(result.get("section.string"));

        // Test unsetting a value that doesn't exist
        config.setAction(ConfigAction.CONFIG_UNSET).setName("section.string").setValue(null).call();
        result = config.setAction(ConfigAction.CONFIG_GET).setName("section.string").setValue(null)
                .call().or(new HashMap<String, String>());
        assertNull(result.get("section.string"));

        // Test removing a section that exists
        config.setAction(ConfigAction.CONFIG_SET).setName("section.string").setValue("1").call();
        config.setAction(ConfigAction.CONFIG_SET).setName("section.string2").setValue("2").call();
        config.setAction(ConfigAction.CONFIG_REMOVE_SECTION).setName("section").setValue(null)
                .call();

        result = config.setAction(ConfigAction.CONFIG_GET).setName("section.string").setValue(null)
                .call().or(new HashMap<String, String>());
        assertNull(result.get("section.string"));
        result = config.setAction(ConfigAction.CONFIG_GET).setName("section.string2").setValue(null)
                .call().or(new HashMap<String, String>());
        assertNull(result.get("section.string2"));

        // Try listing the config file
        config.setAction(ConfigAction.CONFIG_SET).setName("section.string").setValue("1").call();
        config.setAction(ConfigAction.CONFIG_SET).setName("section.string2").setValue("2").call();

        result = config.setAction(ConfigAction.CONFIG_LIST).call()
                .or(new HashMap<String, String>());
        assertEquals("1", result.get("section.string"));
        assertEquals("2", result.get("section.string2"));
    }

    @Test
    public void testLocal() {
        test(ConfigScope.LOCAL);
    }

    @Test
    public void testGlobal() {
        test(ConfigScope.GLOBAL);
    }

    @Test
    public void testDefault() {
        test(ConfigScope.DEFAULT);
    }

    @Test
    public void testListDefaultWithNoLocalRepository() {
        ConfigDatabase database = mock(ConfigDatabase.class);
        when(database.getAll()).thenThrow(new ConfigException(StatusCode.INVALID_LOCATION));
        ConfigOp config = new ConfigOp(database);

        config.setScope(ConfigScope.DEFAULT).setAction(ConfigAction.CONFIG_LIST).setName(null)
                .setValue(null).call();
    }

    @Test
    public void testGetDefaultWithNoLocalRepository() {
        ConfigDatabase database = mock(ConfigDatabase.class);
        when(database.get(anyString())).thenThrow(new ConfigException(StatusCode.INVALID_LOCATION));
        when(database.getGlobal(anyString())).thenReturn(Optional.of("value"));
        ConfigOp config = new ConfigOp(database);

        config.setScope(ConfigScope.DEFAULT).setAction(ConfigAction.CONFIG_GET)
                .setName("section.key").setValue(null).call();
    }

    @Test
    public void testListLocalWithNoLocalRepository() {
        ConfigDatabase database = mock(ConfigDatabase.class);
        when(database.getAll()).thenThrow(new ConfigException(StatusCode.INVALID_LOCATION));
        ConfigOp config = new ConfigOp(database);

        exception.expect(ConfigException.class);

        config.setScope(ConfigScope.LOCAL).setAction(ConfigAction.CONFIG_LIST).setName(null)
                .setValue(null).call();
    }

    @Test
    public void testGetLocalWithNoLocalRepository() {
        ConfigDatabase database = mock(ConfigDatabase.class);
        when(database.get(anyString())).thenThrow(new ConfigException(StatusCode.INVALID_LOCATION));
        ConfigOp config = new ConfigOp(database);

        exception.expect(ConfigException.class);
        config.setScope(ConfigScope.LOCAL).setAction(ConfigAction.CONFIG_GET).setName("section.key")
                .setValue(null).call();
    }

    @Test
    public void testNullNameValuePairForGet() {
        final ConfigOp config = geogig.command(ConfigOp.class);

        exception.expect(ConfigException.class);
        config.setScope(ConfigScope.GLOBAL).setAction(ConfigAction.CONFIG_GET).setName(null)
                .setValue(null).call();
    }

    @Test
    public void testEmptyNameAndValueForGet() {
        final ConfigOp config = geogig.command(ConfigOp.class);

        exception.expect(ConfigException.class);
        config.setScope(ConfigScope.GLOBAL).setAction(ConfigAction.CONFIG_GET).setName("")
                .setValue("").call();
    }

    @Test
    public void testEmptyNameAndValueForSet() {
        final ConfigOp config = geogig.command(ConfigOp.class);

        exception.expect(ConfigException.class);
        config.setScope(ConfigScope.GLOBAL).setAction(ConfigAction.CONFIG_SET).setName("")
                .setValue("").call();
    }

    @Test
    public void testEmptyNameForUnset() {
        final ConfigOp config = geogig.command(ConfigOp.class);

        exception.expect(ConfigException.class);
        config.setScope(ConfigScope.GLOBAL).setAction(ConfigAction.CONFIG_UNSET).setName("")
                .setValue(null).call();
    }

    @Test
    public void testEmptyNameForRemoveSection() {
        final ConfigOp config = geogig.command(ConfigOp.class);

        exception.expect(ConfigException.class);
        config.setScope(ConfigScope.GLOBAL).setAction(ConfigAction.CONFIG_REMOVE_SECTION)
                .setName("").call();
    }

    @Test
    public void testNoNameForSet() {
        final ConfigOp config = geogig.command(ConfigOp.class);

        exception.expect(ConfigException.class);
        config.setScope(ConfigScope.GLOBAL).setAction(ConfigAction.CONFIG_SET).setName(null)
                .setValue(null).call();
    }

    @Test
    public void testNoNameForUnset() {
        final ConfigOp config = geogig.command(ConfigOp.class);

        exception.expect(ConfigException.class);
        config.setScope(ConfigScope.GLOBAL).setAction(ConfigAction.CONFIG_UNSET).setName(null)
                .setValue(null).call();
    }

    @Test
    public void testNoNameForRemoveSection() {
        final ConfigOp config = geogig.command(ConfigOp.class);

        exception.expect(ConfigException.class);
        config.setScope(ConfigScope.GLOBAL).setAction(ConfigAction.CONFIG_REMOVE_SECTION)
                .setName(null).setValue(null).call();
    }

    @Test
    public void testRemovingMissingSection() {
        final ConfigOp config = geogig.command(ConfigOp.class);

        exception.expect(ConfigException.class);
        config.setScope(ConfigScope.GLOBAL).setAction(ConfigAction.CONFIG_REMOVE_SECTION)
                .setName("unusedsectionname").setValue(null).call();
    }

    @Test
    public void testInvalidSectionKey() {
        final ConfigOp config = geogig.command(ConfigOp.class);
        Optional<Map<String, String>> result = config.setScope(ConfigScope.GLOBAL)
                .setAction(ConfigAction.CONFIG_GET).setName("doesnt.exist").setValue(null).call();
        assertFalse(result.isPresent());
    }

    @Test
    public void testTooManyArguments() {
        final ConfigOp config = geogig.command(ConfigOp.class);

        exception.expect(ConfigException.class);
        config.setScope(ConfigScope.GLOBAL).setAction(ConfigAction.CONFIG_GET).setName("too.many")
                .setValue("arguments").call();
    }

    @Test
    public void testEnum() {
        ConfigAction.values();
        assertEquals(ConfigAction.CONFIG_GET, ConfigAction.valueOf("CONFIG_GET"));
    }

    @Test
    public void testNoAction() {
        final ConfigOp config = geogig.command(ConfigOp.class);

        exception.expect(ConfigException.class);
        config.setAction(ConfigAction.CONFIG_NO_ACTION).setName("section.key").setValue(null)
                .call();
    }

    @Test
    public void testFallback() {
        final ConfigOp config = geogig.command(ConfigOp.class);

        // Set a value in global config, then try to get value from local even though
        // we're not in a valid repository
        config.setAction(ConfigAction.CONFIG_SET).setScope(ConfigScope.GLOBAL)
                .setName("section.key").setValue("1").call();
        Optional<Map<String, String>> value = config.setAction(ConfigAction.CONFIG_GET)
                .setScope(ConfigScope.LOCAL).setName("section.key").setValue(null).call();
        assertTrue(value.isPresent());
        assertEquals("1", value.get().get("section.key"));

        value = Optional.absent();
        value = config.setAction(ConfigAction.CONFIG_GET).setScope(ConfigScope.LOCAL)
                .setName("section.key").setValue("").call();
        assertTrue(value.isPresent());
        assertEquals("1", value.get().get("section.key"));
    }
}
