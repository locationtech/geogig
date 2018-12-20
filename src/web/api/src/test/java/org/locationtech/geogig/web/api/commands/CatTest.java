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
import static org.locationtech.geogig.web.api.JsonUtils.jsonEquals;
import static org.locationtech.geogig.web.api.JsonUtils.toJSONArray;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import org.junit.Test;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevPerson;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.plumbing.ResolveFeatureType;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.porcelain.TagCreateOp;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.repository.TestParams;
import org.locationtech.geogig.test.TestData;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.ParameterSet;

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
        assertEquals(id.toString(), op.object);
    }

    @Test
    public void testCatNoObjectId() {
        ParameterSet options = TestParams.of();

        ex.expect(IllegalArgumentException.class);
        ex.expectMessage("Required parameter 'objectid' was not provided.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testCatNonNullObjectId() {
        ParameterSet options = TestParams.of("objectid", ObjectId.NULL.toString());

        ex.expect(IllegalArgumentException.class);
        ex.expectMessage("You must specify a valid non-null ObjectId.");
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
        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonObject commit = response.getJsonObject("commit");
        assertEquals(lastCommit.getId().toString(), commit.getString("id"));
        assertEquals(lastCommit.getTreeId().toString(), commit.getString("tree"));
        assertEquals(lastCommit.getMessage(), commit.getString("message"));
        JsonObject parents = commit.getJsonObject("parents");
        String expectedParents = "[\"" + lastCommit.getParentIds().get(0) + "\", \""
                + lastCommit.getParentIds().get(1) + "\"]";
        assertTrue(jsonEquals(toJSONArray(expectedParents), parents.getJsonArray("id"), false));
        RevPerson author = lastCommit.getAuthor();
        RevPerson committer = lastCommit.getCommitter();
        JsonObject authorObject = commit.getJsonObject("author");
        assertEquals(author.getName().or(""), authorObject.getString("name"));
        assertEquals(author.getEmail().or(""), authorObject.getString("email"));
        assertEquals(author.getTimestamp(),
                authorObject.getJsonNumber("timestamp").longValueExact());
        assertEquals(author.getTimeZoneOffset(), authorObject.getInt("timeZoneOffset"));
        JsonObject committerObject = commit.getJsonObject("committer");
        assertEquals(committer.getName().or(""), committerObject.getString("name"));
        assertEquals(committer.getEmail().or(""), committerObject.getString("email"));
        assertEquals(committer.getTimestamp(),
                committerObject.getJsonNumber("timestamp").longValueExact());
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
        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonObject treeObject = response.getJsonObject("tree");
        assertEquals(tree.getId().toString(), treeObject.getString("id"));
        assertEquals(tree.size(), treeObject.getJsonNumber("size").longValueExact());
        assertEquals(tree.numTrees(), treeObject.getInt("numtrees"));
        JsonArray subtrees = treeObject.getJsonArray("subtree");
        assertEquals(3, subtrees.getValuesAs(JsonValue.class).size());
    }

    @Test
    public void testCatFeature() throws Exception {
        TestData testData = new TestData(testContext.get().getRepository());
        testData.init();
        testData.loadDefaultData();

        RevFeature point = RevFeature.builder().build(TestData.point1);
        ParameterSet options = TestParams.of("objectid", point.getId().toString());
        buildCommand(options).run(testContext.get());
        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonObject feature = response.getJsonObject("feature");
        assertEquals(point.getId().toString(), feature.getString("id"));
        JsonArray attributes = feature.getJsonArray("attribute");
        assertEquals(3, attributes.getValuesAs(JsonValue.class).size());
        String expectedAttributes = "[{\"type\": \"STRING\", \"value\": \"StringProp1_1\"},"
                + "{\"type\": \"INTEGER\", \"value\": 1000},"
                + "{\"type\": \"POINT\", \"value\": \"POINT (0 0)\"}]";
        assertTrue(jsonEquals(toJSONArray(expectedAttributes), attributes, false));

    }

    @Test
    public void testCatFeatureType() throws Exception {
        TestData testData = new TestData(testContext.get().getRepository());
        testData.init();
        testData.loadDefaultData();

        RevFeatureType pointType = testContext.get().getRepository()
                .command(ResolveFeatureType.class).setRefSpec("Points").call().get();
        ParameterSet options = TestParams.of("objectid", pointType.getId().toString());
        buildCommand(options).run(testContext.get());
        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonObject featureType = response.getJsonObject("featuretype");
        assertEquals(pointType.getId().toString(), featureType.getString("id"));
        assertEquals(pointType.getName().toString(), featureType.getString("name"));
        JsonArray attributes = featureType.getJsonArray("attribute");
        assertEquals(3, attributes.getValuesAs(JsonValue.class).size());
        String expectedAttributes = "[{\"name\": \"sp\", \"type\": \"STRING\"},"
                + "{\"name\": \"ip\", \"type\": \"INTEGER\"},"
                + "{\"name\": \"geom\", \"type\": \"POINT\", \"crs\": \"urn:ogc:def:crs:EPSG::4326\"}]";
        assertTrue(jsonEquals(toJSONArray(expectedAttributes), attributes, false));

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
        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonObject tagObject = response.getJsonObject("tag");
        assertEquals(tag.getId().toString(), tagObject.getString("id"));
        assertEquals(tag.getName(), tagObject.getString("name"));
        assertEquals(tag.getMessage(), tagObject.getString("message"));
        assertEquals(tag.getCommitId().toString(), tagObject.getString("commitid"));
        RevPerson tagger = tag.getTagger();
        JsonObject taggerObject = tagObject.getJsonObject("tagger");
        assertEquals(tagger.getName().or(""), taggerObject.getString("name"));
        assertEquals(tagger.getEmail().or(""), taggerObject.getString("email"));
        assertEquals(tagger.getTimestamp(),
                taggerObject.getJsonNumber("timestamp").longValueExact());
        assertEquals(tagger.getTimeZoneOffset(), taggerObject.getInt("timeZoneOffset"));
    }
}
