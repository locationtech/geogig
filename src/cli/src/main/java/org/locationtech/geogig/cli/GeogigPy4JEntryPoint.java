/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.cli;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.NoSuchElementException;

import jline.console.ConsoleReader;

import org.locationtech.geogig.api.DefaultPlatform;
import org.locationtech.geogig.api.DefaultProgressListener;
import org.locationtech.geogig.api.ProgressListener;

import py4j.GatewayServer;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;

/**
 * Provides an entry point using the py4j library, to expose GeoGig functionality to python
 * applications
 */
public class GeogigPy4JEntryPoint {

    ConsoleReader consoleReader;

    /**
     * A class to parse and store console output of GeoGig commands
     */
    public class ToStringOutputStream extends OutputStream {

        private static final int PAGE_SIZE = 1000;

        StringBuffer sb = new StringBuffer();

        @Override
        public void write(int b) throws IOException {
            char c = (char) b;
            String s = String.valueOf(c);
            sb.append(s);
        }

        public void clear() {
            sb = new StringBuffer();
        }

        public String asString() {
            return sb.toString();
        }

        public Iterator<String> getIterator() {
            Splitter page = Splitter.fixedLength(PAGE_SIZE);
            return page.split(sb.toString()).iterator();
        }

    }

    private PrintStream stream;

    private ToStringOutputStream os;

    private Iterator<String> pages = null;

    private GeoGigPy4JProgressListener listener;

    public GeogigPy4JEntryPoint() {
        listener = new SilentProgressListener();
        os = new ToStringOutputStream();
        stream = new PrintStream(os);
        try {
            consoleReader = new ConsoleReader(System.in, stream);
            consoleReader.getTerminal().setEchoEnabled(true);
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
        System.out.print("Running command: " + command);
        int ret = cli.execute(args);
        cli.close();
        if (ret == 0) {
            System.out.println(" [OK]");
        } else {
            System.out.println(" [Error]");
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
        if (args.length != 0) {
            if (args.length > 1) {
                System.out.println("Too many arguments.\n Usage: geogig-gateway [port]");
                return;
            }
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Wrong argument: " + args[0] + "\nUsage: geogig-gateway [port]");
                return;
            }
        }
        GatewayServer gatewayServer = new GatewayServer(new GeogigPy4JEntryPoint(), port);
        gatewayServer.start();
        System.out.println("GeoGig server correctly started and waiting for conections at port "
                + Integer.toString(port));
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