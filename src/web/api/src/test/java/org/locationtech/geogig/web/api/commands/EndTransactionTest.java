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
import static org.junit.Assert.assertFalse;
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
import org.locationtech.geogig.api.plumbing.TransactionBegin;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.TestData;
import org.locationtech.geogig.web.api.TestParams;
import org.skyscreamer.jsonassert.JSONAssert;

public class EndTransactionTest extends AbstractWebOpTest {

    @Override
    protected String getRoute() {
        return "endTransaction";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return EndTransaction.class;
    }

    @Test
    public void testBuildParameters() {
        ParameterSet options = TestParams.of("cancel", "true");

        EndTransaction op = (EndTransaction) buildCommand(options);
        assertTrue(op.cancel);
    }

    @Test
    public void testCancelTransaction() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        TestData testData = new TestData(geogig);
        testData.init();

        Ref master = geogig.command(org.locationtech.geogig.api.plumbing.RefParse.class)
                .setName(Ref.MASTER).call().get();

        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();
        testData.setTransaction(transaction);

        testData.loadDefaultData();

        Ref txMaster = transaction.command(org.locationtech.geogig.api.plumbing.RefParse.class)
                .setName(Ref.MASTER).call().get();

        assertFalse(master.getObjectId().equals(txMaster.getObjectId()));

        ParameterSet options = TestParams.of("cancel", "true", "transactionId",
                transaction.getTransactionId().toString());
        buildCommand(options).run(testContext.get());

        Ref newMaster = geogig.command(org.locationtech.geogig.api.plumbing.RefParse.class)
                .setName(Ref.MASTER).call().get();

        assertEquals(master, newMaster);

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        JSONAssert.assertEquals("{'ID':'" + transaction.getTransactionId().toString() + "'}",
                response.getJSONObject("Transaction").toString(), false);

    }

    @Test
    public void testTransaction() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        TestData testData = new TestData(geogig);
        testData.init();

        Ref master = geogig.command(org.locationtech.geogig.api.plumbing.RefParse.class)
                .setName(Ref.MASTER).call().get();

        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();
        testData.setTransaction(transaction);

        testData.loadDefaultData();

        Ref txMaster = transaction.command(org.locationtech.geogig.api.plumbing.RefParse.class)
                .setName(Ref.MASTER).call().get();

        assertFalse(master.getObjectId().equals(txMaster.getObjectId()));

        ParameterSet options = TestParams.of("transactionId",
                transaction.getTransactionId().toString());
        buildCommand(options).run(testContext.get());

        Ref newMaster = geogig.command(org.locationtech.geogig.api.plumbing.RefParse.class)
                .setName(Ref.MASTER).call().get();

        assertEquals(txMaster, newMaster);

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        JSONAssert.assertEquals("{'ID':'" + transaction.getTransactionId().toString() + "'}",
                response.getJSONObject("Transaction").toString(), false);
    }

    @Test
    public void testTransactionConflicts() throws Exception {
        GeoGIG geogig = testContext.get().getGeoGIG();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.checkout("master");

        String path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point1.getID());

        testData.insert(TestData.point1);
        testData.add();
        RevCommit ancestor = geogig.command(CommitOp.class).setMessage("Inserted point1").call();
        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();

        testData.insert(TestData.point1_modified);
        ObjectId point1_modified_id = RevFeatureBuilder.build(TestData.point1_modified).getId();
        testData.add();
        RevCommit theirs = geogig.command(CommitOp.class).setMessage("Modified point1").call();
        testData.setTransaction(transaction);
        testData.remove(TestData.point1);
        testData.add();
        RevCommit ours = transaction.command(CommitOp.class).setMessage("Removed point1").call();

        ParameterSet options = TestParams.of("transactionId",
                transaction.getTransactionId().toString());
        buildCommand(options).run(testContext.get());

        JSONObject response = getJSONResponse().getJSONObject("response");
        assertTrue(response.getBoolean("success"));
        JSONObject merge = response.getJSONObject("Merge");
        assertEquals(ours.getId().toString(), merge.getString("ours"));
        assertEquals(ancestor.getId().toString(), merge.getString("ancestor"));
        assertEquals(theirs.getId().toString(), merge.getString("theirs"));
        assertEquals(1, merge.getInt("conflicts"));
        JSONObject conflictedFeature = merge.getJSONObject("Feature");
        assertEquals("CONFLICT", conflictedFeature.getString("change"));
        assertEquals(path, conflictedFeature.getString("id"));
        assertEquals(ObjectId.NULL.toString(), conflictedFeature.getString("ourvalue"));
        assertEquals(point1_modified_id.toString(), conflictedFeature.getString("theirvalue"));
    }

}
