/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.app;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.model.impl.DefaultPlatform;
import org.locationtech.geogig.repository.DefaultProgressListener;
import org.locationtech.geogig.repository.ProgressListener;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;

import py4j.GatewayServer;

/**
 * Provides an entry point using the py4j library, to expose GeoGig functionality to python
 * applications
 */
public class GeogigPy4JEntryPoint {

    Console consoleReader;

    /**
     * A class to parse and store console output of GeoGig commands
     */
    public class ToStringOutputStream extends OutputStream {

        private static final int PAGE_SIZE = 1000;

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        @Override
        public void write(int b) throws IOException {
            bytes.write((byte)b);
        }

        public void clear() {
            bytes = new ByteArrayOutputStream();
        }

        public String asString() throws IOException {
            return bytes.toString("UTF-8");
        }

        public Iterator<String> getIterator() throws IOException {
            Splitter page = Splitter.fixedLength(PAGE_SIZE);
            String buffer = bytes.toString("UTF-8");
            return page.split(buffer).iterator();
        }

    }

    private PrintStream stream;

    private ToStringOutputStream os;

    private Iterator<String> pages = null;

    private GeoGigPy4JProgressListener listener;

    private boolean verbose = false;

    public GeogigPy4JEntryPoint(boolean verbose) {
        this.verbose = verbose;
        listener = new SilentProgressListener();
        os = new ToStringOutputStream();
        stream = new PrintStream(os);
        try {
            consoleReader = new Console(System.in, stream);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Runs a command on a given repository
     *
     * @param folder the repository folder
     * @param args the args to run, including the command itself and additional parameters
     * @return
     * @throws IOException
     */
    public int runCommand(String folder, String[] args) throws IOException {
        System.gc();
        GeogigCLI cli = new GeogigCLI(consoleReader) {
            @Override
            public synchronized ProgressListener getProgressListener() {
                if (super.progressListener == null) {

                    super.progressListener = new DefaultProgressListener() {
                        @Override
                        public void setDescription(String s) {
                            GeogigPy4JEntryPoint.this.listener.setProgressText(s);
                        }

                        @Override
                        public synchronized void setProgress(float percent) {
                            GeogigPy4JEntryPoint.this.listener.setProgress(percent);

                        }
                    };
                }
                return super.progressListener;
            }
        };
        DefaultPlatform platform = new DefaultPlatform();
        platform.setWorkingDir(new File(folder));
        cli.setPlatform(platform);
        String command = Joiner.on(" ").join(args);
        os.clear();
        pages = null;
        if (verbose) {
            String noPasswordCommand = command.replaceAll("--password \\S*", "[PASSWORD_HIDDEN]");
            System.out.print("Running command: " + noPasswordCommand);
        }
        int ret = cli.execute(args);
        cli.close();
        if (verbose) {
            if (ret == 0) {
                System.out.println(" [OK]");
            } else {
                System.out.println(" [Error]");
            }
        }
        stream.flush();
        os.flush();
        return ret;
    }

    public String nextOutputPage() throws IOException {
        if (pages == null) {
            pages = os.getIterator();
        }
        String next;
        try {
            next = pages.next();
        } catch (NoSuchElementException e) {
            next = null;
        }
        return next;
    }

    public boolean isGeoGigServer() {
        return true;
    }

    /**
     * Shutdowns the server
     */
    public void shutdown() {
        System.out.println("Shutting down GeoGig server.");
        System.exit(0);
    }

    /**
     * Sets the progress listener that will receive progress updates
     *
     * @param listener
     */
    public void setProgressListener(GeoGigPy4JProgressListener listener) {
        this.listener = listener;
    }

    /**
     * Removes the progress listener, if set
     */
    public void removeProgressListener() {
        this.listener = new SilentProgressListener();
    }

    public static void main(String[] args) {
        Logging.tryConfigureLogging();
        int port = GatewayServer.DEFAULT_PORT;
        boolean verbose = false;
        try {
            if (args.length == 0) {
            } else if (args.length == 1) {
                if (args[0].equals("-v")) {
                    verbose = true;
                } else {
                    port = Integer.parseInt(args[0]);
                }
            } else if (args.length == 2) {
                String portArgument = args[0];
                if (args[0].equals("-v")) {
                    verbose = true;
                    portArgument = args[1];
                } else if (args[1].equals("-v")) {
                    verbose = true;
                } else {
                    System.out.println("Wrong arguments\nUsage: geogig-gateway [port][-v]");
                    return;
                }
                System.out.println(portArgument);
                port = Integer.parseInt(portArgument);
            } else {
                System.out.println("Too many arguments.\n Usage: geogig-gateway [port] [-v]");
                return;
            }

            GatewayServer gatewayServer = new GatewayServer(new GeogigPy4JEntryPoint(verbose), port);
            gatewayServer.start();
            System.out
                    .println("GeoGig server correctly started and waiting for conections at port "
                            + Integer.toString(port));
        } catch (NumberFormatException e) {
            System.out.println("Wrong argument: " + port + "\nUsage: geogig-gateway [port][-v]");
            return;
        }
    }
}

class SilentProgressListener implements GeoGigPy4JProgressListener {

    @Override
    public void setProgress(float i) {

    }

    @Override
    public void setProgressText(String s) {

    }

}
