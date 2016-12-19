/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Michael Fawcett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.cli;

import java.util.ServiceLoader;

import com.google.inject.Module;

/**
 * Marker interface for modules that provide extra CLI commands.
 * 
 * <p>
 * The {@link GeogigCLI CLI} app uses the standard {@link ServiceLoader}
 * "Service Provider Interface" mechanism to look for implementations of this interface on the
 * classpath.
 * <p>
 * Any CLI plugin that provides extra command line commands shall include a file named
 * {@code org.locationtech.geogig.cli.CLIModule} text file inside the jar's {@code META-INF/services} folder,
 * whose content is the full qualified class name of the module implementation. There can be more
 * than one module declared on each file, separated by a newline.
 */
public interface CLIModule extends Module {

}
