/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.geogig.web.functional;

import static org.geogig.web.functional.FunctionalTestContext.replaceVariables;
import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.junit.Test;

import com.google.common.collect.ImmutableMap;

public class WebAPICucumberHooksTest {

    @Test
    public void testReplaceVariables() {

        Map<String, String> variables = ImmutableMap.of("@token", "abc123");

        String uri = "/resource?token={@token}";
        String expected = "/resource?token=abc123"; 
        String actual = replaceVariables(uri, variables);
        assertEquals(expected, actual);
    }

}
