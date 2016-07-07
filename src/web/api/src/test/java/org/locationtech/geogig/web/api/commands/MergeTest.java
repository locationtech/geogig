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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.GeogigTransaction;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevFeatureBuilder;
import org.locationtech.geogig.api.plumbing.FindTreeChild;
import org.locationtech.geogig.api.plumbing.TransactionBegin;
import org.locationtech.geogig.api.plumbing.UpdateSymRef;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.TestData;
import org.locationtech.geogig.web.api.TestParams;
import org.skyscreamer.jsonassert.JSONAssert;

public class MergeTest extends AbstractWebOpTest {

    @Override
    protected String getRoute() {
        return "merge";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return Merge.class;
    }

    @Test
    public void testBuildParameters() {
        ParameterSet options = TestParams.of("noCommit", "true", "commit", "master", "authorName",
                "Tester", "authorEmail", "tester@example.com");

        Merge op = (Merge) buildCommand(options);
        assertEquals("master", op.commit);
        assertEquals("Tester", op.authorName.get());
        assertEquals("tester@example.com", op.authorEmail.get());
        assertTrue(op.noCommit);
    }

    @Test
    public void testMergeNoCommit() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();

        ParameterSet options = TestParams.of("transactionId",
                transaction.getTransactionId().toString());
        ex.expect(CommandSpecException.class);
        ex.expectMessage("No commits were specified for merging.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testMergeNoHead() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        geogig.command(UpdateSymRef.class).setDelete(true).setName(Ref.HEAD).call();
        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();

        ParameterSet options = TestParams.of("commit", "branch1", "transactionId",
                transaction.getTransactionId().toString());
        ex.expect(CommandSpecException.class);
        ex.expectMessage("Repository has no HEAD, can't merge.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testMergeInvalidCommit() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();

        ParameterSet options = TestParams.of("commit", "nonexistent", "transactionId",
                transaction.getTransactionId().toString());
        ex.expect(CommandSpecException.class);
        ex.expectMessage("Couldn't resolve 'nonexistent' to a commit.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testMerge() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        TestData testData = new TestData(geogig);
        testData.init();

        testData.checkout("master");
        testData.insert(TestData.point1);
        testData.add();
        RevCommit ancestor = geogig.command(CommitOp.class).setMessage("point1").call();
        testData.branch("branch1");
        testData.insert(TestData.point2);
        testData.add();
        RevCommit ours = geogig.command(CommitOp.class).setMessage("point2").call();
        testData.checkout("branch1");
        testData.insert(TestData.point3);
        testData.add();
        RevCommit theirs = geogig.command(CommitOp.class).setMessage("point3").call();
        testData.checkout("master");

        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();

        ParameterSet options = TestParams.of("commit", "branch1", "authorName", "Tester",
                "authorEmail", "tester@example.com", "transactionId",
                transaction.getTransactionId().toString());
        buildCommand(options).run(testContext.get());

        String path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point1.getID());
        assertTrue(transaction.command(FindTreeChild.class).setChildPath(path).call().isPresent());

        path = NodeRef.appendChild(TestData.pointsType.getTypeName(), TestData.point2.getID());
        assertTrue(transaction.command(FindTreeChild.class).setChildPath(path).call()
                .isPresent());

        path = NodeRef.appendChild(TestData.pointsType.getTypeName(), TestData.point3.getID());
        assertTrue(transaction.command(FindTreeChild.class).setChildPath(path).call()
                .isPresent());

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        JSONObject merge = response.getJSONObject("Merge");
        assertEquals(ours.getId().toString(), merge.getString("ours"));
        assertEquals(ancestor.getId().toString(), merge.getString("ancestor"));
        assertEquals(theirs.getId().toString(), merge.getString("theirs"));
        assertNotNull(merge.getString("mergedCommit"));
        JSONObject feature = merge.getJSONObject("Feature");
        String expected = "{'change':'ADDED','id':'" + path
                + "','geometry':'POINT (10 10)','crs':'EPSG:4326'}";
        JSONAssert.assertEquals(expected, feature.toString(), true);
    }

    @Test
    public void testMergeConflict() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        TestData testData = new TestData(geogig);
        testData.init();

        testData.checkout("master");
        testData.insert(TestData.point1);
        testData.add();
        RevCommit ancestor = geogig.command(CommitOp.class).setMessage("point1").call();
        testData.branch("branch1");
        testData.insert(TestData.point1_modified);
        testData.add();
        RevCommit ours = geogig.command(CommitOp.class).setMessage("modify point1").call();
        ObjectId point1_id = RevFeatureBuilder.build(TestData.point1_modified).getId();
        testData.checkout("branch1");
        testData.remove(TestData.point1);
        testData.add();
        RevCommit theirs = geogig.command(CommitOp.class).setMessage("remove point1").call();
        testData.checkout("master");

        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();

        ParameterSet options = TestParams.of("commit", "branch1", "authorName", "Tester",
                "authorEmail", "tester@example.com", "transactionId",
                transaction.getTransactionId().toString());
        buildCommand(options).run(testContext.get());

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        JSONObject merge = response.getJSONObject("Merge");
        assertEquals(ours.getId().toString(), merge.getString("ours"));
        assertEquals(ancestor.getId().toString(), merge.getString("ancestor"));
        assertEquals(theirs.getId().toString(), merge.getString("theirs"));
        assertEquals(1, merge.getInt("conflicts"));
        JSONObject feature = merge.getJSONObject("Feature");
        assertEquals("CONFLICT", feature.get("change"));
        String path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point1.getID());
        assertEquals(path, feature.get("id"));
        assertEquals("POINT (0 0)", feature.get("geometry"));
        assertEquals(point1_id.toString(), feature.get("ourvalue"));
        assertEquals(ObjectId.NULL.toString(), feature.get("theirvalue"));
    }

}
