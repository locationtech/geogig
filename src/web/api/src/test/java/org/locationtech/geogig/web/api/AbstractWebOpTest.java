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

import java.io.StringWriter;
import java.util.UUID;

import javax.xml.stream.XMLStreamWriter;

import org.codehaus.jettison.json.JSONObject;
import org.codehaus.jettison.mapped.MappedNamespaceConvention;
import org.codehaus.jettison.mapped.MappedXMLStreamWriter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandBuilder;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;
import org.locationtech.geogig.web.api.WebAPICommand;

import com.google.common.base.Throwables;

public abstract class AbstractWebOpTest {
    @Rule
    public TestContext context = new TestContext();

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

    protected WebAPICommand buildCommand(ParameterSet options) {
        return CommandBuilder.build(getRoute(), options);
    }

    public JSONObject getResponse() {
        JSONObject response = null;
        try {
            StringWriter writer = new StringWriter();
            XMLStreamWriter xmlwriter = new MappedXMLStreamWriter(new MappedNamespaceConvention(),
                    writer);
            context.getCommandResponse().write(new ResponseWriter(xmlwriter));
            response = new JSONObject(writer.getBuffer().toString()).getJSONObject("response");
        } catch (Exception e) {
            Throwables.propagate(e);
        }
        return response;
    }

}
