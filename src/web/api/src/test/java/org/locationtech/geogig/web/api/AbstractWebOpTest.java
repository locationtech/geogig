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

import java.io.ByteArrayOutputStream;
import java.util.UUID;

import org.codehaus.jettison.json.JSONObject;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.rest.Variants;
import org.restlet.data.MediaType;
import org.restlet.resource.Representation;

import com.google.common.base.Throwables;

public abstract class AbstractWebOpTest {
    @Rule
    public TestContext testContext = new TestContext();

    @Rule
    public ExpectedException ex = ExpectedException.none();

    protected abstract String getRoute();

    protected abstract Class<? extends AbstractWebAPICommand> getCommandClass();

    @Test
    public void testSPI() {
        ParameterSet options = TestParams.of();
        WebAPICommand cmd = CommandBuilder.build(getRoute(), options);
        assertTrue(getCommandClass().isInstance(cmd));
    }

    @Test
    public void testBuildTxId() {
        UUID txId = UUID.randomUUID();
        ParameterSet options = TestParams.of("transactionId", txId.toString());
        AbstractWebAPICommand cmd = (AbstractWebAPICommand) buildCommand(options);
        assertEquals(txId, cmd.getTransactionId());
    }

    protected <T extends WebAPICommand> T buildCommand(@Nullable String... optionsKvp) {
        return buildCommand(TestParams.of(optionsKvp));
    }

    @SuppressWarnings("unchecked")
    protected <T extends WebAPICommand> T buildCommand(ParameterSet options) {
        return (T) CommandBuilder.build(getRoute(), options);
    }

    public Representation getResponseRepresentation(MediaType mediaType) {
        Representation representation = testContext.getRepresentation(mediaType);
        return representation;
    }

    public JSONObject getJSONResponse() {
        JSONObject response = null;
        try {
            Representation representation = getResponseRepresentation(Variants.JSON.getMediaType());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            representation.write(out);

            String content = out.toString();
            response = new JSONObject(content);
        } catch (Exception e) {
            Throwables.propagate(e);
        }
        return response;
    }

}
