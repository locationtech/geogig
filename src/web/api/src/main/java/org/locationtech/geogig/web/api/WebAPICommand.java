/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.web.api;

import org.springframework.web.bind.annotation.RequestMethod;

/**
 * A basic interface from which all Web API commands will be derived.
 */
public interface WebAPICommand {

    /**
     * Runs the command with the given {@link CommandContext}.
     * 
     * @param context the context for this command
     */
    public void run(CommandContext context);

    public void setParameters(ParameterSet options);

    boolean supports(RequestMethod method);

}
