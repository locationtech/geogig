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

import java.util.UUID;

import javax.json.JsonObject;

import org.junit.Test;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.TransactionBegin;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.GeogigTransaction;
import org.locationtech.geogig.rest.repository.TestParams;
import org.locationtech.geogig.test.TestData;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;

import com.google.common.base.Optional;

public class RemoveTest extends AbstractWebOpTest {

    @Override
    protected String getRoute() {
        return "remove";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return Remove.class;
    }

    @Test
    public void testBuildParameters() {
        ParameterSet options = TestParams.of("path", "some/path", "recursive", "true",
                "transactionId", UUID.randomUUID().toString());

        Remove op = (Remove) buildCommand(options);
        assertEquals("some/path", op.path);
        assertTrue(op.recursive);
    }

    @Test
    public void testRequirePath() {
        GeogigTransaction transaction = testContext.get().getRepository()
                .command(TransactionBegin.class).call();
        ParameterSet options = TestParams.of("transactionId",
                transaction.getTransactionId().toString());

        ex.expect(CommandSpecException.class);
        ex.expectMessage("Required parameter 'path' was not provided.");
        buildCommand(options).run(testContext.get());
    }

    @Test
    public void testRemove() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();
        String path = NodeRef.appendChild(TestData.pointsType.getTypeName(),
                TestData.point1.getID());
        ParameterSet options = TestParams.of("path", path, "transactionId",
                transaction.getTransactionId().toString());
        buildCommand(options).run(testContext.get());

        RevTree index = transaction.stagingArea().getTree();

        Optional<NodeRef> node = transaction.command(FindTreeChild.class).setParent(index)
                .setChildPath(path).call();
        assertFalse(node.isPresent());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        assertEquals(path, response.getString("Deleted"));
    }

    @Test
    public void testRemoveRecursive() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.loadDefaultData();

        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();
        ParameterSet options = TestParams.of("path", TestData.pointsType.getTypeName(), "recursive",
                "true", "transactionId", transaction.getTransactionId().toString());
        buildCommand(options).run(testContext.get());

        RevTree index = transaction.stagingArea().getTree();

        Optional<NodeRef> node = transaction.command(FindTreeChild.class).setParent(index)
                .setChildPath(TestData.pointsType.getTypeName()).call();
        assertFalse(node.isPresent());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        assertEquals(TestData.pointsType.getTypeName(), response.getString("Deleted"));
    }

}
