/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.web.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.UUID;

import javax.json.Json;
import javax.json.JsonObject;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.rest.repository.TestParams;
import org.locationtech.geogig.spring.dto.LegacyResponse;
import org.springframework.http.MediaType;

public abstract class AbstractWebOpTest {
    @Rule
    public TestContext testContext = new TestContext();

    @Rule
    public ExpectedException ex = ExpectedException.none();

    protected abstract String getRoute();

    protected abstract Class<? extends AbstractWebAPICommand> getCommandClass();

    protected boolean requiresRepository() {
        return true;
    }

    protected boolean requiresTransaction() {
        return true;
    }

    @Test
    public void testSPI() {
        WebAPICommand cmd = (AbstractWebAPICommand) buildCommand(null);
        assertTrue(getCommandClass().isInstance(cmd));
    }

    @Test
    public void testBuildTxId() {
        UUID txId = UUID.randomUUID();
        AbstractWebAPICommand testCommand = new AbstractWebAPICommand() {

            @Override
            protected void setParametersInternal(ParameterSet options) {
                // do nothing
            }

            @Override
            protected void runInternal(CommandContext context) {
                // do nothing
            }

        };
        ParameterSet options = TestParams.of("transactionId", txId.toString());
        testCommand.setParameters(options);
        assertEquals(txId, testCommand.getTransactionId());
    }

    @Test
    public void testRequireRepository() {
        if (requiresRepository()) {
            testContext.createUninitializedRepo();
            WebAPICommand cmd = buildCommand(null);

            ex.expect(CommandSpecException.class);
            ex.expectMessage("Repository not found.");
            cmd.run(testContext.get());
        }
    }

    @Test
    public void testRequireTransaction() {
        if (requiresTransaction()) {
            WebAPICommand cmd = buildCommand(null);

            ex.expect(CommandSpecException.class);
            ex.expectMessage("No transaction was specified");
            cmd.run(testContext.get());
        }
    }

    @SuppressWarnings("unchecked")
    protected <T extends AbstractWebAPICommand> T buildCommand(ParameterSet options) {
        T command = (T) CommandBuilder.build(getRoute());
        if (options != null) {
            command.setParameters(options);
        }
        return command;
    }

    public JsonObject getJSONResponse() {
        JsonObject response = null;

        LegacyResponse commandResponse = testContext.getCommandResponse();
        StringWriter writer = new StringWriter();
        commandResponse.encode(writer, MediaType.APPLICATION_JSON, "/geogig");

        String content = writer.toString();
        response = Json.createReader(new StringReader(content)).readObject();

        return response;
    }

}
