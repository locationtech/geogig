/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli;

import static org.junit.Assert.assertTrue;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.points1;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.points1_modified;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.points2;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.points3;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.api.porcelain.AddOp;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.cli.GeogigPy4JEntryPoint;
import org.locationtech.geogig.cli.test.functional.CLIContext;
import org.locationtech.geogig.cli.test.functional.CLIContextProvider;
import org.locationtech.geogig.cli.test.functional.TestRepoURIBuilder;

import py4j.GatewayServer;

public class GeogigPy4JEntryPointTest {

    private CLIContextProvider cliContextProvider;

    private CLIContext state;

    private GatewayServer gatewayServer;

    private GeogigPy4JEntryPoint py4j;

    @Before
    public void setUpDirectories() throws Throwable {
        cliContextProvider = CLIContextProvider.get();
        cliContextProvider.setURIBuilder(TestRepoURIBuilder.createDefault());
        cliContextProvider.before();

        state = cliContextProvider.getOrCreateRepositoryContext("testrepo");

        py4j = new GeogigPy4JEntryPoint(true);
        gatewayServer = new GatewayServer(py4j);
        gatewayServer.start();
    }

    @After
    public void after() {
        try {
            cliContextProvider.after();
        } finally {
            if (gatewayServer != null) {
                gatewayServer.shutdown();
            }
        }
    }

    @Test
    public void testPy4JentryPoint() throws Exception {
        String repoFolder = state.platform.pwd().getAbsolutePath();
        py4j.runCommand(repoFolder, new String[] { "init" });
        py4j.runCommand(repoFolder, "config user.name name".split(" "));
        py4j.runCommand(repoFolder, "config user.email email@email.com".split(" "));
        state.insert(points1);
        state.insert(points2);
        state.insert(points3);
        state.geogigCLI.getGeogig().command(AddOp.class).call();
        state.geogigCLI.getGeogig().command(CommitOp.class).setMessage("message").call();
        py4j.runCommand(repoFolder, new String[] { "log" });
        String output = py4j.nextOutputPage();
        assertTrue(output.contains("message"));
        assertTrue(output.contains("name"));
        assertTrue(output.contains("email@email.com"));
        state.insert(points1_modified);
        py4j.runCommand(repoFolder, new String[] { "add" });
        py4j.runCommand(repoFolder, new String[] { "commit", "-m", "a commit message" });
        py4j.runCommand(repoFolder, new String[] { "log" });
        output = py4j.nextOutputPage();
        System.out.println(output);
        assertTrue(output.contains("a commit message"));
    }
}
