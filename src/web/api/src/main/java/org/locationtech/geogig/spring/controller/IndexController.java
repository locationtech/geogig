/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.spring.controller;

import static org.locationtech.geogig.rest.repository.RepositoryProvider.BASE_REPOSITORY_ROUTE;
import static org.locationtech.geogig.rest.repository.RepositoryProvider.GEOGIG_ROUTE_PREFIX;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_XML_VALUE;

import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.index.IndexCommandBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for handing index commands.
 */
@RestController
@RequestMapping(path = GEOGIG_ROUTE_PREFIX + "/" + BASE_REPOSITORY_ROUTE
        + "/{repoName}/index",
        produces = {APPLICATION_XML_VALUE, APPLICATION_JSON_VALUE})
public class IndexController extends RepositoryCommandController {

    private static final Logger LOGGER = LoggerFactory.getLogger(IndexController.class);

    @Override
    protected AbstractWebAPICommand buildCommand(String commandName) {
        return IndexCommandBuilder.build(commandName);
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }
}
