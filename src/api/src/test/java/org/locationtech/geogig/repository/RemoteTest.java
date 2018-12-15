/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class RemoteTest {

    @Test
    public void testConstructorAndAccessors() {
        Remote remote = new Remote("name", "fetchurl", "pushurl", "fetch", true, "mappedBranch",
                "username", "password");
        assertEquals("name", remote.getName());
        assertEquals("fetchurl", remote.getFetchURL());
        assertEquals("pushurl", remote.getPushURL());
        assertEquals("fetch", remote.getFetchSpec());
        assertEquals(true, remote.getMapped());
        assertEquals("mappedBranch", remote.getMappedBranch());
        assertEquals("username", remote.getUserName());
        assertEquals("password", remote.getPassword());

        Remote remote2 = new Remote("name", "fetchurl", "pushurl", "fetch", false, null, null,
                null);
        assertEquals("name", remote2.getName());
        assertEquals("fetchurl", remote2.getFetchURL());
        assertEquals("pushurl", remote2.getPushURL());
        assertEquals("fetch", remote2.getFetchSpec());
        assertEquals(false, remote2.getMapped());
        assertEquals("*", remote2.getMappedBranch());
        assertEquals(null, remote2.getUserName());
        assertEquals(null, remote2.getPassword());

        try {
            new Remote("name", "validuri", "fi:\\invalidURI", "fetch", false, null, null, null);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Invalid remote URL.", e.getMessage());
        }

        try {
            new Remote("name", "fi:\\invalidURI", "validuri", "fetch", false, null, null, null);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Invalid remote URL.", e.getMessage());
        }

        try {
            new Remote("name", "validuri", null, "fetch", false, null, null, null);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Invalid remote URL.", e.getMessage());
        }

        try {
            new Remote("name", null, "validuri", "fetch", false, null, null, null);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Invalid remote URL.", e.getMessage());
        }
    }

    @Test
    public void testEquals() {
        Remote remote1 = new Remote("remote1", "fetchurl", "pushurl", "fetch", true, "mappedBranch",
                "username", "password");

        Remote remote1_identical = new Remote("remote1", "fetchurl", "pushurl", "fetch", true,
                "mappedBranch", "username", "password");

        Remote remote2 = new Remote("remote2", "fetchurl", "pushurl", "fetch", false, null, null,
                null);
        Remote remote2_identical = new Remote("remote2", "fetchurl", "pushurl", "fetch", false,
                null, null, null);

        assertTrue(remote1.equals(remote1));
        assertFalse(remote1.equals("remote1"));
        assertFalse(remote1.equals(remote2));
        assertFalse(remote2.equals(remote1));
        assertTrue(remote1.equals(remote1_identical));
        assertTrue(remote2.equals(remote2_identical));

        // Test equals when only one parameter is different
        Remote remote3 = new Remote("remote3", "fetchurl", "pushurl", "fetch", true, "mappedBranch",
                "username", "password");

        Remote remote4 = new Remote("remote1", "fetchurl2", "pushurl", "fetch", true,
                "mappedBranch", "username", "password");

        Remote remote5 = new Remote("remote1", "fetchurl", "pushurl2", "fetch", true,
                "mappedBranch", "username", "password");

        Remote remote6 = new Remote("remote1", "fetchurl", "pushurl", "fetch2", true,
                "mappedBranch", "username", "password");

        Remote remote7 = new Remote("remote1", "fetchurl", "pushurl", "fetch", false,
                "mappedBranch", "username", "password");

        Remote remote8 = new Remote("remote1", "fetchurl", "pushurl", "fetch", true,
                "mappedBranch2", "username", "password");

        Remote remote9 = new Remote("remote1", "fetchurl", "pushurl", "fetch", true, "mappedBranch",
                "username2", "password");

        Remote remote10 = new Remote("remote1", "fetchurl", "pushurl", "fetch", true,
                "mappedBranch", "username", "password2");

        assertFalse(remote1.equals(remote3));
        assertFalse(remote1.equals(remote4));
        assertFalse(remote1.equals(remote5));
        assertFalse(remote1.equals(remote6));
        assertFalse(remote1.equals(remote7));
        assertFalse(remote1.equals(remote8));
        assertFalse(remote1.equals(remote9));
        assertFalse(remote1.equals(remote10));

    }
}
