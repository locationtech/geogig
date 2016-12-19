/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli;

import com.beust.jcommander.JCommander;

/**
 * Interface for cli command extensions, allowing to provide their own configured {@link JCommander}
 * , and hence support command line extensions (a'la git-svn, for example
 * {@code geogig pg <command> <args>...}).
 */
public interface CLICommandExtension {

    /**
     * @return the JCommander parser for this extension
     * @see JCommander
     */
    public JCommander getCommandParser();
}
