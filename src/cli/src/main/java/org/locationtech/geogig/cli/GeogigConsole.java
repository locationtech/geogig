/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.cli;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jline.Terminal;
import jline.UnsupportedTerminal;
import jline.console.ConsoleReader;
import jline.console.completer.AggregateCompleter;
import jline.console.completer.ArgumentCompleter;
import jline.console.completer.Completer;
import jline.console.completer.StringsCompleter;

import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.SymRef;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.plumbing.ResolveGeogigDir;
import org.locationtech.geogig.repository.Hints;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterDescription;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;

/**
 * Provides the ability to execute several commands in succession without re-initializing GeoGig or
 * the command line interface.
 */
public class GeogigConsole {

    private boolean interactive;

    /**
     * Entry point for the Geogig console.
     * 
     * @param args unused
     */
    public static void main(String... args) {
        Logging.tryConfigureLogging();
        try {
            if (args.length == 1) {
                new GeogigConsole().runFile(args[0]);
            } else if (args.length == 0) {
                new GeogigConsole().run();
            } else {
                System.out.println("Too many arguments.\nUsage: geogig-console [batch_file]");
            }
            System.exit(0);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    private void runFile(String filename) throws IOException {
        final File file = new File(filename);
        if (!file.exists()) {
            System.out.println("The specified batch file does not exist");
            return;
        }

        // take the input from the console with its input stream directly from the file
        InputStream in = new FileInputStream(file);
        try {
            interactive = false;
            run(in, System.out);
        } finally {
            in.close();
        }
    }

    /**
     * @throws IOException
     * 
     */
    private void run() throws IOException {
        // interactive will be false if stdin/stdout is redirected
        interactive = null != System.console();
        run(System.in, System.out);
    }

    private void run(final InputStream in, final OutputStream out) throws IOException {

        final Terminal terminal;
        if (interactive) {
            terminal = null;/* let jline select an appropriate one */
        } else {
            // no colors in output
            terminal = new UnsupportedTerminal();
        }
        ConsoleReader consoleReader = new ConsoleReader(in, out, terminal);
        consoleReader.setAutoprintThreshold(20);
        consoleReader.setPaginationEnabled(interactive);
        consoleReader.setHistoryEnabled(interactive);
        // needed for CTRL+C not to let the console broken
        consoleReader.getTerminal().setEchoEnabled(interactive);

        final GeogigCLI cli = new GeogigCLI(consoleReader);
        if (interactive) {
            addCommandCompleter(consoleReader, cli);
        } else {
            // no progress percent in output
            cli.disableProgressListener();
        }

        GeogigCLI.addShutdownHook(cli);

        setPrompt(cli);
        cli.close();

        try {
            runInternal(cli);
        } finally {
            try {
                cli.close();
            } finally {
                try {
                    if (terminal != null) {
                        terminal.restore();
                    }
                    consoleReader.shutdown();
                } catch (Exception e) {
                    throw Throwables.propagate(e);
                }
            }
        }
    }

    private void addCommandCompleter(ConsoleReader consoleReader, final GeogigCLI cli) {
        final JCommander globalCommandParser = cli.newCommandParser();

        final Map<String, JCommander> commands = globalCommandParser.getCommands();

        List<Completer> completers = new ArrayList<Completer>(commands.size());
        for (Map.Entry<String, JCommander> entry : commands.entrySet()) {

            String commandName = entry.getKey();
            JCommander commandParser = entry.getValue();

            List<ParameterDescription> parameters = commandParser.getParameters();
            List<String> options = new ArrayList<String>(parameters.size());
            for (ParameterDescription pd : parameters) {
                String longestName = pd.getLongestName();
                options.add(longestName);
            }
            Collections.sort(options);

            ArgumentCompleter commandCompleter = new ArgumentCompleter(new StringsCompleter(
                    commandName), new StringsCompleter(options));
            completers.add(commandCompleter);
        }

        completers.add(new StringsCompleter("exit", "clear"));

        Completer completer = new AggregateCompleter(completers);
        consoleReader.addCompleter(completer);
    }

    /**
     * Sets the command prompt
     * 
     * @throws IOException
     */
    private void setPrompt(GeogigCLI cli) throws IOException {
        if (!interactive) {
            return;
        }
        String currentDir = new File(".").getCanonicalPath();
        String currentHead = "";
        GeoGIG geogig;
        try {
            geogig = cli.newGeoGIG(Hints.readOnly());
        } catch (Exception e) {
            geogig = null;
        }
        if (geogig != null) {
            Optional<URL> dir = geogig.command(ResolveGeogigDir.class).call();
            if (dir.isPresent()) {
                try {
                    Optional<Ref> ref = geogig.command(RefParse.class).setName(Ref.HEAD).call();
                    if (ref.isPresent()) {
                        if (ref.get() instanceof SymRef) {
                            currentHead = ((SymRef) ref.get()).getTarget();
                            int idx = currentHead.lastIndexOf("/");
                            if (idx != -1) {
                                currentHead = currentHead.substring(idx + 1);
                            }
                        } else {
                            currentHead = ref.get().getObjectId().toString().substring(0, 7);
                        }
                        currentHead = " (" + currentHead + ")";
                    }
                } finally {
                    geogig.close();
                }
            }
        }
        String prompt = "(geogig):" + currentDir + currentHead + " $ ";
        cli.getConsole().setPrompt(prompt);
    }

    private void runInternal(final GeogigCLI cli) throws IOException {

        final ConsoleReader consoleReader = cli.getConsole();
        while (true) {
            String line = consoleReader.readLine();
            if (line == null) {
                // EOF / CTRL-D
                return;
            }
            if (line.trim().length() == 0) {
                continue;
            }
            if (line.trim().startsWith("#")) {// comment
                continue;
            }

            String[] args = ArgumentTokenizer.tokenize(line);

            if (interactive && args != null && args.length == 1) {
                if ("exit".equals(args[0])) {
                    return;
                }
                if ("clear".equals(args[0])) {
                    consoleReader.clearScreen();
                    consoleReader.redrawLine();
                    continue;
                }
            }

            cli.execute(args);
            setPrompt(cli);// in case HEAD has changed
            cli.close();
        }
    }

}
