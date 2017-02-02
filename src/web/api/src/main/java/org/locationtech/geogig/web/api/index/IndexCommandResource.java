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

import org.locationtech.geogig.rest.repository.CommandResource;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.WebAPICommand;

public class IndexCommandResource extends CommandResource {

    @Override
    protected WebAPICommand buildCommand(String commandName, ParameterSet params) {
        return IndexCommandBuilder.build(commandName, params);
    }

}
