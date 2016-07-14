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

import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevTag;
import org.locationtech.geogig.api.porcelain.TagCreateOp;
import org.locationtech.geogig.api.porcelain.TagListOp;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.TestData;
import org.locationtech.geogig.web.api.TestParams;
import org.restlet.data.Method;
import org.skyscreamer.jsonassert.JSONAssert;

import com.google.common.collect.ImmutableList;

public class TagTest extends AbstractWebOpTest {

    @Override
    protected String getRoute() {
        return "tag";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return Tag.class;
    }

    @Override
    protected boolean requiresTransaction() {
        return false;
    }

    @Test
    public void testBuildParameters() {
        ParameterSet options = TestParams.of("name", "TestName", "commit", "TestCommit", "message",
                "TestMessage");

        Tag op = (Tag) buildCommand(options);
        assertEquals("TestName", op.name);
        assertEquals("TestCommit", op.commit);
        assertEquals("TestMessage", op.message);
    }

    @Test
    public void testListTags() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        Ref master = geogig.command(org.locationtech.geogig.api.plumbing.RefParse.class)
                .setName(Ref.MASTER).call().get();
        Ref branch1 = geogig.command(org.locationtech.geogig.api.plumbing.RefParse.class)
                .setName("branch1").call().get();

        RevTag branch1Tag = geogig.command(TagCreateOp.class).setCommitId(branch1.getObjectId())
                .setName("Branch1Tag")
                .call();
        RevTag masterTag = geogig.command(TagCreateOp.class).setCommitId(master.getObjectId())
                .setName("MasterTag")
                .setMessage("provided message")
                .call();

        ParameterSet options = TestParams.of();
        buildCommand(options).run(testContext.get());

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        String expectedTags = "[" + expectedTagString(branch1Tag) + ","
                + expectedTagString(masterTag) + "]";
        JSONAssert.assertEquals(expectedTags, response.getJSONArray("Tag").toString(), true);
    }

    @Test
    public void testDeleteTagNoName() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        Ref master = geogig.command(org.locationtech.geogig.api.plumbing.RefParse.class)
                .setName(Ref.MASTER).call().get();
        Ref branch1 = geogig.command(org.locationtech.geogig.api.plumbing.RefParse.class)
                .setName("branch1").call().get();

        geogig.command(TagCreateOp.class).setCommitId(branch1.getObjectId()).setName("Branch1Tag")
                .call();
        geogig.command(TagCreateOp.class).setCommitId(master.getObjectId()).setName("MasterTag")
                .call();

        ParameterSet options = TestParams.of();
        ex.expect(CommandSpecException.class);
        ex.expectMessage("You must specify the tag name to delete.");
        testContext.setRequestMethod(Method.DELETE);
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testDeleteNonexistingTag() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        Ref master = geogig.command(org.locationtech.geogig.api.plumbing.RefParse.class)
                .setName(Ref.MASTER).call().get();
        Ref branch1 = geogig.command(org.locationtech.geogig.api.plumbing.RefParse.class)
                .setName("branch1").call().get();

        geogig.command(TagCreateOp.class).setCommitId(branch1.getObjectId()).setName("Branch1Tag")
                .call();
        geogig.command(TagCreateOp.class).setCommitId(master.getObjectId()).setName("MasterTag")
                .call();

        ParameterSet options = TestParams.of("name", "nonexistent");
        ex.expect(IllegalArgumentException.class);
        ex.expectMessage("Wrong tag name: nonexistent");
        testContext.setRequestMethod(Method.DELETE);
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testDeleteTag() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        Ref master = geogig.command(org.locationtech.geogig.api.plumbing.RefParse.class)
                .setName(Ref.MASTER).call().get();
        Ref branch1 = geogig.command(org.locationtech.geogig.api.plumbing.RefParse.class)
                .setName("branch1").call().get();

        RevTag branch1Tag = geogig.command(TagCreateOp.class).setCommitId(branch1.getObjectId())
                .setName("Branch1Tag")
                .call();
        geogig.command(TagCreateOp.class).setCommitId(master.getObjectId()).setName("MasterTag")
                .call();

        ParameterSet options = TestParams.of("name", "Branch1Tag");
        testContext.setRequestMethod(Method.DELETE);
        buildCommand(options).run(testContext.get());
        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        String expectedTag = expectedTagString(branch1Tag);
        JSONAssert.assertEquals(expectedTag, response.getJSONObject("DeletedTag").toString(), true);

        ImmutableList<RevTag> tags = geogig.command(TagListOp.class).call();
        assertEquals(1, tags.size());
        assertEquals("MasterTag", tags.get(0).getName());
    }

    @Test
    public void testCreateTagNoName() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        ParameterSet options = TestParams.of("commit", Ref.MASTER);
        ex.expect(CommandSpecException.class);
        ex.expectMessage(
                "You must specify list or delete, or provide a name, message, and commit for the new tag.");
        testContext.setRequestMethod(Method.PUT);
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testCreateTagNoCommit() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        ParameterSet options = TestParams.of("name", "MasterTag");
        ex.expect(CommandSpecException.class);
        ex.expectMessage("You must specify a commit to point the tag to.");
        testContext.setRequestMethod(Method.PUT);
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testCreateTagInvalidCommit() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        ParameterSet options = TestParams.of("name", "MasterTag", "commit", "nonexistent");
        ex.expect(CommandSpecException.class);
        ex.expectMessage("'nonexistent' could not be resolved.");
        testContext.setRequestMethod(Method.PUT);
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testCreateTagNoMessage() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        Ref master = geogig.command(org.locationtech.geogig.api.plumbing.RefParse.class)
                .setName(Ref.MASTER).call().get();

        ParameterSet options = TestParams.of("name", "MasterTag", "commit", "master");
        testContext.setRequestMethod(Method.PUT);
        buildCommand(options).run(testContext.get());

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        String expectedTag = "{'name':'MasterTag', 'commitid':'" + master.getObjectId()
                + "', 'message':'', 'tagger': {'name':'John Doe', 'email':'JohnDoe@example.com'}}";
        JSONAssert.assertEquals(expectedTag, response.getJSONObject("Tag").toString(), false);

        ImmutableList<RevTag> tags = geogig.command(TagListOp.class).call();
        assertEquals(1, tags.size());
        assertEquals("MasterTag", tags.get(0).getName());
        assertEquals(master.getObjectId(), tags.get(0).getCommitId());
        assertEquals("", tags.get(0).getMessage());
    }

    @Test
    public void testCreateTagWithMessage() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        Ref master = geogig.command(org.locationtech.geogig.api.plumbing.RefParse.class)
                .setName(Ref.MASTER).call().get();

        ParameterSet options = TestParams.of("name", "MasterTag", "commit", "master", "message",
                "My tag message");
        testContext.setRequestMethod(Method.PUT);
        buildCommand(options).run(testContext.get());

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        String expectedTag = "{'name':'MasterTag', 'commitid':'" + master.getObjectId()
                + "', 'message':'My tag message', 'tagger': {'name':'John Doe', 'email':'JohnDoe@example.com'}}";
        JSONAssert.assertEquals(expectedTag, response.getJSONObject("Tag").toString(), false);

        ImmutableList<RevTag> tags = geogig.command(TagListOp.class).call();
        assertEquals(1, tags.size());
        assertEquals("MasterTag", tags.get(0).getName());
        assertEquals(master.getObjectId(), tags.get(0).getCommitId());
        assertEquals("My tag message", tags.get(0).getMessage());
    }

    private String expectedTagString(RevTag tag) {
        StringBuilder builder = new StringBuilder("{");
        builder.append("'id':'").append(tag.getId().toString()).append("',");
        builder.append("'name':'").append(tag.getName()).append("',");
        builder.append("'commitid':'").append(tag.getCommitId().toString()).append("',");
        builder.append("'message':'").append(tag.getMessage()).append("',");
        builder.append("'tagger':{");
        builder.append("'name':'").append(tag.getTagger().getName().get()).append("',");
        builder.append("'email':'").append(tag.getTagger().getEmail().get()).append("',");
        builder.append("'timestamp':").append(Long.toString(tag.getTagger().getTimestamp()))
                .append(",");
        builder.append("'timeZoneOffset':")
                .append(Long.toString(tag.getTagger().getTimeZoneOffset()));
        builder.append("}}");
        return builder.toString();
    }
}
