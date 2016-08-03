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

import org.junit.Test;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

/**
 *
 */
public class ResolveRepositoryNameTest extends RepositoryTestCase {

    @Override
    protected void setUpInternal() throws Exception {
    }

    @Test
    public void testDefault() throws Exception {
        String repoName = geogig.command(ResolveRepositoryName.class).call();
        assertEquals(repositoryDirectory.getName(), repoName);
    }

    @Test
    public void testConfiguredName() throws Exception {
        final String configRepoName = "myConfiguredRepoName";
        getRepository().configDatabase().put("repo.name", configRepoName);
        String repoName = geogig.command(ResolveRepositoryName.class).call();
        assertEquals(configRepoName, repoName);
    }

}
