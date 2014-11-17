/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.cli.test;

import static org.junit.Assert.assertTrue;
import static org.locationtech.geogig.cli.test.functional.general.GlobalState.geogigCLI;
import static org.locationtech.geogig.cli.test.functional.general.GlobalState.insert;
import static org.locationtech.geogig.cli.test.functional.general.GlobalState.platform;
import static org.locationtech.geogig.cli.test.functional.general.GlobalState.setupGeogig;
import static org.locationtech.geogig.cli.test.functional.general.GlobalState.tempFolder;
import static org.locationtech.geogig.cli.test.functional.general.TestFeatures.points1;
import static org.locationtech.geogig.cli.test.functional.general.TestFeatures.points1_modified;
import static org.locationtech.geogig.cli.test.functional.general.TestFeatures.points2;
import static org.locationtech.geogig.cli.test.functional.general.TestFeatures.points3;
import static org.locationtech.geogig.cli.test.functional.general.TestFeatures.setupFeatures;

import java.io.File;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.api.GlobalContextBuilder;
import org.locationtech.geogig.api.TestPlatform;
import org.locationtech.geogig.api.porcelain.AddOp;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.cli.GeogigPy4JEntryPoint;
import org.locationtech.geogig.cli.test.functional.general.CLITestContextBuilder;
import org.locationtech.geogig.cli.test.functional.general.GlobalState;

import py4j.GatewayServer;

public class GeogigPy4JEntryPointTest {

    @Before
    public void setUpDirectories() throws IOException {
        tempFolder = new TemporaryFolder();
        tempFolder.create();
        File homeDirectory = tempFolder.newFolder("fakeHomeDir").getCanonicalFile();
        File currentDirectory = tempFolder.newFolder("testrepo").getCanonicalFile();
        GlobalState.platform = new TestPlatform(currentDirectory, homeDirectory);
        GlobalContextBuilder.builder = new CLITestContextBuilder(platform);
    }

    @Test
    public void testPy4JentryPoint() throws Exception {
        setupGeogig();
        setupFeatures();
        String repoFolder = platform.pwd().getAbsolutePath();
        GeogigPy4JEntryPoint py4j = new GeogigPy4JEntryPoint();
        GatewayServer gatewayServer = new GatewayServer(py4j);
        gatewayServer.start();
        py4j.runCommand(repoFolder, new String[] { "init" });
        py4j.runCommand(repoFolder, "config user.name name".split(" "));
        py4j.runCommand(repoFolder, "config user.email email@email.com".split(" "));
        insert(points1);
        insert(points2);
        insert(points3);
        geogigCLI.getGeogig().command(AddOp.class).call();
        geogigCLI.getGeogig().command(CommitOp.class).setMessage("message").call();
        py4j.runCommand(repoFolder, new String[] { "log" });
        String output = py4j.nextOutputPage();
        assertTrue(output.contains("message"));
        assertTrue(output.contains("name"));
        assertTrue(output.contains("email@email.com"));
        insert(points1_modified);
        py4j.runCommand(repoFolder, new String[] { "add" });
        py4j.runCommand(repoFolder, new String[] { "commit", "-m", "a commit message" });
        py4j.runCommand(repoFolder, new String[] { "log" });
        output = py4j.nextOutputPage();
        System.out.println(output);
        assertTrue(output.contains("a commit message"));

        gatewayServer.shutdown();
    }
}
