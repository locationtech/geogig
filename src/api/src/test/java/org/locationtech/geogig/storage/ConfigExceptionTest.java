/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.storage;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ConfigExceptionTest {
    @Test
    public void testConstructors() {
        RuntimeException parentException = new RuntimeException("message");
        ConfigException exception = new ConfigException(ConfigException.StatusCode.CANNOT_WRITE);
        assertEquals(null, exception.getCause());
        assertEquals(ConfigException.StatusCode.CANNOT_WRITE, exception.statusCode);

        exception = new ConfigException(parentException, ConfigException.StatusCode.CANNOT_WRITE);
        assertEquals(parentException, exception.getCause());
        assertEquals(ConfigException.StatusCode.CANNOT_WRITE, exception.statusCode);
    }

    @Test
    public void testStatusCodes() {
        assertEquals(ConfigException.StatusCode.CANNOT_WRITE,
                ConfigException.StatusCode.valueOf("CANNOT_WRITE"));
        assertEquals(ConfigException.StatusCode.INVALID_LOCATION,
                ConfigException.StatusCode.valueOf("INVALID_LOCATION"));
        assertEquals(ConfigException.StatusCode.INVALID_REGEXP,
                ConfigException.StatusCode.valueOf("INVALID_REGEXP"));
        assertEquals(ConfigException.StatusCode.MISSING_SECTION,
                ConfigException.StatusCode.valueOf("MISSING_SECTION"));
        assertEquals(ConfigException.StatusCode.MULTIPLE_OPTIONS_MATCH,
                ConfigException.StatusCode.valueOf("MULTIPLE_OPTIONS_MATCH"));
        assertEquals(ConfigException.StatusCode.OPTION_DOES_NOT_EXIST,
                ConfigException.StatusCode.valueOf("OPTION_DOES_NOT_EXIST"));
        assertEquals(ConfigException.StatusCode.SECTION_OR_KEY_INVALID,
                ConfigException.StatusCode.valueOf("SECTION_OR_KEY_INVALID"));
        assertEquals(ConfigException.StatusCode.SECTION_OR_NAME_NOT_PROVIDED,
                ConfigException.StatusCode.valueOf("SECTION_OR_NAME_NOT_PROVIDED"));
        assertEquals(ConfigException.StatusCode.TOO_MANY_ACTIONS,
                ConfigException.StatusCode.valueOf("TOO_MANY_ACTIONS"));
        assertEquals(ConfigException.StatusCode.TOO_MANY_ARGS,
                ConfigException.StatusCode.valueOf("TOO_MANY_ARGS"));
        assertEquals(ConfigException.StatusCode.USERHOME_NOT_SET,
                ConfigException.StatusCode.valueOf("USERHOME_NOT_SET"));
        assertEquals(11, ConfigException.StatusCode.values().length);
    }
}
