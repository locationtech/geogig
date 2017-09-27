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
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.TransactionBegin;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.GeogigTransaction;
import org.locationtech.geogig.rest.repository.TestParams;
import org.locationtech.geogig.test.TestData;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.ParameterSet;

public class CommitTest extends AbstractWebOpTest {

    @Override
    protected String getRoute() {
        return "commit";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return Commit.class;
    }

    @Test
    public void testBuildParameters() {
        ParameterSet options = TestParams.of("all", "true", "authorName", "Test Name",
                "authorEmail", "test@example.com", "message", "Commit message", "transactionId",
                UUID.randomUUID().toString());

        Commit op = (Commit) buildCommand(options);
        assertTrue(op.all);
        assertEquals("Test Name", op.authorName.get());
        assertEquals("test@example.com", op.authorEmail.get());
        assertEquals("Commit message", op.message);
    }

    @Test
    public void testCommit() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();

        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();
        testData.setTransaction(transaction);

        testData.insert(TestData.point1);
        testData.add();

        ParameterSet options = TestParams.of("all", "true", "authorName", "Test Name",
                "authorEmail", "test@example.com", "message", "Commit message", "transactionId",
                transaction.getTransactionId().toString());
        buildCommand(options).run(testContext.get());

        JsonObject response = getJSONResponse().getJsonObject("response");
        assertTrue(response.getBoolean("success"));
        Ref head = transaction.command(org.locationtech.geogig.plumbing.RefParse.class)
                .setName(Ref.HEAD).call().get();
        assertEquals(head.getObjectId().toString(), response.getString("commitId"));
        assertEquals(1, response.getInt("added"));
        assertEquals(0, response.getInt("changed"));
        assertEquals(0, response.getInt("deleted"));
    }
}
