/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.test.functional;

import static com.google.common.base.Preconditions.checkNotNull;
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
import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.plumbing.LsTreeOp;
import org.locationtech.geogig.plumbing.LsTreeOp.Strategy;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.FeatureInfo;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.test.TestPlatform;
import org.opengis.feature.Feature;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.Name;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.SetMultimap;
import com.google.common.io.CharSource;

/**
 * The "context" for a specific repository when running the CLI functional tests.
 * <p>
 * The repository context includes the reposiroty URI, a {@link GeogigCLI} for that repositry URI,
 * and a pair of input/output streams to be used as stdin and stdout.
 */
public class CLIContext {

    /**
     * If non null, {@link #configureRepository()} will use it as the repository URI, otherwise
     * it'll use the platform's current directory
     */
    public final URI repositoryURI;

    public final TestPlatform platform;

    /**
     * {@link GeogigCLI#execute(String...)} exit code, updated every time a {@link #runCommand
     * command is ran}
     */
    public int exitCode;

    public GeogigCLI geogigCLI;

    public ByteArrayInputStream stdIn;

    public ByteArrayOutputStream stdOut;

    public Console consoleReader;

    public CLIContext(URI repoURI, TestPlatform platform) {
        checkNotNull(repoURI);
        checkNotNull(platform);
        this.repositoryURI = repoURI;
        this.platform = platform;

        stdIn = new ByteArrayInputStream(new byte[0]);
        stdOut = new ByteArrayOutputStream();
        consoleReader = new Console(stdIn, stdOut).disableAnsi();
        geogigCLI = new GeogigCLI(consoleReader);
        geogigCLI.setPlatform(platform);
    }

    public synchronized void dispose() {
        if (geogigCLI != null) {
            geogigCLI.close();
            geogigCLI = null;
        }
        if (consoleReader != null) {
            consoleReader = null;
        }
    }

    public void configureRepository() throws Exception {
        geogigCLI.close();
        URI uri = repositoryURI;
        Preconditions.checkState(uri != null, "repository URI not set");

        geogigCLI.setPlatform(platform);
        geogigCLI.setRepositoryURI(uri.toString());
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
     * runs the command, does not fail fast, check {@link CLIContext#exitCode} for the exit code and
     * {@link GeogigCLI#exception} for any caught exception
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
            workTree.delete(points1.getType().getName().getLocalPart());
            FeatureType newType = points1_FTmodified.getType();
            Name name = newType.getName();
            String parentPath = name.getLocalPart();
            RevFeatureType rft = RevFeatureType.builder().type(newType).build();
            geogig.getRepository().objectDatabase().put(rft);
            String path = NodeRef.appendChild(parentPath,
                    points1_FTmodified.getIdentifier().getID());
            FeatureInfo fi = FeatureInfo.insert(RevFeature.builder().build(points1_FTmodified),
                    rft.getId(), path);
            workTree.insert(fi);
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
        Map<FeatureType, RevFeatureType> types = new HashMap<>();
        for (Feature f : features) {
            FeatureType type = f.getType();
            RevFeatureType rft = types.get(type);
            if (rft == null) {
                rft = RevFeatureType.builder().type(type).build();
                geogig.getRepository().objectDatabase().put(rft);
                types.put(type, rft);
            }
        }
        try {
            Repository repository = geogig.getRepository();
            final WorkingTree workTree = repository.workingTree();
            for (Feature f : features) {
                FeatureType ft = f.getType();
                RevFeatureType rft = types.get(ft);
                String parentPath = ft.getName().getLocalPart();
                String path = NodeRef.appendChild(parentPath, f.getIdentifier().getID());
                FeatureInfo fi = FeatureInfo.insert(RevFeature.builder().build(f), rft.getId(),
                        path);
                workTree.insert(fi);
                ObjectId objectId = fi.getFeature().getId();
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

    /**
     * List all features in the repository with the given name at the provided ref.
     * 
     * @param headRef the ref from which to list the features
     * @param index {@code true} if the {@code headRef} parameter refers to an index tree
     * @return a multimap that contains all of the feature types and their features
     */
    public SetMultimap<String, String> listRepo(final String headRef, final boolean index) {
        Context context = geogigCLI.getGeogig().getContext();

        ObjectStore source = index ? context.indexDatabase() : context.objectDatabase();

        Iterator<NodeRef> featureRefs = context.command(LsTreeOp.class).setReference(headRef)
                .setStrategy(Strategy.DEPTHFIRST_ONLY_FEATURES).setSource(source).call();

        SetMultimap<String, String> features = HashMultimap.create();
        while (featureRefs.hasNext()) {
            NodeRef ref = featureRefs.next();
            features.put(index ? "index" : ref.getParentPath(), ref.name());
        }
        return features;
    }
}
