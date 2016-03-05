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

import java.util.UUID;

import org.junit.Test;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.TestParams;
import org.locationtech.geogig.web.api.WebAPICommand;

public class AddWebOpTest extends AbstractWebOpTest {

    @Override
    protected String getRoute() {
        return "add";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return AddWebOp.class;
    }

    @Test
    public void testBuildParameters() {
        ParameterSet options = TestParams.of("path", "points", "transactionId",
                UUID.randomUUID().toString());

        AddWebOp op = (AddWebOp) buildCommand(options);
        assertEquals("points", op.path);
    }

    @Test
    public void testRequireTransaction() {
        ParameterSet options = TestParams.of("path", "points");
        WebAPICommand cmd = buildCommand(options);

        ex.expect(CommandSpecException.class);
        ex.expectMessage("No transaction was specified");
        cmd.run(context.get());
    }
}
