/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

public class CheckRefFormatTest extends RepositoryTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Override
    protected void setUpInternal() throws Exception {
    }

    @Test
    public void testRefFormats() throws Exception {
        assertFalse(testRef(null));
        assertTrue(testRef("branch1", true));
        assertFalse(testRef("branch1", false));
        assertTrue(testRef("refs/heads/master", true));
        assertFalse(testRef("mast..er"));
        assertFalse(testRef("refs/heads//master"));
        assertFalse(testRef(".master"));
        assertTrue(testRef("mast.er"));
        assertFalse(testRef("master.lock"));
        assertFalse(testRef("master."));
        assertFalse(testRef("ma?ster"));
        assertFalse(testRef("@"));
        assertFalse(testRef("mast\\er"));
        assertFalse(testRef("mas@{ter"));
    }

    @Test
    public void testRefExceptions() throws Exception {
        testRefException(null, "Ref was not provided.");
        testRefException("branch1", false,
                "Ref must contain at least one slash (/) unless explicitly allowed.");
        testRefException("mast..er",
                "Component of ref cannot have two consecutive dots (..) anywhere.");
        testRefException("refs/heads//master", "Component of cannot be empty.");
        testRefException(".master", "Component of ref cannot begin or end with a dot (.).");
        testRefException("master.lock", "Component of ref cannot end with .lock.");
        testRefException("master.", "Component of ref cannot begin or end with a dot (.).");
        testRefException("ma?ster",
                "Component of ref cannot have ASCII control characters or any of the following: space, ~, ^, :, ?, *, [.");
        testRefException("@", "Component of ref cannot be the single character (@).");
        testRefException("mast\\er", "Component of ref cannot contain a backslash (\\) anywhere.");
        testRefException("mas@{ter", "Component of ref cannot contain a sequence (@{) anywhere.");
    }

    private void testRefException(String ref, String expectedMessage) {
        testRefException(ref, true, expectedMessage);
    }

    private void testRefException(String ref, boolean allowOneLevel, String expectedMessage) {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(expectedMessage);
        geogig.command(CheckRefFormat.class).setThrowsException(true)
                .setAllowOneLevel(allowOneLevel).setRef(ref).call();
    }

    private boolean testRef(String ref) {
        return testRef(ref, true);
    }

    private boolean testRef(String ref, boolean allowOneLevel) {
        return geogig.command(CheckRefFormat.class).setAllowOneLevel(allowOneLevel).setRef(ref)
                .call();
    }
}
