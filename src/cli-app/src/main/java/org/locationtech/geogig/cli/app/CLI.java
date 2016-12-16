/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - extract main() method from GeoGigCLI
 */
package org.locationtech.geogig.cli.app;

import org.locationtech.geogig.cli.CLIContextBuilder;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.porcelain.ConfigOp;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigScope;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.repository.impl.GlobalContextBuilder;

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

        GeoGIG geogig = cli.getGeogig();
        boolean disableAnsi = false;
        boolean closeIt = geogig == null;
        if (closeIt) {
            // we're not in a repository, need a geogig anyways to check the global config
            geogig = cli.newGeoGIG(Hints.readOnly());
            if (geogig.command(ConfigOp.class).setScope(ConfigScope.GLOBAL)
                    .setAction(ConfigAction.CONFIG_GET).setName("ansi.enabled").toString()
                    .equalsIgnoreCase("false")) {
                disableAnsi = true;
            } else if (geogig.command(ConfigOp.class).setScope(ConfigScope.GLOBAL)
                    .setAction(ConfigAction.CONFIG_GET).setName("ansi.enabled").toString()
                    .equalsIgnoreCase("true")) {
                disableAnsi = false;
            }
            geogig.close();
        } else {
            if (geogig.getRepository().configDatabase().get("ansi.enabled").or("")
                    .equalsIgnoreCase("false")) {
                cli.getConsole().setForceAnsi(false);
                disableAnsi = true;
            } else if (geogig.getRepository().configDatabase().get("ansi.enabled").or("")
                    .equalsIgnoreCase("true")) {
                disableAnsi = false;
                cli.getConsole().setForceAnsi(true);
            }
        }
        if (disableAnsi) {
            cli.getConsole().disableAnsi();
        } else {
            cli.getConsole().enableAnsi();
        }

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
