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
import static org.locationtech.geogig.web.api.JsonUtils.jsonEquals;
import static org.locationtech.geogig.web.api.JsonUtils.toJSON;

import java.util.UUID;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import org.junit.Test;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.plumbing.TransactionBegin;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.GeogigTransaction;
import org.locationtech.geogig.rest.repository.TestParams;
import org.locationtech.geogig.test.TestData;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.ParameterSet;

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
        ParameterSet options = TestParams.of("cancel", "true", "transactionId",
                UUID.randomUUID().toString());

        EndTransaction op = (EndTransaction) buildCommand(options);
        assertTrue(op.cancel);
    }

    @Test
    public void testCancelTransaction() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();

        Ref master = geogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName(Ref.MASTER).call().get();

        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();
        testData.setTransaction(transaction);

        testData.loadDefaultData();

        Ref txMaster = transaction.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName(Ref.MASTER).call().get();

        assertFalse(master.getObjectId().equals(txMaster.getObjectId()));

        ParameterSet options = TestParams.of("cancel", "true", "transactionId",
                transaction.getTransactionId().toString());
        buildCommand(options).run(testContext.get());

        Ref newMaster = geogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName(Ref.MASTER).call().get();

        assertEquals(master, newMaster);

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        assertTrue(
                jsonEquals(toJSON("{\"ID\":\"" + transaction.getTransactionId().toString() + "\"}"),
                        response.getJsonObject("Transaction"), false));

    }

    @Test
    public void testTransaction() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();

        Ref master = geogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName(Ref.MASTER).call().get();

        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();
        testData.setTransaction(transaction);

        testData.loadDefaultData();

        Ref txMaster = transaction.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName(Ref.MASTER).call().get();

        assertFalse(master.getObjectId().equals(txMaster.getObjectId()));

        ParameterSet options = TestParams.of("transactionId",
                transaction.getTransactionId().toString());
        buildCommand(options).run(testContext.get());

        Ref newMaster = geogig.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName(Ref.MASTER).call().get();

        assertEquals(txMaster, newMaster);

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        assertTrue(
                jsonEquals(toJSON("{\"ID\":\"" + transaction.getTransactionId().toString() + "\"}"),
                        response.getJsonObject("Transaction"), false));
    }

    @Test
    public void testTransactionConflicts() throws Exception {
        Repository geogig = testContext.get().getRepository();
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
        ObjectId point1_modified_id = RevFeature.builder().build(TestData.point1_modified).getId();
        testData.add();
        RevCommit theirs = geogig.command(CommitOp.class).setMessage("Modified point1").call();
        testData.setTransaction(transaction);
        testData.remove(TestData.point1);
        testData.add();
        RevCommit ours = transaction.command(CommitOp.class).setMessage("Removed point1").call();

        ParameterSet options = TestParams.of("transactionId",
                transaction.getTransactionId().toString());
        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        JsonObject merge = response.getJsonObject("Merge");
        assertEquals(ours.getId().toString(), merge.getString("ours"));
        assertEquals(ancestor.getId().toString(), merge.getString("ancestor"));
        assertEquals(theirs.getId().toString(), merge.getString("theirs"));
        assertEquals(1, merge.getInt("conflicts"));
        JsonArray featureArray = merge.getJsonArray("Feature");
        assertEquals(1, featureArray.getValuesAs(JsonValue.class).size());
        JsonObject conflictedFeature = featureArray.getJsonObject(0);
        assertEquals("CONFLICT", conflictedFeature.getString("change"));
        assertEquals(path, conflictedFeature.getString("id"));
        assertEquals(ObjectId.NULL.toString(), conflictedFeature.getString("ourvalue"));
        assertEquals(point1_modified_id.toString(), conflictedFeature.getString("theirvalue"));
    }

}
