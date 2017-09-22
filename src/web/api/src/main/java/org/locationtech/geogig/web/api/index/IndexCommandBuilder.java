/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.web.api.index;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.WebAPICommand;

/**
 * Builds {@link WebAPICommand}s by parsing a given command name and uses a given parameter set to
 * fill out their variables.
 */
public class IndexCommandBuilder {

    private final static Map<String, Supplier<AbstractWebAPICommand>> MAPPINGS =
            new HashMap<>(30);
    static {
        MAPPINGS.put("create", CreateIndex::new);
        MAPPINGS.put("update", UpdateIndex::new);
        MAPPINGS.put("rebuild", RebuildIndex::new);
        MAPPINGS.put("list", ListIndexes::new);
        MAPPINGS.put("drop", DropIndex::new);
    }

    /**
     * Builds the {@link WebAPICommand}.
     * 
     * @param commandName the name of the command
     * @param options the parameter set
     * @return the command that was built
     * @throws CommandSpecException
     */
    public static AbstractWebAPICommand build(final String commandName)
            throws CommandSpecException {

        if (!MAPPINGS.containsKey(commandName)) {
            throw new CommandSpecException("'" + commandName + "' is not a geogig command");
        }

        AbstractWebAPICommand command = MAPPINGS.get(commandName).get();

        return command;
    }

}
