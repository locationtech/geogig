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

import java.util.UUID;

import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.locationtech.geogig.plumbing.TransactionBegin;
import org.locationtech.geogig.repository.GeogigTransaction;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.TestParams;
import org.locationtech.geogig.web.api.WebAPICommand;

public class BeginTransactionTest extends AbstractWebOpTest {

    @Override
    protected String getRoute() {
        return "beginTransaction";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return BeginTransaction.class;
    }

    @Override
    protected boolean requiresTransaction() {
        return false;
    }

    @Test
    public void testBeginTransaction() throws Exception {
        ParameterSet options = TestParams.of();
        WebAPICommand cmd = buildCommand(options);

        cmd.run(testContext.get());

        JSONObject response = getJSONResponse().getJSONObject("response");
        JSONObject transaction = response.getJSONObject("Transaction");
        UUID.fromString(transaction.getString("ID"));
    }

    @Test
    public void testBeginTransactionWithinTransaction() throws Exception {
        Repository geogig = testContext.get().getRepository();
        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();
        ParameterSet options = TestParams.of("transactionId",
                transaction.getTransactionId().toString());
        WebAPICommand cmd = buildCommand(options);

        ex.expect(CommandSpecException.class);
        ex.expectMessage("Tried to start a transaction within a transaction.");
        cmd.run(testContext.get());
    }

}
