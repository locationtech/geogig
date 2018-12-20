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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.feature;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.idP1;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.lines1;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.lines2;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.lines3;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.points1;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.points1_modified;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.points2;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.points3;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.pointsName;
import static org.locationtech.geogig.cli.test.functional.TestFeatures.pointsType;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.hamcrest.core.StringStartsWith;
import org.junit.Assert;
import org.locationtech.geogig.cli.ArgumentTokenizer;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.plumbing.diff.AttributeDiff;
import org.locationtech.geogig.plumbing.diff.FeatureDiff;
import org.locationtech.geogig.plumbing.diff.GenericAttributeDiffImpl;
import org.locationtech.geogig.plumbing.diff.Patch;
import org.locationtech.geogig.plumbing.diff.PatchSerializer;
import org.locationtech.geogig.porcelain.MergeConflictsException;
import org.locationtech.geogig.porcelain.MergeOp;
import org.locationtech.geogig.porcelain.TagCreateOp;
import org.locationtech.geogig.porcelain.index.IndexUtils;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryResolver;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.locationtech.jts.geom.Envelope;
import org.opengis.feature.Feature;
import org.opengis.feature.type.PropertyDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.google.common.io.Files;

import cucumber.api.DataTable;
import cucumber.api.Scenario;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import cucumber.runtime.java.StepDefAnnotation;

/**
 *
 */
@StepDefAnnotation
public class DefaultStepDefinitions {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultStepDefinitions.class);

    private static final String LINE_SEPARATOR = "\n";

    private CLIContextProvider contextProvider;

    private CLIContext localRepo;

    private Map<String, String> variables = new HashMap<>();

    private String replaceKnownVariables(String s) throws IOException {
        if (s.contains("${currentdir}")) {
            File pwd = localRepo.platform.pwd();
            s = s.replace("${currentdir}", pwd.getCanonicalPath().replace("\\", "/"));
            s = s.replace("\"", "");
        }
        if (s.contains("${repoURI}")) {
            URI uri = localRepo.repositoryURI;
            s = s.replace("${repoURI}", uri.toString());
        }
        if (s.contains("${localrepo}")) {
            URI uri = localRepo.repositoryURI;
            s = s.replace("${localrepo}", uri.toString());
        }
        if (s.contains("${remoterepo}")) {
            CLIContext remote = contextProvider.getRepositoryContext("remoterepo");
            URI remoteURI = remote.repositoryURI;
            s = s.replace("${remoterepo}", remoteURI.toString());
        }
        if (s.contains("${remote repo}")) {
            CLIContext remote = contextProvider.getRepositoryContext("remote repo");
            URI remoteURI = remote.repositoryURI;
            s = s.replace("${remote repo}", remoteURI.toString());
        }
        if (s.contains("${rootRepoURI}")) {
            URI rootRepoURI = contextProvider.getURIBuilder().buildRootURI(localRepo.platform);
            s = s.replace("${rootRepoURI}", rootRepoURI.toString());
        }
        return s;
    }

    /**
     * Initialized method for cucumber tests/scenarios annotated with {@code @FileSystemReposOnly};
     * runs after {@link #before()} and replaces the {@link TestRepoURIBuilder} potentially
     * overwritten by another steps definition class by the default one that sets up {@code file://}
     * URI's.
     */
    @cucumber.api.java.Before(value = "@FileSystemReposOnly", order = 2)
    public void beforeFileOnlyTest() throws Throwable {
        // force using file based repos only
        CLIContextProvider provider = CLIContextProvider.get();
        provider.setURIBuilder(TestRepoURIBuilder.createDefault());
    }

    @cucumber.api.java.Before(order = 1000) // order = 1000 to make sure it runs the latest if the
                                            // @Before annotated methods
    public void before(Scenario scenario) throws Throwable {
        contextProvider = CLIContextProvider.get();
        if (contextProvider.getURIBuilder() == null) {
            contextProvider.setURIBuilder(TestRepoURIBuilder.createDefault());
        }
        LOG.info("'{}': Using URIBuilder {}", scenario.getName(),
                contextProvider.getURIBuilder().getClass().getSimpleName());
        contextProvider.before();
        this.localRepo = contextProvider.getOrCreateRepositoryContext("localrepo");

        RevFeatureType rft = RevFeatureType.builder().type(TestFeatures.pointsType).build();
        setVariable("@PointsTypeID", rft.getId().toString());

        rft = RevFeatureType.builder().type(TestFeatures.linesType).build();
        setVariable("@LinesTypeID", rft.getId().toString());
    }

    @cucumber.api.java.After
    public void after() {
        contextProvider.after();
    }

    private URI resolveURI(String repoParam) {
        URI repoUri = null;
        try {
            repoUri = new URI(repoParam);
        } catch (URISyntaxException e) {
            // See if it's a valid file URI
            try {
                repoUri = new URI("file:/" + repoParam.replace("\\", "/"));
            } catch (URISyntaxException ex) {
                throw new IllegalArgumentException(e);
            }
        }
        return repoUri;
    }

    @Given("^the repository has a truncated graph database$")
    public void the_repository_has_a_truncated_graph_database() throws Throwable {
        Repository repository = localRepo.geogigCLI.getGeogig().getRepository();
        repository.graphDatabase().truncate();
    }

    @Given("^I am in an empty directory$")
    public void I_am_in_an_empty_directory() throws Throwable {
        File wd = localRepo.platform.pwd();
        assertEquals(String.format("directory %s is not empty: %s", wd, Arrays.toString(wd.list())),
                0, wd.list().length);
    }

    @When("^I run the command \"(.*?)\"$")
    public void I_run_the_command_X(String commandSpec) throws Throwable {
        String[] args = ArgumentTokenizer.tokenize(commandSpec);
        for (int i = 0; i < args.length; i++) {
            args[i] = replaceKnownVariables(args[i]);
        }
        localRepo.runCommand(args);
    }

    @Then("^it should exit with non-zero exit code$")
    public void it_should_exit_with_non_zero_exit_code() throws Throwable {
        assertFalse("exited with exit code " + localRepo.exitCode, localRepo.exitCode == 0);
    }

    @Then("^it should exit with zero exit code$")
    public void it_should_exit_with_zero_exit_code() throws Throwable {
        assertEquals(0, localRepo.exitCode);
    }

    @Then("^it should answer \"([^\"]*)\"$")
    public void it_should_answer_exactly(String expected) throws Throwable {
        // File pwd = localRepo.platform.pwd();
        // expected = expected.replace("${currentdir}", pwd.getAbsolutePath()).toLowerCase()
        // .replaceAll("\\\\", "/");
        expected = replaceKnownVariables(expected).toLowerCase();

        String actual = localRepo.stdOut.toString().replaceAll(LINE_SEPARATOR, "")
                .replaceAll("\\\\", "/").trim().toLowerCase();
        assertEquals(expected, actual);
    }

    @Then("^the response should contain \"([^\"]*)\"$")
    public void the_response_should_contain(String expected) throws Throwable {

        expected = replaceKnownVariables(expected);
        String actual = localRepo.stdOut.toString().replaceAll(LINE_SEPARATOR, "")
                .replaceAll("\\\\", "/");
        System.err.println(actual);
        assertTrue("'" + actual + "' does not contain '" + expected + "'",
                actual.contains(expected));
    }

    @Then("^the response should not contain \"([^\"]*)\"$")
    public void the_response_should_not_contain(String expected) throws Throwable {
        expected = replaceKnownVariables(expected);
        String actual = localRepo.stdOut.toString().replaceAll(LINE_SEPARATOR, "")
                .replaceAll("\\\\", "/");
        assertFalse(actual, actual.contains(expected));
    }

    @Then("^the response should contain variable \"([^\"]*)\"$")
    public void checkResponseTextContains(String substring) {
        substring = replaceVariables(substring);
        String actual = localRepo.stdOut.toString().replaceAll(LINE_SEPARATOR, "")
                .replaceAll("\\\\", "/");
        assertTrue("'" + actual + "' does not contain '" + substring + "'",
                actual.contains(substring));
    }

    @Then("^the response should not contain variable \"([^\"]*)\"$")
    public void checkResponseTextDoesNotContain(String substring) {
        substring = replaceVariables(substring);
        String actual = localRepo.stdOut.toString().replaceAll(LINE_SEPARATOR, "")
                .replaceAll("\\\\", "/");
        assertFalse(actual, actual.contains(substring));
    }

    @Then("^the response should contain ([^\"]*) lines$")
    public void the_response_should_contain_x_lines(int lines) throws Throwable {
        String output = localRepo.stdOut.toString();
        String[] lineStrings = output.split(LINE_SEPARATOR);
        assertEquals(output, lines, lineStrings.length);
    }

    @Then("^the response should start with \"([^\"]*)\"$")
    public void the_response_should_start_with(String expected) throws Throwable {
        expected = replaceKnownVariables(expected);
        String actual = localRepo.stdOut.toString().replaceAll(LINE_SEPARATOR, "");
        Assert.assertThat(actual, StringStartsWith.startsWith(expected));
        // assertTrue(actual, actual.startsWith(expected));
    }

    @Then("^the repository shall exist$")
    public void the_repository_shall_exist() throws Throwable {
        List<String> output = localRepo.runAndParseCommand(true, "rev-parse",
                "--resolve-geogig-uri");
        assertEquals(output.toString(), 1, output.size());
        String location = output.get(0);
        assertNotNull(location);
        URI repoURI = resolveURI(location);
        boolean repoExists = RepositoryResolver.lookup(repoURI).repoExists(repoURI);
        assertTrue("Repository not found: " + repoURI, repoExists);
    }

    @Then("^the repository at \"([^\"]*)\" shall exist$")
    public void the_repository_at_shall_exist(String repoUri) throws Throwable {
        repoUri = replaceKnownVariables(repoUri);
        URI uri = resolveURI(repoUri);
        boolean exists = RepositoryResolver.lookup(uri).repoExists(uri);
        assertTrue("Repository does not exist: " + uri, exists);
    }

    @Then("^the repository at \"([^\"]*)\" shall not exist$")
    public void the_repository_at_shall_not_exist(String repoUri) throws Throwable {
        repoUri = replaceKnownVariables(repoUri);
        URI uri = resolveURI(repoUri);
        boolean exists = RepositoryResolver.lookup(uri).repoExists(uri);
        assertFalse("Repository exists: " + uri, exists);
    }

    @Given("^I have a remote ref called \"([^\"]*)\"$")
    public void i_have_a_remote_ref_called(String expected) throws Throwable {
        String ref = "refs/remotes/origin/" + expected;
        localRepo.geogigCLI.getGeogig(Hints.readWrite()).command(UpdateRef.class).setName(ref)
                .setNewValue(ObjectId.NULL).call();
        Optional<Ref> refValue = localRepo.geogigCLI.getGeogig(Hints.readWrite())
                .command(RefParse.class).setName(ref).call();
        assertTrue(refValue.isPresent());
        assertEquals(refValue.get().getObjectId(), ObjectId.NULL);
    }

    @Given("^I have a remote tag called \"([^\"]*)\"$")
    public void i_have_a_remote_tag_called(String expected) throws Throwable {
        localRepo.geogigCLI.getGeogig(Hints.readWrite()) //
                .command(TagCreateOp.class) //
                .setName(expected) //
                .setMessage("Tagged " + expected) //
                .setCommitId(ObjectId.NULL) //
                .call();
    }

    @Given("^I have an unconfigured repository$")
    public void I_have_an_unconfigured_repository() throws Throwable {
        localRepo.configureRepository();
        List<String> output = localRepo.runAndParseCommand(true, "init");
        assertEquals(output.toString(), 1, output.size());
        assertNotNull(output.get(0));
        assertTrue(output.get(0), output.get(0).startsWith("Initialized"));
    }

    @Given("^I have a merge conflict state$")
    public void I_have_a_merge_conflict_state() throws Throwable {
        I_have_conflicting_branches();
        Ref branch = localRepo.geogigCLI.getGeogig(Hints.readOnly()).command(RefParse.class)
                .setName("branch1").call().get();
        try {
            localRepo.geogigCLI.getGeogig(Hints.readWrite()).command(MergeOp.class)
                    .addCommit(branch.getObjectId()).call();
            fail();
        } catch (MergeConflictsException e) {
        }
    }

    @Given("^I have conflicting branches$")
    public void I_have_conflicting_branches() throws Throwable {
        // Create the following revision graph
        // ............o
        // ............|
        // ............o - Points 1 added
        // .........../|\
        // branch2 - o | o - branch1 - Points 1 modifiedB and points 2 added
        // ............|
        // ............o - points 1 modified
        // ............|
        // ............o - master - HEAD - Lines 1 modified
        // branch1 and master are conflicting
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_3", new Integer(2000),
                "POINT(1 1)");
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        localRepo.insertAndAdd(points1);
        localRepo.runCommand(true, "commit -m Commit1");
        localRepo.runCommand(true, "branch branch1");
        localRepo.runCommand(true, "branch branch2");
        localRepo.insertAndAdd(points1Modified);
        localRepo.runCommand(true, "commit -m Commit2");
        localRepo.insertAndAdd(lines1);
        localRepo.runCommand(true, "commit -m Commit3");
        localRepo.runCommand(true, "checkout branch1");
        localRepo.insertAndAdd(points1ModifiedB);
        localRepo.insertAndAdd(points2);
        localRepo.runCommand(true, "commit -m Commit4");
        localRepo.runCommand(true, "checkout branch2");
        localRepo.insertAndAdd(points3);
        localRepo.runCommand(true, "commit -m Commit5");
        localRepo.runCommand(true, "checkout master");
    }

    @Given("^I set up a hook$")
    public void I_set_up_a_hook() throws Throwable {
        URI uri = localRepo.repositoryURI;
        if (!"file".equals(uri.getScheme())) {
            throw new RuntimeException(
                    "Script hooks are only supported for file repositories: " + uri);
        }
        File repoDir = new File(uri);
        File hooksDir = new File(repoDir, ".geogig/hooks");
        File hook = new File(hooksDir, "pre_commit.js");
        String script = "exception = Packages.org.locationtech.geogig.hooks.CannotRunGeogigOperationException;\n"
                + "msg = params.get(\"message\");\n" + "if (msg.length() < 5){\n"
                + "\tthrow new exception(\"Commit messages must have at least 5 letters\");\n"
                + "}\n" + "params.put(\"message\", msg.toLowerCase());";
        Files.asCharSink(hook, Charsets.UTF_8).write(script);
    }

    @Given("^I clone a remote repository$")
    public void i_clone_a_remote_repository() throws Throwable {
        there_is_a_remote_repository();
        CLIContext remoteRepo = contextProvider.getRepositoryContext("remoterepo");
        URI remote = remoteRepo.repositoryURI;
        URI local = localRepo.repositoryURI;
        localRepo.runCommand(true, "clone", remote.toString(), local.toString());
        localRepo.runCommand(true, "config", "--global", "user.name", "John Doe");
        localRepo.runCommand(true, "config", "--global", "user.email", "JohnDoe@example.com");
    }

    @Given("^there is a remote repository$")
    public void there_is_a_remote_repository() throws Throwable {
        createRemote("remoterepo");
    }

    @Given("^there is a remote repository with blank spaces$")
    public void there_is_a_remote_repository_with_blank_spaces() throws Throwable {
        createRemote("remote repo");
    }

    private CLIContext createRemote(String name) throws Throwable {

        CLIContext remoteRepo = contextProvider.getOrCreateRepositoryContext(name);
        remoteRepo.configureRepository();

        List<String> output = remoteRepo.runAndParseCommand(true, "init");

        assertEquals(output.toString(), 1, output.size());
        assertNotNull(output.get(0));
        assertTrue(output.get(0), output.get(0).startsWith("Initialized"));

        remoteRepo.runCommand(true, "config", "--global", "user.name", "John Doe");
        remoteRepo.runCommand(true, "config", "--global", "user.email", "JohnDoe@example.com");
        remoteRepo.insertAndAdd(points1);
        remoteRepo.runCommand(true, "commit -m Commit1");
        remoteRepo.runCommand(true, "branch -c branch1");
        remoteRepo.insertAndAdd(points2);
        remoteRepo.runCommand(true, "commit -m Commit2");
        remoteRepo.insertAndAdd(points3);
        remoteRepo.runCommand(true, "commit -m Commit3");
        remoteRepo.runCommand(true, "checkout master");
        remoteRepo.insertAndAdd(lines1);
        remoteRepo.runCommand(true, "commit -m Commit4");
        remoteRepo.insertAndAdd(lines2);
        remoteRepo.runCommand(true, "commit -m Commit5");

        return remoteRepo;
    }

    @Given("^there is a remote repository with a tag named \"([^\"]*)\"$")
    public void there_is_a_remote_repository_with_a_tag_named(String tagName) throws Throwable {

        CLIContext remoteRepo = contextProvider.getOrCreateRepositoryContext("remoterepo");
        remoteRepo.configureRepository();

        List<String> output = remoteRepo.runAndParseCommand(true, "init");
        assertEquals(output.toString(), 1, output.size());
        assertNotNull(output.get(0));
        assertTrue(output.get(0), output.get(0).startsWith("Initialized"));

        remoteRepo.runCommand(true, "config", "--global", "user.name", "John Doe");
        remoteRepo.runCommand(true, "config", "--global", "user.email", "JohnDoe@example.com");
        remoteRepo.insertAndAdd(points1);
        remoteRepo.runCommand(true, "commit -m Commit1");
        remoteRepo.runCommand(true, "branch -c branch1");
        remoteRepo.insertAndAdd(points2);
        remoteRepo.runCommand(true, "commit -m Commit2");
        remoteRepo.insertAndAdd(points3);
        remoteRepo.runCommand(true, "commit -m Commit3");
        remoteRepo.runCommand(true, "checkout master");
        remoteRepo.insertAndAdd(lines1);
        remoteRepo.runCommand(true, "commit -m Commit4");
        remoteRepo.insertAndAdd(lines2);
        remoteRepo.runCommand(true, "commit -m Commit5");
        remoteRepo.runCommand(true, "tag " + tagName + " -m Created_" + tagName + "");

        String actual = remoteRepo.stdOut.toString().replaceAll(LINE_SEPARATOR, "")
                .replaceAll("\\\\", "/").trim().toLowerCase();
        assertTrue(actual, actual.startsWith("created tag " + tagName));

    }

    @Given("^the remote repository has changes")
    public void the_remote_repository_has_changes() throws Throwable {
        CLIContext remoteRepo = contextProvider.getOrCreateRepositoryContext("remoterepo");
        remoteRepo.insertAndAdd(lines3);
        remoteRepo.runCommand(true, "commit -m Commit10");
    }

    @Given("^I have a repository$")
    public void I_have_a_repository() throws Throwable {
        I_have_an_unconfigured_repository();
        localRepo.runCommand(true, "config", "--global", "user.name", "John Doe");
        localRepo.runCommand(true, "config", "--global", "user.email", "JohnDoe@example.com");
    }

    @Given("^I have a repository with a remote$")
    public void I_have_a_repository_with_a_remote() throws Throwable {
        I_have_an_unconfigured_repository();
        CLIContext remoteRepo = createRemote("remoterepo");
        final URI remoteURI = remoteRepo.repositoryURI;

        localRepo.runCommand(true, "config", "--global", "user.name", "John Doe");
        localRepo.runCommand(true, "config", "--global", "user.email", "JohnDoe@example.com");

        String uriArg = remoteURI.toString();
        localRepo.runCommand(true, "remote", "add", "origin", uriArg);
    }

    @Given("^I have staged \"([^\"]*)\"$")
    public void I_have_staged(String feature) throws Throwable {
        if (feature.equals("points1")) {
            localRepo.insertAndAdd(points1);
        } else if (feature.equals("points2")) {
            localRepo.insertAndAdd(points2);
        } else if (feature.equals("points3")) {
            localRepo.insertAndAdd(points3);
        } else if (feature.equals("points1_modified")) {
            localRepo.insertAndAdd(points1_modified);
        } else if (feature.equals("lines1")) {
            localRepo.insertAndAdd(lines1);
        } else if (feature.equals("lines2")) {
            localRepo.insertAndAdd(lines2);
        } else if (feature.equals("lines3")) {
            localRepo.insertAndAdd(lines3);
        } else {
            throw new Exception("Unknown Feature");
        }
    }

    @Given("^I have a local committer")
    public void I_have_a_local_committer() throws Throwable {
        localRepo.runCommand(true, "config", "--local", "user.name", "Jane Doe");
        localRepo.runCommand(true, "config", "--local", "user.email", "JaneDoe@example.com");
    }

    @Given("^I have 6 unstaged features$")
    public void I_have_6_unstaged_features() throws Throwable {
        localRepo.insertFeatures();
    }

    @Given("^I have unstaged \"([^\"]*)\"$")
    public void I_have_unstaged(String feature) throws Throwable {
        if (feature.equals("points1")) {
            localRepo.insert(points1);
        } else if (feature.equals("points2")) {
            localRepo.insert(points2);
        } else if (feature.equals("points3")) {
            localRepo.insert(points3);
        } else if (feature.equals("points1_modified")) {
            localRepo.insert(points1_modified);
        } else if (feature.equals("lines1")) {
            localRepo.insert(lines1);
        } else if (feature.equals("lines2")) {
            localRepo.insert(lines2);
        } else if (feature.equals("lines3")) {
            localRepo.insert(lines3);
        } else {
            throw new Exception("Unknown Feature");
        }
    }

    @Given("^I have unstaged an empty feature type$")
    public void I_have_unstaged_an_empty_feature_type() throws Throwable {
        localRepo.insert(points1);
        GeoGIG geogig = localRepo.geogigCLI.newGeoGIG();
        final WorkingTree workTree = geogig.getRepository().workingTree();
        workTree.delete(pointsName, idP1);
        geogig.close();
    }

    @Given("^I stage 6 features$")
    public void I_stage_6_features() throws Throwable {
        localRepo.insertAndAddFeatures();
    }

    @Given("^I have several commits$")
    public void I_have_several_commits() throws Throwable {
        localRepo.insertAndAdd(points1, points2);
        localRepo.runCommand(true, "commit -m Commit1");
        localRepo.insertAndAdd(points3, lines1);
        localRepo.runCommand(true, "commit -m Commit2");
        localRepo.insertAndAdd(lines2, lines3);
        localRepo.runCommand(true, "commit -m Commit3");
        localRepo.insertAndAdd(points1_modified);
        localRepo.runCommand(true, "commit -m Commit4");
    }

    @Given("^I have several branches")
    public void I_have_several_branches() throws Throwable {
        localRepo.insertAndAdd(points1);
        localRepo.runCommand(true, "commit -m Commit1");
        localRepo.runCommand(true, "branch -c branch1");
        localRepo.insertAndAdd(points2);
        localRepo.runCommand(true, "commit -m Commit2");
        localRepo.insertAndAdd(points3);
        localRepo.runCommand(true, "commit -m Commit3");
        localRepo.runCommand(true, "branch -c branch2");
        localRepo.insertAndAdd(lines1);
        localRepo.runCommand(true, "commit -m Commit4");
        localRepo.runCommand(true, "checkout master");
        localRepo.insertAndAdd(lines2);
        localRepo.runCommand(true, "commit -m Commit5");
    }

    @Given("I modify and add a feature")
    public void I_modify_and_add_a_feature() throws Throwable {
        localRepo.insertAndAdd(points1_modified);
    }

    @Given("I modify a feature")
    public void I_modify_a_feature() throws Throwable {
        localRepo.insert(points1_modified);
    }

    @Given("I remove and add a feature")
    public void I_remove_and_add_a_feature() throws Throwable {
        localRepo.deleteAndAdd(points1);
    }

    @Given("I remove a feature")
    public void I_remove_a_feature() throws Throwable {
        localRepo.delete(points1);
    }

    @Given("^I a featuretype is modified$")
    public void I_modify_a_feature_type() throws Throwable {
        localRepo.deleteAndReplaceFeatureType();
    }

    @Then("^if I change to the respository subdirectory \"([^\"]*)\"$")
    public void if_I_change_to_the_respository_subdirectory(String subdirSpec) throws Throwable {
        String[] subdirs = subdirSpec.split("/");
        File dir = localRepo.platform.pwd();
        for (String subdir : subdirs) {
            dir = new File(dir, subdir);
        }
        assertTrue(dir.exists());
        localRepo.platform.setWorkingDir(dir);
        localRepo.geogigCLI.close();
        localRepo.geogigCLI.setRepositoryURI(dir.toURI().toString());
    }

    @Given("^I have a patch file$")
    public void I_have_a_patch_file() throws Throwable {
        Patch patch = new Patch();
        String path = NodeRef.appendChild(pointsName, points1.getIdentifier().getID());
        Map<PropertyDescriptor, AttributeDiff> map = Maps.newHashMap();
        Object oldValue = points1.getProperty("sp").getValue();
        GenericAttributeDiffImpl diff = new GenericAttributeDiffImpl(oldValue, "new");
        map.put(pointsType.getDescriptor("sp"), diff);
        FeatureDiff feaureDiff = new FeatureDiff(path, map,
                RevFeatureType.builder().type(pointsType).build(),
                RevFeatureType.builder().type(pointsType).build());
        patch.addModifiedFeature(feaureDiff);
        File file = new File(localRepo.platform.pwd(), "test.patch");
        BufferedWriter writer = Files.newWriter(file, Charsets.UTF_8);
        PatchSerializer.write(writer, patch);
        writer.flush();
        writer.close();
    }

    @Given("^I have an insert file$")
    public void I_have_an_insert_file() throws Throwable {
        File file = new File(localRepo.platform.pwd(), "insert");
        BufferedWriter writer = Files.newWriter(file, Charsets.UTF_8);
        writer.write("Points/Points.1\n");
        writer.write("sp\tNew_String\n");
        writer.write("ip\t1001\n");
        writer.write("pp\tPOINT(2 2)\n");
        writer.flush();
        writer.close();
    }

    @Given("^I am inside a repository subdirectory \"([^\"]*)\"$")
    public void I_am_inside_a_repository_subdirectory(String subdirSpec) throws Throwable {
        String[] subdirs = subdirSpec.split("/");
        File dir = localRepo.platform.pwd();
        for (String subdir : subdirs) {
            dir = new File(dir, subdir);
        }
        assertTrue(dir.mkdirs());
        localRepo.platform.setWorkingDir(dir);
    }

    @Given("^I create a detached branch")
    public void I_create_a_detached_branch() throws Throwable {
        localRepo.runCommand(true, "log --oneline");
        String actual = localRepo.stdOut.toString().replaceAll(LINE_SEPARATOR, "")
                .replaceAll("\\\\", "/");
        String[] commitId = actual.split(" ");
        localRepo.runCommand(true, "checkout " + commitId[0]);
    }

    @Then("^the response should contain the index ID for tree \"([^\"]*)\"$")
    public void the_response_contains_indexID(String tree) throws Throwable {
        GeoGIG gig = localRepo.geogigCLI.getGeogig();

        ObjectId canonicalTreeId = gig.command(ResolveTreeish.class).setTreeish("HEAD:" + tree)
                .call().get();
        Optional<IndexInfo> indexInfo = gig.getRepository().indexDatabase().getIndexInfo(tree,
                "pp");

        Optional<ObjectId> indexedTree = gig.getRepository().indexDatabase()
                .resolveIndexedTree(indexInfo.get(), canonicalTreeId);

        if (!indexedTree.isPresent()) {
            fail();
        }
        String indexId = indexedTree.get().toString();
        String actual = localRepo.stdOut.toString().replaceAll(LINE_SEPARATOR, "")
                .replaceAll("\\\\", "/");
        assertTrue("'" + actual + "' does not contain ID '" + indexId.substring(0, 8),
                actual.contains(indexId.toString().substring(0, 8)));
    }

    @Then("^the response should contain index info ID for tree \"([^\"]*)\"$")
    public void the_response_contains_indexInfoID(String tree) throws Throwable {
        Repository repo = localRepo.geogigCLI.getGeogig().getRepository();

        Optional<IndexInfo> indexInfo = repo.indexDatabase().getIndexInfo(tree, "pp");
        ObjectId oid = null;
        if (indexInfo.isPresent()) {
            oid = indexInfo.get().getId();
        }

        String actual = localRepo.stdOut.toString().replaceAll(LINE_SEPARATOR, "")
                .replaceAll("\\\\", "/");
        if (oid == null) {
            fail();
        } else {
            assertTrue("'" + actual + "' does not contain ID for '" + oid.toString(),
                    actual.contains(oid.toString()));
        }
    }

    /**
     * Checks that the local repository, at it's commit {@code headRef}, has the expected features
     * as given by the {@code expectedFeatures} {@link DataTable}.
     * <p>
     * The {@code DataTable} top cells represent feature tree paths, and their cells beneath each
     * feature tree path, the feature ids expected for each layer.
     * <p>
     * A {@code question mark} indicates a wild card feature where the feature id may not be known.
     * <p>
     * Example:
     * 
     * <pre>
     * <code>
     *     |  Points   |  Lines   |  Polygons   | 
     *     |  Points.1 |  Lines.1 |  Polygons.1 | 
     *     |  Points.2 |  Lines.2 |  Polygons.2 | 
     *     |  ?        |          |             |
     *</code>
     * </pre>
     * 
     * @param headRef
     * @param expectedFeatures
     * @param index
     * @throws Throwable
     */
    private void verifyRepositoryContents(String headRef, DataTable expectedFeatures,
            boolean index) {
        SetMultimap<String, String> expected = HashMultimap.create();
        {
            List<Map<String, String>> asMaps = expectedFeatures.asMaps(String.class, String.class);
            for (Map<String, String> featureMap : asMaps) {
                for (Entry<String, String> entry : featureMap.entrySet()) {
                    if (entry.getValue().length() > 0) {
                        expected.put(entry.getKey(), replaceVariables(entry.getValue()));
                    }
                }
            }
        }

        SetMultimap<String, String> actual = localRepo.listRepo(headRef, index);

        Map<String, Collection<String>> actualMap = actual.asMap();
        Map<String, Collection<String>> expectedMap = expected.asMap();

        for (String featureType : actualMap.keySet()) {
            assertTrue(expectedMap.containsKey(featureType));
            Collection<String> actualFeatureCollection = actualMap.get(featureType);
            Collection<String> expectedFeatureCollection = expectedMap.get(featureType);
            for (String actualFeature : actualFeatureCollection) {
                if (expectedFeatureCollection.contains(actualFeature)) {
                    expectedFeatureCollection.remove(actualFeature);
                } else if (expectedFeatureCollection.contains("?")) {
                    expectedFeatureCollection.remove("?");
                } else {
                    fail();
                }
            }
            assertEquals(0, expectedFeatureCollection.size());
            expectedMap.remove(featureType);
        }
        assertEquals(0, expectedMap.size());
    }

    private Optional<ObjectId> resolveIndexTreeId(String headRef, @Nullable String attributeName) {
        Repository repo = localRepo.geogigCLI.getGeogig().getRepository();
        final NodeRef typeTreeRef = IndexUtils.resolveTypeTreeRef(repo.context(), headRef);
        if (typeTreeRef == null) {
            return Optional.absent();
        }
        String treeName = typeTreeRef.path();
        List<IndexInfo> indexInfos = IndexUtils.resolveIndexInfo(repo.indexDatabase(), treeName,
                attributeName);
        if (indexInfos.isEmpty()) {
            return Optional.absent();
        }
        Preconditions.checkState(indexInfos.size() == 1,
                "Multiple indexes found for given tree ref.");
        Optional<ObjectId> indexTreeId = repo.indexDatabase().resolveIndexedTree(indexInfos.get(0),
                typeTreeRef.getObjectId());
        return indexTreeId;
    }

    @Then("^the repository's \"([^\"]*)\" index on the \"([^\"]*)\" attribute should have the following features:$")
    public void verifyIndexContents(String headRef, String attributeName,
            DataTable expectedFeatures) throws Throwable {
        Optional<ObjectId> indexTreeId = resolveIndexTreeId(headRef, attributeName);
        assertTrue(indexTreeId.isPresent());
        verifyRepositoryContents(indexTreeId.get().toString(), expectedFeatures, true);
    }

    @Then("^the repository's \"([^\"]*)\" index should have the following features:$")
    public void verifyIndexContents(String headRef, DataTable expectedFeatures) throws Throwable {
        verifyIndexContents(headRef, null, expectedFeatures);
    }

    @Then("^the repository's \"([^\"]*)\" should not have an index$")
    public void noIndexAtCommit(String headRef) throws Throwable {
        Optional<ObjectId> indexTreeId = resolveIndexTreeId(headRef, null);
        assertFalse(indexTreeId.isPresent());
    }

    @Then("^the repository's \"([^\"]*)\" index bounds should be \"([^\"]*)\"$")
    public void verifyIndexBounds(String headRef, String bbox) throws Throwable {
        Repository repo = localRepo.geogigCLI.getGeogig().getRepository();
        final NodeRef typeTreeRef = IndexUtils.resolveTypeTreeRef(repo.context(), headRef);
        assertNotNull(typeTreeRef);
        String treeName = typeTreeRef.path();
        List<IndexInfo> indexInfos = IndexUtils.resolveIndexInfo(repo.indexDatabase(), treeName,
                null);
        assertEquals(1, indexInfos.size());
        Map<String, Object> metadata = indexInfos.get(0).getMetadata();
        assertTrue(metadata.containsKey(IndexInfo.MD_QUAD_MAX_BOUNDS));
        Envelope indexBounds = (Envelope) metadata.get(IndexInfo.MD_QUAD_MAX_BOUNDS);
        Envelope expected = SpatialOps.parseNonReferencedBBOX(bbox);
        final double EPSILON = 0.00001;
        assertEquals(expected.getMinX(), indexBounds.getMinX(), EPSILON);
        assertEquals(expected.getMaxX(), indexBounds.getMaxX(), EPSILON);
        assertEquals(expected.getMinY(), indexBounds.getMinY(), EPSILON);
        assertEquals(expected.getMaxY(), indexBounds.getMaxY(), EPSILON);
    }

    @SuppressWarnings("unchecked")
    @Then("^the repository's \"([^\"]*)\" index should track the extra attribute \"([^\"]*)\"$")
    public void verifyIndexExtraAttributes(String headRef, String attributeName) throws Throwable {
        Repository repo = localRepo.geogigCLI.getGeogig().getRepository();
        Optional<ObjectId> indexTreeId = resolveIndexTreeId(headRef, null);
        assertTrue(indexTreeId.isPresent());
        RevTree indexTree = repo.indexDatabase().getTree(indexTreeId.get());
        Set<org.locationtech.geogig.model.Node> nodes = RevObjectTestSupport.getTreeNodes(indexTree,
                repo.indexDatabase());
        for (org.locationtech.geogig.model.Node n : nodes) {
            Map<String, Object> extraData = n.getExtraData();
            assertTrue(extraData.containsKey(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA));
            Map<String, Object> attributeData = (Map<String, Object>) extraData
                    .get(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA);
            assertTrue(attributeData.containsKey(attributeName));
        }
    }

    @SuppressWarnings("unchecked")
    @Then("^the repository's \"([^\"]*)\" index should not track the extra attribute \"([^\"]*)\"$")
    public void verifyIndexNotExtraAttributes(String headRef, String attributeName)
            throws Throwable {
        Repository repo = localRepo.geogigCLI.getGeogig().getRepository();
        Optional<ObjectId> indexTreeId = resolveIndexTreeId(headRef, null);
        assertTrue(indexTreeId.isPresent());
        RevTree indexTree = repo.indexDatabase().getTree(indexTreeId.get());
        Set<org.locationtech.geogig.model.Node> nodes = RevObjectTestSupport.getTreeNodes(indexTree,
                repo.indexDatabase());
        for (org.locationtech.geogig.model.Node n : nodes) {
            Map<String, Object> extraData = n.getExtraData();
            if (extraData.containsKey(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA)) {
                Map<String, Object> attributeData = (Map<String, Object>) extraData
                        .get(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA);
                assertFalse(attributeData.containsKey(attributeName));
            }
        }
    }

    public void setVariable(String name, String value) {
        this.variables.put(name, value);
    }

    public String getVariable(String name) {
        return getVariable(name, this.variables);
    }

    static public String getVariable(String varName, Map<String, String> variables) {
        String varValue = variables.get(varName);
        Preconditions.checkState(varValue != null, "Variable " + varName + " does not exist");
        return varValue;
    }

    public String replaceVariables(final String text) {
        return replaceVariables(text, this.variables, this);
    }

    public String replaceVariables(final String text, Map<String, String> variables,
            DefaultStepDefinitions defaultStepDefinitions) {
        String resource = text;
        int varIndex = -1;
        while ((varIndex = resource.indexOf("{@")) > -1) {
            for (int i = varIndex + 1; i < resource.length(); i++) {
                char c = resource.charAt(i);
                if (c == '}') {
                    String varName = resource.substring(varIndex + 1, i);
                    String varValue;
                    if (defaultStepDefinitions != null && varName.startsWith("@ObjectId|")) {
                        String[] parts = varName.split("\\|");
                        String repoName = parts[1];
                        // repoName for remote = "remoterepo", "localrepo" for local
                        CLIContext context = contextProvider.getRepositoryContext(repoName);
                        Repository repo = context.geogigCLI.getGeogig().getRepository();
                        String ref;
                        Optional<RevObject> object;
                        ref = parts[2];
                        object = repo.command(RevObjectParse.class).setRefSpec(ref).call();

                        if (object.isPresent()) {
                            varValue = object.get().getId().toString();
                        } else {
                            varValue = "specified ref doesn't exist";
                        }
                    } else {
                        varValue = getVariable(varName, variables);
                    }
                    String tmp = resource.replace("{" + varName + "}", varValue);
                    resource = tmp;
                    break;
                }
            }
        }
        return resource;
    }

    String replaceVariables(final String text, Map<String, String> variables) {
        return replaceVariables(text, variables, null);
    }
}
