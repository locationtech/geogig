/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.spring.main;

import java.io.IOException;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * Embedded Jetty server to expose the GeoGig Web API via Spring MVC. This class replaces the old
 * Web App Main class, using Jetty and Spring MVC, instead of Restlet, to provide the GeoGig Web
 * API.
 */
public class JettyServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(JettyServer.class);

    private RepositoryProvider repoProvider;

    private final Server server;

    public JettyServer(final int port) {
        this(port, null);
    }

    public JettyServer(final int port, RepositoryProvider repoProvider) {
        super();
        this.repoProvider = repoProvider;
        this.server = new Server(port);
    }

    /**
     * Starts the embedded server and blocks until it stops.
     *
     * @throws Exception
     */
    public void start() throws Exception {
        start(false);
    }

    /**
     * Starts the embedded server.
     * 
     * @param async if {@code true} this function will return after starting the server, otherwise
     *        it will block until the server stops
     *
     * @throws Exception
     */
    public void start(boolean async) throws Exception {
        server.setHandler(getServletContextHandler(getContext(), repoProvider));
        server.start();
        //server.dumpStdErr();
        if (!async) {
            server.join();
        }
    }

    public void stop() throws Exception {
        server.stop();
    }

    public Server getServer() {
        return this.server;
    }
    /**
     * Creates a Web Application Context that uses Spring annotations for configuration.
     *
     * @return A Spring annotations enabled Web Context.
     */
    private static WebApplicationContext getContext() {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setConfigLocation("org.locationtech.geogig.spring.config");
        return context;
    }

    /**
     * Creates a Jetter Handler associated with a Web Context.
     *
     * @param context The Web Application Context to associate with the Handler.
     *
     * @return A Handler that can handle Web Requests to this Jetty Server.
     */
    private static Handler getServletContextHandler(WebApplicationContext context,
            RepositoryProvider repoProvider) {
        ServletContextHandler contextHandler = new ServletContextHandler();
        // gzip handler
        GzipHandler gzipHandler = new GzipHandler();
        gzipHandler.addIncludedMimeTypes(
                MediaType.APPLICATION_JSON_VALUE,
                MediaType.APPLICATION_XML_VALUE,
                MediaType.TEXT_XML_VALUE);
        contextHandler.setGzipHandler(gzipHandler);
        //contextHandler.setErrorHandler(null);
        contextHandler.setContextPath("/");
        ServletHolder servletHolder = new ServletHolder(new DispatcherServlet(context));
        // configure the Servlet with Multipart support, just use defaults for now
        servletHolder.getRegistration().setMultipartConfig(new MultipartConfigElement((String)null));
        contextHandler.addServlet(servletHolder, "/");
        contextHandler.addEventListener(new ContextLoaderListener(context));
        // wrap it with our RequestHandler that inserts the repoProvider into the request attributes
        return new RequestHandlerWrapper(contextHandler, repoProvider);
    }

    public static void main(String[] args) {
        if (args.length == 1) {
            try {
                int port = Integer.parseInt(args[0]);
                if (1024 < port && port < 65535) {
                    try {
                        (new JettyServer(port)).start();
                    } catch (Exception ex) {
                        LOGGER.error("Error starting server", ex);
                    }
                } else {
                    LOGGER.error("Invalid port: " + port);
                }
            } catch (Exception ex) {
                LOGGER.error("Invalid port: " + args[0], ex);
            }
        } else {
            // try default of 8182
            try {
                (new JettyServer(8182)).start();
            } catch (Exception ex) {
                LOGGER.error("Error starting server", ex);
            }
        }

    }

    private static class RequestHandlerWrapper extends HandlerWrapper {

        private final RepositoryProvider repoProvider;

        private RequestHandlerWrapper(ServletContextHandler parent,
                RepositoryProvider repoProvider) {
            super();
            setDelegate(parent);
            this.repoProvider = repoProvider;
        }

        private void setDelegate(ServletContextHandler delegate) {
            this.setHandler(delegate);
        }

        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request,
                HttpServletResponse response) throws
                IOException, ServletException {
            // add the repository provide to the request attributes
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Setting RepositoryProvider on Request to: " + repoProvider);
            }
            baseRequest.setAttribute(RepositoryProvider.KEY, repoProvider);
            // now let the delegate handle it
            super.handle(target, baseRequest, request, response);
        }

    }
}
