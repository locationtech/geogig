/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.spring.controller;

import static org.locationtech.geogig.rest.repository.RepositoryProvider.BASE_REPOSITORY_ROUTE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_XML_VALUE;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.spring.dto.RepositoryList;
import org.locationtech.geogig.spring.service.RepositoryListService;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.base.Optional;

/**
 * Controller for Repository List related endpoints.
 * <p>
 * <pre>
 * /repos
 * </pre>
 */
@RestController
@RequestMapping(path = "/" + BASE_REPOSITORY_ROUTE,
        produces = {APPLICATION_XML_VALUE, APPLICATION_JSON_VALUE})
public class RepositoryListController extends AbstractController {

    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryListController.class);

    @Autowired
    private RepositoryListService repositoryListService;

    @GetMapping(params = API_V2)
    public RepositoryList getRepositoryListV2(final HttpServletRequest request) {
        // build the List
        RepositoryList list = extractRepoList(request);
        return list;
    }

    @GetMapping()
    public void getRepositoryList(HttpServletRequest request, HttpServletResponse response) {
        // build the List
        RepositoryList list = extractRepoList(request);
        // encode using legacy output format
        encode(list, request, response);
    }

    private RepositoryList extractRepoList(final HttpServletRequest request) {
        // get the repositoryProvider form the request
        Optional<RepositoryProvider> repoProvider = getRepoProvider(request);
        if (repoProvider.isPresent()) {
            return repositoryListService.getRepositoryList(repoProvider.get(),
                    getMediaType(request), getBaseUrl(request) + "/" + BASE_REPOSITORY_ROUTE);
        }
        throw new CommandSpecException("No Repository Provider found in request",
                HttpStatus.BAD_REQUEST);
    }

    @Override
    protected Logger getLogger() {
        return RepositoryListController.LOGGER;
    }
}
