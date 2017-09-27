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

import java.util.UUID;

import javax.json.JsonObject;

import org.junit.Test;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.TransactionBegin;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.GeogigTransaction;
import org.locationtech.geogig.rest.repository.TestParams;
import org.locationtech.geogig.test.TestData;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;

public class CheckoutTest extends AbstractWebOpTest {

    @Override
    protected String getRoute() {
        return "checkout";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return Checkout.class;
    }

    @Test
    public void testBuildParameters() {
        ParameterSet options = TestParams.of("branch", "branch1", "ours", "true", "theirs", "true",
                "path", "Points/1", "transactionId", UUID.randomUUID().toString());

        Checkout op = (Checkout) buildCommand(options);
        assertEquals("branch1", op.branchOrCommit);
        assertTrue(op.ours);
        assertTrue(op.theirs);
        assertEquals("Points/1", op.path);
    }

    @Test
    public void testCheckoutNoBranchOrPath() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();

        ParameterSet options = TestParams.of("transactionId",
                transaction.getTransactionId().toString());
        ex.expect(CommandSpecException.class);
        ex.expectMessage("No branch or commit specified for checkout.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testCheckoutBranch() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();

        Ref head = transaction.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName(Ref.HEAD).call().get();
        Ref master = transaction.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName(Ref.MASTER).call().get();
        Ref branch1 = transaction.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName("branch1").call().get();
        assertEquals(head.getObjectId(), master.getObjectId());
        ParameterSet options = TestParams.of("branch", branch1.getName(), "transactionId",
                transaction.getTransactionId().toString());
        buildCommand(options).run(testContext.get());

        head = transaction.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName(Ref.HEAD).call().get();

        assertEquals(head.getObjectId(), branch1.getObjectId());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        assertEquals(master.getName(), response.getString("OldTarget"));
        assertEquals(branch1.getName(), response.getString("NewTarget"));
    }

    @Test
    public void testCheckoutPathNoOursOrTheirs() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();

        String path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point1.getID());
        ParameterSet options = TestParams.of("path", path, "transactionId",
                transaction.getTransactionId().toString());
        ex.expect(CommandSpecException.class);
        ex.expectMessage(
                "Please specify either ours or theirs to update the feature path specified.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testCheckoutPathBothOursAndTheirs() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();

        String path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point1.getID());
        ParameterSet options = TestParams.of("path", path, "ours", "true", "theirs", "true",
                "transactionId", transaction.getTransactionId().toString());
        ex.expect(CommandSpecException.class);
        ex.expectMessage(
                "Please specify either ours or theirs to update the feature path specified.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testCheckoutPathOurs() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();

        String path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point1.getID());
        ParameterSet options = TestParams.of("path", path, "ours", "true", "transactionId",
                transaction.getTransactionId().toString());
        buildCommand(options).run(testContext.get());
        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        assertEquals(path, response.getString("Path"));
        assertEquals("ours", response.getString("Strategy"));
    }

    @Test
    public void testCheckoutPathTheirs() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();

        String path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point1.getID());
        ParameterSet options = TestParams.of("path", path, "theirs", "true", "transactionId",
                transaction.getTransactionId().toString());
        buildCommand(options).run(testContext.get());
        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        assertEquals(path, response.getString("Path"));
        assertEquals("theirs", response.getString("Strategy"));
    }

}
