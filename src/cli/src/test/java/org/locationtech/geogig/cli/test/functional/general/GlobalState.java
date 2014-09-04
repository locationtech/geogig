/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.test.functional.general;

import static org.junit.Assert.assertNotNull;
import static org.locationtech.geogig.cli.test.functional.general.TestFeatures.lines1;
import static org.locationtech.geogig.cli.test.functional.general.TestFeatures.lines2;
import static org.locationtech.geogig.cli.test.functional.general.TestFeatures.lines3;
import static org.locationtech.geogig.cli.test.functional.general.TestFeatures.points1;
import static org.locationtech.geogig.cli.test.functional.general.TestFeatures.points1_FTmodified;
import static org.locationtech.geogig.cli.test.functional.general.TestFeatures.points2;
import static org.locationtech.geogig.cli.test.functional.general.TestFeatures.points3;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

import jline.UnsupportedTerminal;
import jline.console.ConsoleReader;

import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.ContextBuilder;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.GlobalContextBuilder;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.TestPlatform;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.WorkingTree;
import org.opengis.feature.Feature;
import org.opengis.feature.type.Name;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.CharSource;

/**
 */
public class GlobalState {

    public static TemporaryFolder tempFolder;

    /**
     * {@link GeogigCLI#execute(String...)} exit code, upadted every time a {@link #runCommand
     * command is ran}
     */
    public static int exitCode;

    /**
     * A platform to set the current working directory and the user home directory.
     * <p>
     * Note this platform is NOT the same than used by the created GeoGIG instances. They'll instead
     * use a copy of it (as per CLITestInjectorBuilder.build()), in order for the platform not to be
     * shared by multiple geogig instances open (like when working with remotes) and get them
     * confused on what their respective working dir is. So whenever this platform's working dir is
     * changed, setupGeogig() should be called for a new GeogigCLI to be created on the current
     * working dir, if need be.
     */
    public static TestPlatform platform;

    public static File remoteRepo;

    public static ByteArrayInputStream stdIn;

    public static ByteArrayOutputStream stdOut;

    public static GeogigCLI geogigCLI;

    public static ConsoleReader consoleReader;

    public static void setUpDirectories() throws IOException {
        File homeDirectory = tempFolder.newFolder("fakeHomeDir").getCanonicalFile();
        File currentDirectory = tempFolder.newFolder("testrepo").getCanonicalFile();
        if (GlobalState.platform == null) {
            GlobalState.platform = new TestPlatform(currentDirectory, homeDirectory);
        } else {
            GlobalState.platform.setWorkingDir(currentDirectory);
            GlobalState.platform.setUserHome(homeDirectory);
        }
    }

    public static void setupGeogig() throws Exception {
        assertNotNull(platform);

        stdIn = new ByteArrayInputStream(new byte[0]);
        stdOut = new ByteArrayOutputStream();

        if (GlobalState.consoleReader != null) {
            GlobalState.consoleReader.shutdown();
        }
        // GlobalState.consoleReader = new ConsoleReader(stdIn,
        // new TeeOutputStream(stdOut, System.err), new UnsupportedTerminal());
        GlobalState.consoleReader = new ConsoleReader(stdIn, stdOut, new UnsupportedTerminal());

        ContextBuilder injectorBuilder = new CLITestContextBuilder(platform);
        Context injector = injectorBuilder.build();

        if (geogigCLI != null) {
            geogigCLI.close();
        }

        geogigCLI = new GeogigCLI(GlobalState.consoleReader);
        GlobalContextBuilder.builder = injectorBuilder;
        Platform platform = injector.platform();
        geogigCLI.setPlatform(platform);
        geogigCLI.tryConfigureLogging();
    }

    /**
     * Runs the given command with its arguments and returns the command output as a list of
     * strings, one per line.
     */
    public static List<String> runAndParseCommand(String... command) throws Exception {
        return runAndParseCommand(false, command);
    }

    public static List<String> runAndParseCommand(boolean failFast, String... command)
            throws Exception {
        runCommand(failFast, command);
        CharSource reader = CharSource.wrap(stdOut.toString(Charsets.UTF_8.name()));
        ImmutableList<String> lines = reader.readLines();
        return lines;
    }

    /**
     * @param commandAndArgs the command and its arguments. This method is dumb, be careful of not
     *        using arguments that shouldn't be split on a space (like "commit -m 'separate words')
     */
    public static void runCommand(String commandAndArgs) throws Exception {
        runCommand(false, commandAndArgs);
    }

    public static void runCommand(boolean failFast, String commandAndArgs) throws Exception {
        runCommand(failFast, commandAndArgs.split(" "));
    }

    /**
     * runs the command, does not fail fast, check {@link GlobalState#exitCode} for the exit code
     * and {@link GeogigCLI#exception} for any caught exception
     */
    public static void runCommand(String... command) throws Exception {
        runCommand(false, command);
    }

    public static void runCommand(boolean failFast, String... command) throws Exception {
        assertNotNull(geogigCLI);
        stdOut.reset();
        exitCode = geogigCLI.execute(command);
        if (failFast && geogigCLI.exception != null) {
            Exception exception = geogigCLI.exception;
            throw exception;
        }
    }

    public static void insertFeatures() throws Exception {
        insert(points1);
        insert(points2);
        insert(points3);
        insert(lines1);
        insert(lines2);
        insert(lines3);
    }

    public static void insertAndAddFeatures() throws Exception {
        insertAndAdd(points1);
        insertAndAdd(points2);
        insertAndAdd(points3);
        insertAndAdd(lines1);
        insertAndAdd(lines2);
        insertAndAdd(lines3);
    }

    public static void deleteAndReplaceFeatureType() throws Exception {

        GeoGIG geogig = geogigCLI.newGeoGIG();
        try {
            final WorkingTree workTree = geogig.getRepository().workingTree();
            workTree.delete(points1.getType().getName());
            Name name = points1_FTmodified.getType().getName();
            String parentPath = name.getLocalPart();
            workTree.insert(parentPath, points1_FTmodified);
        } finally {
            geogig.close();
        }
    }

    /**
     * Inserts the Feature to the index and stages it to be committed.
     */
    public static ObjectId insertAndAdd(Feature f) throws Exception {
        ObjectId objectId = insert(f);

        runCommand(true, "add");
        return objectId;
    }

    /**
     * Inserts the feature to the index but does not stages it to be committed
     */
    public static ObjectId insert(Feature f) throws Exception {
        return insert(new Feature[] { f }).get(0);
    }

    public static List<ObjectId> insertAndAdd(Feature... features) throws Exception {
        List<ObjectId> ids = insert(features);
        geogigCLI.execute("add");
        return ids;
    }

    public static List<ObjectId> insert(Feature... features) throws Exception {
        geogigCLI.close();
        GeoGIG geogig = geogigCLI.newGeoGIG(Hints.readWrite());
        Preconditions.checkNotNull(geogig);
        List<ObjectId> ids = Lists.newArrayListWithCapacity(features.length);
        try {
            Repository repository = geogig.getRepository();
            final WorkingTree workTree = repository.workingTree();
            for (Feature f : features) {
                Name name = f.getType().getName();
                String parentPath = name.getLocalPart();
                Node ref = workTree.insert(parentPath, f);
                ObjectId objectId = ref.getObjectId();
                ids.add(objectId);
            }
        } finally {
            geogig.close();
        }
        return ids;
    }

    /**
     * Deletes a feature from the index
     * 
     * @param f
     * @return
     * @throws Exception
     */
    public static boolean deleteAndAdd(Feature f) throws Exception {
        boolean existed = delete(f);
        if (existed) {
            runCommand(true, "add");
        }

        return existed;
    }

    public static boolean delete(Feature f) throws Exception {
        GeoGIG geogig = geogigCLI.newGeoGIG();
        try {
            final WorkingTree workTree = geogig.getRepository().workingTree();
            Name name = f.getType().getName();
            String localPart = name.getLocalPart();
            String id = f.getIdentifier().getID();
            boolean existed = workTree.delete(localPart, id);
            return existed;
        } finally {
            geogig.close();
        }
    }
}
