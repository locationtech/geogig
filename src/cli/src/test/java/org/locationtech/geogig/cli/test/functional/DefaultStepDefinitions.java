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
import static org.locationtech.geogig.cli.test.functional.TestFeatures.setupFeatures;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevFeatureTypeImpl;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.plumbing.UpdateRef;
import org.locationtech.geogig.api.plumbing.diff.AttributeDiff;
import org.locationtech.geogig.api.plumbing.diff.FeatureDiff;
import org.locationtech.geogig.api.plumbing.diff.GenericAttributeDiffImpl;
import org.locationtech.geogig.api.plumbing.diff.Patch;
import org.locationtech.geogig.api.plumbing.diff.PatchSerializer;
import org.locationtech.geogig.api.porcelain.MergeConflictsException;
import org.locationtech.geogig.api.porcelain.MergeOp;
import org.locationtech.geogig.api.porcelain.TagCreateOp;
import org.locationtech.geogig.cli.ArgumentTokenizer;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.WorkingTree;
import org.opengis.feature.Feature;
import org.opengis.feature.type.PropertyDescriptor;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Suppliers;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import cucumber.runtime.java.StepDefAnnotation;

/**
 *
 */
@StepDefAnnotation
public class DefaultStepDefinitions {

    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    private FunctionalTestState state;

    @cucumber.api.java.Before
    public void before() throws Exception {
        state = FunctionalTestState.get();
        state.before();
    }

    @cucumber.api.java.After
    public void after() {
        state.after();
        System.gc();
        System.runFinalization();
    }

    @Given("^I am in an empty directory$")
    public void I_am_in_an_empty_directory() throws Throwable {
        state.setUpDirectories();
        assertEquals(0, state.platform.pwd().list().length);
        state.repositoryURI = null;
        state.setupGeogig();
    }

    @When("^I run the command \"(.*?)\"$")
    public void I_run_the_command_X(String commandSpec) throws Throwable {
        String[] args = ArgumentTokenizer.tokenize(commandSpec);
        File pwd = state.platform.pwd();
        for (int i = 0; i < args.length; i++) {
            args[i] = args[i].replace("${currentdir}", pwd.getAbsolutePath());
            args[i] = args[i].replace("\"", "");
        }
        state.runCommand(args);
    }

    @Then("^it should exit with non-zero exit code$")
    public void it_should_exit_with_non_zero_exit_code() throws Throwable {
        assertFalse("exited with exit code " + state.exitCode, state.exitCode == 0);
    }

    @Then("^it should exit with zero exit code$")
    public void it_should_exit_with_zero_exit_code() throws Throwable {
        assertEquals(0, state.exitCode);
    }

    @Then("^it should answer \"([^\"]*)\"$")
    public void it_should_answer_exactly(String expected) throws Throwable {
        File pwd = state.platform.pwd();
        expected = expected.replace("${currentdir}", pwd.getAbsolutePath()).toLowerCase()
                .replaceAll("\\\\", "/");
        String actual = state.stdOut.toString().replaceAll(LINE_SEPARATOR, "")
                .replaceAll("\\\\", "/").trim().toLowerCase();
        assertEquals(expected, actual);
    }

    @Then("^the response should contain \"([^\"]*)\"$")
    public void the_response_should_contain(String expected) throws Throwable {
        String actual = state.stdOut.toString().replaceAll(LINE_SEPARATOR, "").replaceAll("\\\\",
                "/");
        if (!actual.contains(expected))
            System.err.println(actual);
        assertTrue("'" + actual + "' does not contain '" + expected + "'",
                actual.contains(expected));
    }

    @Then("^the response should not contain \"([^\"]*)\"$")
    public void the_response_should_not_contain(String expected) throws Throwable {
        String actual = state.stdOut.toString().replaceAll(LINE_SEPARATOR, "").replaceAll("\\\\",
                "/");
        assertFalse(actual, actual.contains(expected));
    }

    @Then("^the response should contain ([^\"]*) lines$")
    public void the_response_should_contain_x_lines(int lines) throws Throwable {
        String output = state.stdOut.toString();
        String[] lineStrings = output.split(LINE_SEPARATOR);
        assertEquals(output, lines, lineStrings.length);
    }

    @Then("^the response should start with \"([^\"]*)\"$")
    public void the_response_should_start_with(String expected) throws Throwable {
        String actual = state.stdOut.toString().replaceAll(LINE_SEPARATOR, "");
        assertTrue(actual, actual.startsWith(expected));
    }

    @Then("^the repository directory shall exist$")
    public void the_repository_directory_shall_exist() throws Throwable {
        List<String> output = state.runAndParseCommand(true, "rev-parse", "--resolve-geogig-uri");
        assertEquals(output.toString(), 1, output.size());
        String location = output.get(0);
        assertNotNull(location);
        File repoDir = new File(location);
        assertTrue("Repository directory not found: " + repoDir.getAbsolutePath(),
                repoDir.exists());
    }

    @Given("^I have a remote ref called \"([^\"]*)\"$")
    public void i_have_a_remote_ref_called(String expected) throws Throwable {
        String ref = "refs/remotes/origin/" + expected;
        state.geogigCLI.getGeogig(Hints.readWrite()).command(UpdateRef.class).setName(ref)
                .setNewValue(ObjectId.NULL).call();
        Optional<Ref> refValue = state.geogigCLI.getGeogig(Hints.readWrite())
                .command(RefParse.class).setName(ref).call();
        assertTrue(refValue.isPresent());
        assertEquals(refValue.get().getObjectId(), ObjectId.NULL);
    }

    @Given("^I have a remote tag called \"([^\"]*)\"$")
    public void i_have_a_remote_tag_called(String expected) throws Throwable {
        state.geogigCLI.getGeogig(Hints.readWrite()) //
                .command(TagCreateOp.class) //
                .setName(expected) //
                .setMessage("Tagged " + expected) //
                .setCommitId(ObjectId.NULL) //
                .call();
    }

    @Given("^I have an unconfigured repository$")
    public void I_have_an_unconfigured_repository() throws Throwable {
        state.setUpDirectories();
        state.setupGeogig();

        List<String> output = state.runAndParseCommand(true, "init");
        assertEquals(output.toString(), 1, output.size());
        assertNotNull(output.get(0));
        assertTrue(output.get(0), output.get(0).startsWith("Initialized"));
    }

    @Given("^I have a merge conflict state$")
    public void I_have_a_merge_conflict_state() throws Throwable {
        I_have_conflicting_branches();
        Ref branch = state.geogigCLI.getGeogig(Hints.readOnly()).command(RefParse.class)
                .setName("branch1").call().get();
        try {
            state.geogigCLI.getGeogig(Hints.readWrite()).command(MergeOp.class)
                    .addCommit(Suppliers.ofInstance(branch.getObjectId())).call();
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
        state.insertAndAdd(points1);
        state.runCommand(true, "commit -m Commit1");
        state.runCommand(true, "branch branch1");
        state.runCommand(true, "branch branch2");
        state.insertAndAdd(points1Modified);
        state.runCommand(true, "commit -m Commit2");
        state.insertAndAdd(lines1);
        state.runCommand(true, "commit -m Commit3");
        state.runCommand(true, "checkout branch1");
        state.insertAndAdd(points1ModifiedB);
        state.insertAndAdd(points2);
        state.runCommand(true, "commit -m Commit4");
        state.runCommand(true, "checkout branch2");
        state.insertAndAdd(points3);
        state.runCommand(true, "commit -m Commit5");
        state.runCommand(true, "checkout master");
    }

    @Given("^I set up a hook$")
    public void I_set_up_a_hook() throws Throwable {
        File hooksDir = new File(state.platform.pwd(), ".geogig/hooks");
        File hook = new File(hooksDir, "pre_commit.js");
        String script = "exception = Packages.org.locationtech.geogig.api.hooks.CannotRunGeogigOperationException;\n"
                + "msg = params.get(\"message\");\n" + "if (msg.length() < 5){\n"
                + "\tthrow new exception(\"Commit messages must have at least 5 letters\");\n"
                + "}\n" + "params.put(\"message\", msg.toLowerCase());";
        Files.write(script, hook, Charset.forName("UTF-8"));
    }

    @Given("^there is a remote repository$")
    public void there_is_a_remote_repository() throws Throwable {
        createRemote("remoterepo");
    }

    @Given("^there is a remote repository with blank spaces$")
    public void there_is_a_remote_repository_with_blank_spaces() throws Throwable {
        createRemote("remote repo");
    }

    private void createRemote(String name) throws Throwable {
        I_am_in_an_empty_directory();

        final File currDir = state.platform.pwd();

        List<String> output = state.runAndParseCommand(true, "init", name);

        assertEquals(output.toString(), 1, output.size());
        assertNotNull(output.get(0));
        assertTrue(output.get(0), output.get(0).startsWith("Initialized"));

        final File remoteRepo = new File(currDir, name);
        state.remoteRepo = remoteRepo;
        state.platform.setWorkingDir(remoteRepo);
        state.setupGeogig();
        state.runCommand(true, "config", "--global", "user.name", "John Doe");
        state.runCommand(true, "config", "--global", "user.email", "JohnDoe@example.com");
        state.insertAndAdd(points1);
        state.runCommand(true, "commit -m Commit1");
        state.runCommand(true, "branch -c branch1");
        state.insertAndAdd(points2);
        state.runCommand(true, "commit -m Commit2");
        state.insertAndAdd(points3);
        state.runCommand(true, "commit -m Commit3");
        state.runCommand(true, "checkout master");
        state.insertAndAdd(lines1);
        state.runCommand(true, "commit -m Commit4");
        state.insertAndAdd(lines2);
        state.runCommand(true, "commit -m Commit5");

        state.platform.setWorkingDir(currDir);
        state.setupGeogig();
    }

    @Given("^there is a remote repository with a tag named \"([^\"]*)\"$")
    public void there_is_a_remote_repository_with_a_tag_named(String tagName) throws Throwable {
        I_am_in_an_empty_directory();

        final File currDir = state.platform.pwd();

        List<String> output = state.runAndParseCommand(true, "init", "remoterepo");

        assertEquals(output.toString(), 1, output.size());
        assertNotNull(output.get(0));
        assertTrue(output.get(0), output.get(0).startsWith("Initialized"));

        final File remoteRepo = new File(currDir, "remoterepo");
        state.remoteRepo = remoteRepo;
        state.platform.setWorkingDir(remoteRepo);
        state.setupGeogig();
        state.runCommand(true, "config", "--global", "user.name", "John Doe");
        state.runCommand(true, "config", "--global", "user.email", "JohnDoe@example.com");
        state.insertAndAdd(points1);
        state.runCommand(true, "commit -m Commit1");
        state.runCommand(true, "branch -c branch1");
        state.insertAndAdd(points2);
        state.runCommand(true, "commit -m Commit2");
        state.insertAndAdd(points3);
        state.runCommand(true, "commit -m Commit3");
        state.runCommand(true, "checkout master");
        state.insertAndAdd(lines1);
        state.runCommand(true, "commit -m Commit4");
        state.insertAndAdd(lines2);
        state.runCommand(true, "commit -m Commit5");
        state.runCommand(true, "tag " + tagName + " -m Created_" + tagName + "");

        String actual = state.stdOut.toString().replaceAll(LINE_SEPARATOR, "")
                .replaceAll("\\\\", "/").trim().toLowerCase();
        assertTrue(actual, actual.startsWith("created tag " + tagName));

        state.platform.setWorkingDir(currDir);
        state.setupGeogig();
    }

    @Given("^I have a repository$")
    public void I_have_a_repository() throws Throwable {
        I_have_an_unconfigured_repository();
        state.runCommand(true, "config", "--global", "user.name", "John Doe");
        state.runCommand(true, "config", "--global", "user.email", "JohnDoe@example.com");
    }

    @Given("^I have a repository with a remote$")
    public void I_have_a_repository_with_a_remote() throws Throwable {
        there_is_a_remote_repository();

        List<String> output = state.runAndParseCommand("init", "localrepo");
        assertEquals(output.toString(), 1, output.size());
        assertNotNull(output.get(0));
        assertTrue(output.get(0), output.get(0).startsWith("Initialized"));

        state.platform.setWorkingDir(new File(state.platform.pwd(), "localrepo"));
        state.setupGeogig();

        state.runCommand(true, "config", "--global", "user.name", "John Doe");
        state.runCommand(true, "config", "--global", "user.email", "JohnDoe@example.com");

        File remoteRepo = state.remoteRepo;
        String remotePath = remoteRepo.getAbsolutePath();
        state.runCommand(true, "remote", "add", "origin", remotePath);
    }

    @Given("^I have staged \"([^\"]*)\"$")
    public void I_have_staged(String feature) throws Throwable {
        if (feature.equals("points1")) {
            state.insertAndAdd(points1);
        } else if (feature.equals("points2")) {
            state.insertAndAdd(points2);
        } else if (feature.equals("points3")) {
            state.insertAndAdd(points3);
        } else if (feature.equals("points1_modified")) {
            state.insertAndAdd(points1_modified);
        } else if (feature.equals("lines1")) {
            state.insertAndAdd(lines1);
        } else if (feature.equals("lines2")) {
            state.insertAndAdd(lines2);
        } else if (feature.equals("lines3")) {
            state.insertAndAdd(lines3);
        } else {
            throw new Exception("Unknown Feature");
        }
    }

    @Given("^I have 6 unstaged features$")
    public void I_have_6_unstaged_features() throws Throwable {
        state.insertFeatures();
    }

    @Given("^I have unstaged \"([^\"]*)\"$")
    public void I_have_unstaged(String feature) throws Throwable {
        if (feature.equals("points1")) {
            state.insert(points1);
        } else if (feature.equals("points2")) {
            state.insert(points2);
        } else if (feature.equals("points3")) {
            state.insert(points3);
        } else if (feature.equals("points1_modified")) {
            state.insert(points1_modified);
        } else if (feature.equals("lines1")) {
            state.insert(lines1);
        } else if (feature.equals("lines2")) {
            state.insert(lines2);
        } else if (feature.equals("lines3")) {
            state.insert(lines3);
        } else {
            throw new Exception("Unknown Feature");
        }
    }

    @Given("^I have unstaged an empty feature type$")
    public void I_have_unstaged_an_empty_feature_type() throws Throwable {
        state.insert(points1);
        GeoGIG geogig = state.geogigCLI.newGeoGIG();
        final WorkingTree workTree = geogig.getRepository().workingTree();
        workTree.delete(pointsName, idP1);
        geogig.close();
    }

    @Given("^I stage 6 features$")
    public void I_stage_6_features() throws Throwable {
        state.insertAndAddFeatures();
    }

    @Given("^I have several commits$")
    public void I_have_several_commits() throws Throwable {
        state.insertAndAdd(points1, points2);
        state.runCommand(true, "commit -m Commit1");
        state.insertAndAdd(points3, lines1);
        state.runCommand(true, "commit -m Commit2");
        state.insertAndAdd(lines2, lines3);
        state.runCommand(true, "commit -m Commit3");
        state.insertAndAdd(points1_modified);
        state.runCommand(true, "commit -m Commit4");
    }

    @Given("^I have several branches")
    public void I_have_several_branches() throws Throwable {
        state.insertAndAdd(points1);
        state.runCommand(true, "commit -m Commit1");
        state.runCommand(true, "branch -c branch1");
        state.insertAndAdd(points2);
        state.runCommand(true, "commit -m Commit2");
        state.insertAndAdd(points3);
        state.runCommand(true, "commit -m Commit3");
        state.runCommand(true, "branch -c branch2");
        state.insertAndAdd(lines1);
        state.runCommand(true, "commit -m Commit4");
        state.runCommand(true, "checkout master");
        state.insertAndAdd(lines2);
        state.runCommand(true, "commit -m Commit5");
    }

    @Given("I modify and add a feature")
    public void I_modify_and_add_a_feature() throws Throwable {
        state.insertAndAdd(points1_modified);
    }

    @Given("I modify a feature")
    public void I_modify_a_feature() throws Throwable {
        state.insert(points1_modified);
    }

    @Given("^I a featuretype is modified$")
    public void I_modify_a_feature_type() throws Throwable {
        state.deleteAndReplaceFeatureType();
    }

    @Then("^if I change to the respository subdirectory \"([^\"]*)\"$")
    public void if_I_change_to_the_respository_subdirectory(String subdirSpec) throws Throwable {
        String[] subdirs = subdirSpec.split("/");
        File dir = state.platform.pwd();
        for (String subdir : subdirs) {
            dir = new File(dir, subdir);
        }
        assertTrue(dir.exists());
        state.platform.setWorkingDir(dir);
        state.setupGeogig();
    }

    @Given("^I have a patch file$")
    public void I_have_a_patch_file() throws Throwable {
        Patch patch = new Patch();
        String path = NodeRef.appendChild(pointsName, points1.getIdentifier().getID());
        Map<PropertyDescriptor, AttributeDiff> map = Maps.newHashMap();
        Optional<?> oldValue = Optional.fromNullable(points1.getProperty("sp").getValue());
        GenericAttributeDiffImpl diff = new GenericAttributeDiffImpl(oldValue, Optional.of("new"));
        map.put(pointsType.getDescriptor("sp"), diff);
        FeatureDiff feaureDiff = new FeatureDiff(path, map, RevFeatureTypeImpl.build(pointsType),
                RevFeatureTypeImpl.build(pointsType));
        patch.addModifiedFeature(feaureDiff);
        File file = new File(state.platform.pwd(), "test.patch");
        BufferedWriter writer = Files.newWriter(file, Charsets.UTF_8);
        PatchSerializer.write(writer, patch);
        writer.flush();
        writer.close();
    }

    @Given("^I have an insert file$")
    public void I_have_an_insert_file() throws Throwable {
        File file = new File(state.platform.pwd(), "insert");
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
        File dir = state.platform.pwd();
        for (String subdir : subdirs) {
            dir = new File(dir, subdir);
        }
        assertTrue(dir.mkdirs());
        state.platform.setWorkingDir(dir);
    }
}
