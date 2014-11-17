/*******************************************************************************
 * Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.web.api;

/**
 * A basic interface from which all Web API commands will be derived.
 */
public interface WebAPICommand {

    /**
     * Runs the command with the given {@link CommandContext}.
     * 
     * @param context the context for this command
     */
    void run(CommandContext context);

}
