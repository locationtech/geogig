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

import static java.lang.String.format;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.DefaultPlatform;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.impl.FileRepositoryResolver;
import org.locationtech.geogig.repository.impl.GlobalContextBuilder;
import org.locationtech.geogig.repository.impl.PluginsContextBuilder;
import org.locationtech.geogig.storage.ConfigDatabase;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;

public class CLI {

    public int run(InputStream stdin, PrintStream stdout, String[] args) throws IOException {
        final Platform platform = new DefaultPlatform();
        final Console console = new Console(stdin, stdout);
        final @Nullable String repoURI;
        final String[] cliArgs;
        {
            List<String> arglist = Lists.newArrayList(args);
            if (arglist.indexOf("--repo") > -1) {
                int indexOfRepoArg = arglist.indexOf("--repo");
                int indexOfRepoValue = indexOfRepoArg + 1;
                if (arglist.size() <= indexOfRepoValue) {
                    console.println("--repo argument value missing");
                    return -1;
                }
                repoURI = arglist.get(indexOfRepoValue);
                arglist.remove(indexOfRepoValue);
                arglist.remove(indexOfRepoArg);
            } else {
                Optional<URI> geogigDirUrl = new ResolveGeogigURI(platform, null).call();
                repoURI = geogigDirUrl.isPresent() ? geogigDirUrl.get().toString() : null;
            }
            cliArgs = arglist.toArray(new String[arglist.size()]);
        }

        GlobalContextBuilder.builder(new PluginsContextBuilder());
        Logging.tryConfigureLogging(platform, repoURI);

        {// resolve the ansi.enabled global config without opening a full repo, but just the global
         // config database at $HOME/.geogigconfig
            Context context = GlobalContextBuilder.builder().build(Hints.readOnly());
            // only need the $HOME/.geogigconfig global config. Note given the control coupling
            // imposed by this argument, this works "by accident" just because we know
            // FileRepositoryResolver doesn't fail regardless of the argument value, but other
            // implementations might.
            final boolean resolveAsRootURI = false;
            try (ConfigDatabase config = FileRepositoryResolver
                    .resolveConfigDatabase(platform.pwd().toURI(), context, resolveAsRootURI)) {
                Optional<String> ansiEnabled = config.getGlobal("ansi.enabled");
                if (ansiEnabled.isPresent()) {
                    boolean enable = Boolean.getBoolean(ansiEnabled.get());
                    if (enable) {
                        console.enableAnsi();
                    } else {
                        console.disableAnsi();
                    }
                }
            } catch (IOException e) {
                console.println(format("Unable to obtain global config: " + e.getMessage()));
                System.exit(-1);
            }
        }

        final GeogigCLI cli = new GeogigCLI(console);
        cli.setRepositoryURI(repoURI);
        cli.setPlatform(platform);

        addShutdownHook(cli);

        int exitCode;
        if (cliArgs.length == 1 && "-".equals(cliArgs[0])) {
            exitCode = runFromStdIn(cli, stdin);
        } else {
            exitCode = cli.execute(cliArgs);
        }
        if (exitCode != 0 || cli.isExitOnFinish()) {
            cli.close();
        }

        return cli.isExitOnFinish() ? exitCode : Integer.MIN_VALUE;
    }

    @SuppressWarnings("resource")
    private int runFromStdIn(GeogigCLI cli, InputStream stdin) {
        final String QUOTE = "\"";
        boolean quoted = false;
        try (Scanner lines = new Scanner(stdin)) {
            while (lines.hasNextLine()) {
                String nextLine = lines.nextLine();
                Scanner line = new Scanner(nextLine);
                List<String> tokens = new ArrayList<>();
                String token = null;
                while (line.hasNext()) {
                    String curr = line.next();
                    boolean startQuote = !quoted && curr.startsWith(QUOTE);
                    if (startQuote) {
                        quoted = true;
                        curr = curr.substring(1);
                    }
                    boolean endQuote = quoted && curr.endsWith(QUOTE);
                    if (endQuote) {
                        curr = curr.substring(0, curr.length() - 1);
                        quoted = false;
                    }

                    token = token == null ? curr : (token + " " + curr);
                    if (!quoted) {
                        tokens.add(token);
                        token = null;
                    }
                }

                if (tokens.isEmpty()) {
                    continue;
                } else if (tokens.size() == 1 && "exit".equals(tokens.get(0))) {
                    break;
                }
                String[] args = tokens.toArray(new String[tokens.size()]);
                int retCode = cli.execute(args);
                if (0 != retCode) {
                    return retCode;
                }
            }
        }
        return 0;
    }

    /**
     * Entry point for the command line interface.
     * 
     * @param args
     */
    public static void main(String[] args) {

        int exitCode;
        try {
            exitCode = new CLI().run(System.in, System.out, args);
            if (exitCode != Integer.MIN_VALUE) {
                System.exit(exitCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
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
                    geogig.getProgressListener().cancel();
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    geogig.close();
                    System.err.println("geogig closed.");
                    System.err.flush();
                }
            }
        });
    }

}
