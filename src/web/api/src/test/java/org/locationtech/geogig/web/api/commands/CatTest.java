/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.web.api.commands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureBuilder;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObjectTestSupport;
import org.locationtech.geogig.model.RevPerson;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.ResolveFeatureType;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.porcelain.TagCreateOp;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.TestData;
import org.locationtech.geogig.web.api.TestParams;

public class CatTest extends AbstractWebOpTest {

    @Override
    protected String getRoute() {
        return "cat";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return Cat.class;
    }

    @Override
    protected boolean requiresTransaction() {
        return false;
    }

    @Test
    public void testBuildParameters() {
        ObjectId id = RevObjectTestSupport.hashString("objectId");
        ParameterSet options = TestParams.of("objectid", id.toString());

        Cat op = (Cat) buildCommand(options);
        assertEquals(id, op.object);
    }

    @Test
    public void testCatNoObjectId() {
        ParameterSet options = TestParams.of();

        ex.expect(IllegalArgumentException.class);
        ex.expectMessage("You must specify a non-null ObjectId.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testCatNonNullObjectId() {
        ParameterSet options = TestParams.of("objectid", ObjectId.NULL.toString());

        ex.expect(IllegalArgumentException.class);
        ex.expectMessage("You must specify a non-null ObjectId.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testCatCommit() throws Exception {
        TestData testData = new TestData(testContext.get().getRepository());
        testData.init();
        testData.loadDefaultData();

        RevCommit lastCommit = testContext.get().getRepository().command(LogOp.class).call().next();
        ParameterSet options = TestParams.of("objectid", lastCommit.getId().toString());
        buildCommand(options).run(testContext.get());
        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        JSONObject commit = response.getJSONObject("commit");
        assertEquals(lastCommit.getId().toString(), commit.getString("id"));
        assertEquals(lastCommit.getTreeId().toString(), commit.getString("tree"));
        assertEquals(lastCommit.getMessage(), commit.getString("message"));
        JSONObject parents = commit.getJSONObject("parents");
        String expectedParents = "['" + lastCommit.getParentIds().get(0) + "', '"
                + lastCommit.getParentIds().get(1) + "']";
        assertTrue(TestData.jsonEquals(TestData.toJSONArray(expectedParents),
                parents.getJSONArray("id"), false));
        RevPerson author = lastCommit.getAuthor();
        RevPerson committer = lastCommit.getCommitter();
        JSONObject authorObject = commit.getJSONObject("author");
        assertEquals(author.getName().or(""), authorObject.getString("name"));
        assertEquals(author.getEmail().or(""), authorObject.getString("email"));
        assertEquals(author.getTimestamp(), authorObject.getLong("timestamp"));
        assertEquals(author.getTimeZoneOffset(), authorObject.getInt("timeZoneOffset"));
        JSONObject committerObject = commit.getJSONObject("committer");
        assertEquals(committer.getName().or(""), committerObject.getString("name"));
        assertEquals(committer.getEmail().or(""), committerObject.getString("email"));
        assertEquals(committer.getTimestamp(), committerObject.getLong("timestamp"));
        assertEquals(committer.getTimeZoneOffset(), committerObject.getInt("timeZoneOffset"));

    }

    @Test
    public void testCatTree() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(testContext.get().getRepository());
        testData.init();
        testData.loadDefaultData();

        RevTree tree = geogig.index().getTree();
        ParameterSet options = TestParams.of("objectid", tree.getId().toString());
        buildCommand(options).run(testContext.get());
        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        JSONObject treeObject = response.getJSONObject("tree");
        assertEquals(tree.getId().toString(), treeObject.getString("id"));
        assertEquals(tree.size(), treeObject.getLong("size"));
        assertEquals(tree.numTrees(), treeObject.getInt("numtrees"));
        JSONArray subtrees = treeObject.getJSONArray("subtree");
        assertEquals(3, subtrees.length());
    }

    @Test
    public void testCatFeature() throws Exception {
        TestData testData = new TestData(testContext.get().getRepository());
        testData.init();
        testData.loadDefaultData();

        RevFeature point = RevFeatureBuilder.build(TestData.point1);
        ParameterSet options = TestParams.of("objectid", point.getId().toString());
        buildCommand(options).run(testContext.get());
        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        JSONObject feature = response.getJSONObject("feature");
        assertEquals(point.getId().toString(), feature.getString("id"));
        JSONArray attributes = feature.getJSONArray("attribute");
        assertEquals(3, attributes.length());
        String expectedAttributes = "[{'type': 'STRING', 'value': 'StringProp1_1'},"
                + "{'type': 'INTEGER', 'value': 1000},"
                + "{'type': 'POINT', 'value': 'POINT (0 0)'}]";
        assertTrue(
                TestData.jsonEquals(TestData.toJSONArray(expectedAttributes), attributes, false));

    }

    @Test
    public void testCatFeatureType() throws Exception {
        TestData testData = new TestData(testContext.get().getRepository());
        testData.init();
        testData.loadDefaultData();

        RevFeatureType pointType = testContext.get().getRepository().command(ResolveFeatureType.class)
                .setRefSpec("Points").call().get();
        ParameterSet options = TestParams.of("objectid", pointType.getId().toString());
        buildCommand(options).run(testContext.get());
        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        JSONObject featureType = response.getJSONObject("featuretype");
        assertEquals(pointType.getId().toString(), featureType.getString("id"));
        assertEquals(pointType.getName().toString(), featureType.getString("name"));
        JSONArray attributes = featureType.getJSONArray("attribute");
        assertEquals(3, attributes.length());
        String expectedAttributes = "[{'name': 'sp', 'type': 'STRING'},"
                + "{'name': 'ip', 'type': 'INTEGER'},"
                + "{'name': 'geom', 'type': 'POINT', 'crs': 'urn:ogc:def:crs:EPSG::4326'}]";
        assertTrue(
                TestData.jsonEquals(TestData.toJSONArray(expectedAttributes), attributes, false));

    }

    @Test
    public void testCatTag() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(testContext.get().getRepository());
        testData.init();
        testData.loadDefaultData();

        Ref branch1Ref = geogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("branch1").call().get();
        RevTag tag = geogig.command(TagCreateOp.class).setCommitId(branch1Ref.getObjectId())
                .setMessage("new tag at branch1").setName("testTag").call();
        ParameterSet options = TestParams.of("objectid", tag.getId().toString());
        buildCommand(options).run(testContext.get());
        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        JSONObject tagObject = response.getJSONObject("tag");
        assertEquals(tag.getId().toString(), tagObject.getString("id"));
        assertEquals(tag.getName(), tagObject.getString("name"));
        assertEquals(tag.getMessage(), tagObject.getString("message"));
        assertEquals(tag.getCommitId().toString(), tagObject.getString("commitid"));
        RevPerson tagger = tag.getTagger();
        JSONObject taggerObject = tagObject.getJSONObject("tagger");
        assertEquals(tagger.getName().or(""), taggerObject.getString("name"));
        assertEquals(tagger.getEmail().or(""), taggerObject.getString("email"));
        assertEquals(tagger.getTimestamp(), taggerObject.getLong("timestamp"));
        assertEquals(tagger.getTimeZoneOffset(), taggerObject.getInt("timeZoneOffset"));
    }
}
