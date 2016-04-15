/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.test.functional;

import static org.junit.Assert.assertNotNull;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.lines1;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.lines2;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.lines3;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.points1;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.points1_FTmodified;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.points2;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.points3;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.ContextBuilder;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.GlobalContextBuilder;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.TestPlatform;
import org.locationtech.geogig.cli.Console;
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
 * A global state that allows to set up repositories for cucumber functional tests.
 * <p>
 * Cucumber tests (annotated with <code>@RunWith(Cucumber.class)</code>) can share a single instance
 * of this class for each test scenario acquiring it through {@link FunctionalTestState#get()},
 * which uses a {@link ThreadLocal} variable to hold on the thread singleton. (Note: coulnd't make
 * {@code cucumber-picocontainer} nor {@code cucumnber-guice} as expected, hence this solution).
 * <p>
 * The acquired instance shall be initialized and disposed through {@link #before()} and
 * {@link #after()}, respectively, which are both idempotent.
 */
public class FunctionalTestState {

    public TemporaryFolder tempFolder;

    /**
     * {@link GeogigCLI#execute(String...)} exit code, updated every time a {@link #runCommand
     * command is ran}
     */
    public int exitCode;

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
    public TestPlatform platform;

    public File remoteRepo;

    public ByteArrayInputStream stdIn;

    public ByteArrayOutputStream stdOut;

    public GeogigCLI geogigCLI;

    public Console consoleReader;

    private FunctionalTestState() {
        // force usage through #get()
    }

    private static ThreadLocal<FunctionalTestState> threadLocal = new ThreadLocal<FunctionalTestState>() {
        @Override
        protected FunctionalTestState initialValue() {
            return new FunctionalTestState();
        }
    };

    public static FunctionalTestState get() {
        return threadLocal.get();
    }

    /**
     * If non null, {@link #setupGeogig()} will use it as the repository URI, otherwise it'll use
     * the platform's current directory
     */
    public String repositoryURI;

    public synchronized void before() throws Exception {
        if (tempFolder == null) {
            tempFolder = new TemporaryFolder();
            tempFolder.create();
            TestFeatures.setupFeatures();
        }
    }

    public synchronized void after() {
        try {
            if (geogigCLI != null) {
                geogigCLI.close();
                geogigCLI = null;
            }
            if (consoleReader != null) {
                consoleReader = null;
            }
        } finally {
            if (tempFolder != null) {
                try {
                    tempFolder.delete();
                } finally {
                    tempFolder = null;
                    threadLocal.remove();
                }
            }
        }
    }

    public void setUpDirectories() throws IOException {
        File homeDirectory = new File(tempFolder.getRoot(), "fakeHomeDir");
        File currentDirectory = new File(tempFolder.getRoot(), "testrepo");
        if (!homeDirectory.exists()) {
            homeDirectory.mkdir();
        }
        if (!currentDirectory.exists()) {
            currentDirectory.mkdir();
        }
        if (platform == null) {
            platform = new TestPlatform(currentDirectory, homeDirectory);
        } else {
            platform.setWorkingDir(currentDirectory);
            platform.setUserHome(homeDirectory);
        }
    }

    public void setupGeogig() throws Exception {
        assertNotNull(platform);

        stdIn = new ByteArrayInputStream(new byte[0]);
        stdOut = new ByteArrayOutputStream();

        consoleReader = new Console(stdIn, stdOut).disableAnsi();

        if (geogigCLI != null) {
            geogigCLI.close();
        }

        geogigCLI = new GeogigCLI(consoleReader);

        ContextBuilder injectorBuilder = new CLITestContextBuilder(platform);
        GlobalContextBuilder.builder = injectorBuilder;

        Context context = injectorBuilder.build();
        Platform platform = context.platform();

        geogigCLI.setPlatform(platform);
        geogigCLI.tryConfigureLogging();

        String uri = repositoryURI;
        if (uri != null) {
            geogigCLI.setRepositoryURI(uri);
        }
    }

    /**
     * Runs the given command with its arguments and returns the command output as a list of
     * strings, one per line.
     */
    public List<String> runAndParseCommand(String... command) throws Exception {
        return runAndParseCommand(false, command);
    }

    public List<String> runAndParseCommand(boolean failFast, String... command) throws Exception {
        runCommand(failFast, command);
        CharSource reader = CharSource.wrap(stdOut.toString(Charsets.UTF_8.name()));
        ImmutableList<String> lines = reader.readLines();
        return lines;
    }

    /**
     * @param commandAndArgs the command and its arguments. This method is dumb, be careful of not
     *        using arguments that shouldn't be split on a space (like "commit -m 'separate words')
     */
    public void runCommand(String commandAndArgs) throws Exception {
        runCommand(false, commandAndArgs);
    }

    public void runCommand(boolean failFast, String commandAndArgs) throws Exception {
        runCommand(failFast, commandAndArgs.split(" "));
    }

    /**
     * runs the command, does not fail fast, check {@link FunctionalTestState#exitCode} for the exit
     * code and {@link GeogigCLI#exception} for any caught exception
     */
    public void runCommand(String... command) throws Exception {
        runCommand(false, command);
    }

    public void runCommand(boolean failFast, String... command) throws Exception {
        assertNotNull(geogigCLI);
        stdOut.reset();
        exitCode = geogigCLI.execute(command);
        if (failFast && geogigCLI.exception != null) {
            Exception exception = geogigCLI.exception;
            throw exception;
        }
    }

    public void insertFeatures() throws Exception {
        insert(points1);
        insert(points2);
        insert(points3);
        insert(lines1);
        insert(lines2);
        insert(lines3);
    }

    public void insertAndAddFeatures() throws Exception {
        insertAndAdd(points1);
        insertAndAdd(points2);
        insertAndAdd(points3);
        insertAndAdd(lines1);
        insertAndAdd(lines2);
        insertAndAdd(lines3);
    }

    public void deleteAndReplaceFeatureType() throws Exception {

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
    public ObjectId insertAndAdd(Feature f) throws Exception {
        ObjectId objectId = insert(f);

        runCommand(true, "add");
        return objectId;
    }

    /**
     * Inserts the feature to the index but does not stages it to be committed
     */
    public ObjectId insert(Feature f) throws Exception {
        return insert(new Feature[] { f }).get(0);
    }

    public List<ObjectId> insertAndAdd(Feature... features) throws Exception {
        List<ObjectId> ids = insert(features);
        geogigCLI.execute("add");
        return ids;
    }

    public List<ObjectId> insert(Feature... features) throws Exception {
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
    public boolean deleteAndAdd(Feature f) throws Exception {
        boolean existed = delete(f);
        if (existed) {
            runCommand(true, "add");
        }

        return existed;
    }

    public boolean delete(Feature f) throws Exception {
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
