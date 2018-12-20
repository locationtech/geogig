/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.app;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.repository.DefaultPlatform;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import com.google.common.base.Optional;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.google.common.io.Resources;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;

/**
 * Utility class for the CLI applications to configure logging using the default logback logger
 * context.
 * <p>
 * {@link #tryConfigureLogging()} is meant to be called by the {@code static main(String [])}
 * methods or such places where it wouldn't interfere with any alternate logging mechanism a client
 * application may be using (e.g. geoserver using log4j instead).
 */
class Logging {
    private static final Logger LOGGER = LoggerFactory.getLogger(Logging.class);

    private static File geogigDirLoggingConfiguration;

    static void tryConfigureLogging() {
        tryConfigureLogging(new DefaultPlatform(), null);
    }

    static void tryConfigureLogging(Platform platform, @Nullable final String repoURI) {
        System.setProperty("hsqldb.reconfig_logging", "false"); // stop hsql from reseting logging
        // instantiate and call ResolveGeogigDir directly to avoid calling getGeogig() and hence get
        // some logging events before having configured logging
        Hints hints = new Hints();
        try {
            if (repoURI != null) {
                URI uri = new URI(repoURI);
                hints.set(Hints.REPOSITORY_URL, uri);
            }
        } catch (URISyntaxException ignore) {
            // not our call to do anything with a wrong URI here
        }
        final Optional<URI> geogigDirUrl = new ResolveGeogigURI(platform, hints).call();
        if (!geogigDirUrl.isPresent() || !"file".equalsIgnoreCase(geogigDirUrl.get().getScheme())) {
            // redirect java.util.logging to SLF4J anyways
            SLF4JBridgeHandler.removeHandlersForRootLogger();
            SLF4JBridgeHandler.install();
            return;
        }

        final File geogigDir = new File(geogigDirUrl.get());

        if (repoURI == null && geogigDir.equals(geogigDirLoggingConfiguration)) {
            return;
        }

        if (!geogigDir.exists() || !geogigDir.isDirectory()) {
            return;
        }
        final URL loggingFile = getOrCreateLoggingConfigFile(geogigDir);

        if (loggingFile == null) {
            return;
        }

        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            loggerContext.reset();
            /*
             * Set the geogigdir variable for the config file can resolve the default location
             * ${geogigdir}/log/geogig.log
             */
            loggerContext.putProperty("geogigdir", geogigDir.getAbsolutePath());
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(loggerContext);
            configurator.doConfigure(loggingFile);

            // redirect java.util.logging to SLF4J
            SLF4JBridgeHandler.removeHandlersForRootLogger();
            SLF4JBridgeHandler.install();
            geogigDirLoggingConfiguration = geogigDir;
        } catch (JoranException e) {
            LOGGER.error("Error configuring logging from file {}. '{}'", loggingFile,
                    e.getMessage(), e);
        }
    }

    @Nullable
    private static URL getOrCreateLoggingConfigFile(final File geogigdir) {

        final File logsDir = new File(geogigdir, "log");
        if (!logsDir.exists() && !logsDir.mkdir()) {
            return null;
        }
        final File configFile = new File(logsDir, "logback.xml");
        if (configFile.exists()) {
            try {
                return configFile.toURI().toURL();
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }
        ByteSource from;
        final URL resource = CLI.class.getResource("logback_default.xml");
        try {
            from = Resources.asByteSource(resource);
        } catch (NullPointerException npe) {
            LOGGER.warn("Couldn't obtain default logging configuration file");
            return null;
        }
        try {
            from.copyTo(Files.asByteSink(configFile));
            return configFile.toURI().toURL();
        } catch (Exception e) {
            LOGGER.warn("Error copying logback_default.xml to {}. Using default configuration.",
                    configFile, e);
            return resource;
        }
    }

}
