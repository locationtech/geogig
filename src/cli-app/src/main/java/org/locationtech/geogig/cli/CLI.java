/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - extract main() method from GeoGigCLI
 */
package org.locationtech.geogig.cli;

import org.locationtech.geogig.repository.GlobalContextBuilder;

public class CLI {
    /**
     * Entry point for the command line interface.
     * 
     * @param args
     */
    public static void main(String[] args) {
        String repoURI = null;
        if (args.length > 1 && "--repo".equals(args[0])) {
            repoURI = args[1];
            String[] norepoArgs = new String[args.length - 2];
            if (args.length > 2) {
                System.arraycopy(args, 2, norepoArgs, 0, args.length - 2);
            }
            args = norepoArgs;
        }

        GlobalContextBuilder.builder(new CLIContextBuilder());
        Logging.tryConfigureLogging();
        Console consoleReader = new Console();

        final GeogigCLI cli = new GeogigCLI(consoleReader);
        cli.setRepositoryURI(repoURI);

        addShutdownHook(cli);
        int exitCode = cli.execute(args);

        cli.close();

        if (exitCode != 0 || cli.isExitOnFinish()) {
            System.exit(exitCode);
        }
    }

    static void addShutdownHook(final GeogigCLI cli) {
        // try to grafefully shutdown upon CTRL+C
        Runtime.getRuntime().addShutdownHook(new Thread() {

            private GeogigCLI geogig = cli;

            @Override
            public void run() {
                if (cli.isRunning()) {
                    System.err.println("Forced shut down, wait for geogig to be closed...");
                    System.err.flush();
                    geogig.close();
                    System.err.println("geogig closed.");
                    System.err.flush();
                }
            }
        });
    }

}
