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
import static org.locationtech.geogig.rest.repository.RepositoryProvider.GEOGIG_ROUTE_PREFIX;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_XML_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.DELETE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.OPTIONS;
import static org.springframework.web.bind.annotation.RequestMethod.PATCH;
import static org.springframework.web.bind.annotation.RequestMethod.POST;
import static org.springframework.web.bind.annotation.RequestMethod.PUT;
import static org.springframework.web.bind.annotation.RequestMethod.TRACE;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.spring.dto.InitRequest;
import org.locationtech.geogig.spring.dto.RepositoryInitRepo;
import org.locationtech.geogig.spring.service.RepositoryInitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Controller for handing Repository INIT requests.
 */
@RestController
@RequestMapping(path = GEOGIG_ROUTE_PREFIX + "/" + BASE_REPOSITORY_ROUTE
        + "/{repoName}/init",
        produces = {APPLICATION_XML_VALUE, APPLICATION_JSON_VALUE})
public class RepositoryInitController extends AbstractController {

    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryInitController.class);

    @Autowired
    private RepositoryInitService repositoryInitService;

    @RequestMapping(method = {GET, POST, DELETE, PATCH, TRACE, OPTIONS})
    public void catchAll() {
        // if we hit this controller, it's a 405
        supportedMethods(Sets.newHashSet(PUT.toString()));
    }

    @PutMapping
    public void initRepositoryNoBody(@PathVariable(name = "repoName") String repoName,
            HttpServletRequest request, HttpServletResponse response)
            throws RepositoryConnectionException {
        RepositoryInitRepo repo = initRepo(request, repoName);
        encode(repo, request, response);
    }

    @PutMapping(consumes = {APPLICATION_JSON_VALUE, APPLICATION_XML_VALUE})
    public void initRepositoryFromJsonOrXml(
            @PathVariable(name = "repoName")String repoName,
            @RequestBody InitRequest requestBody,
            HttpServletRequest request, HttpServletResponse response)
            throws RepositoryConnectionException {
        RepositoryInitRepo repo = initRepo(request, repoName, requestBody);
        encode(repo, request, response);
    }

    @PutMapping(consumes = {APPLICATION_FORM_URLENCODED_VALUE})
    public void initRepositoryFromForm(
            @PathVariable(name = "repoName")String repoName,
            @RequestBody MultiValueMap<String, String> requestBody,
            HttpServletRequest request, HttpServletResponse response)
            throws RepositoryConnectionException {
        RepositoryInitRepo repo = initRepo(request, repoName, requestBody);
        encode(repo, request, response);
    }

    private RepositoryInitRepo initRepo(HttpServletRequest request, String repoName)
            throws RepositoryConnectionException {
        Optional<RepositoryProvider> repoProvider = getRepoProvider(request);
        if (repoProvider.isPresent()) {
            return repositoryInitService.initRepository(repoProvider.get(), repoName,
                    Maps.newHashMap());
        } else {
            throw NO_PROVIDER;
        }
    }

    private RepositoryInitRepo initRepo(HttpServletRequest request, String repoName,
            InitRequest requestBody)
            throws RepositoryConnectionException {
        Optional<RepositoryProvider> repoProvider = getRepoProvider(request);
        if (repoProvider.isPresent()) {
            return repositoryInitService.initRepository(repoProvider.get(), repoName,
                    (requestBody == null) ? Maps.newHashMap() : requestBody.getParameters());
        } else {
            throw NO_PROVIDER;
        }
    }

    private RepositoryInitRepo initRepo(HttpServletRequest request, String repoName,
            MultiValueMap<String, String> requestBody)
            throws RepositoryConnectionException {
        Optional<RepositoryProvider> repoProvider = getRepoProvider(request);
        if (repoProvider.isPresent()) {
            return repositoryInitService.initRepository(repoProvider.get(), repoName,
                    (requestBody == null) ? Maps.newHashMap() : requestBody.toSingleValueMap());
        } else {
            throw NO_PROVIDER;
        }
    }

    // ---- API V2 methods ---- //
    @PutMapping(params = API_V2)
    public RepositoryInitRepo initRepositoryNoBody_v2(
            @PathVariable(name = "repoName") String repoName,
            HttpServletRequest request, HttpServletResponse response )
            throws RepositoryConnectionException {
        RepositoryInitRepo repo = initRepo(request, repoName);
        response.setStatus(HttpStatus.CREATED.value());
        return repo;
    }

    @PutMapping(params = API_V2, consumes = {APPLICATION_JSON_VALUE, APPLICATION_XML_VALUE})
    public RepositoryInitRepo initRepositoryFromJsonOrXml_v2(
            @PathVariable(name = "repoName")String repoName,
            @RequestBody InitRequest requestBody, HttpServletRequest request,
            HttpServletResponse response) throws RepositoryConnectionException {
        RepositoryInitRepo repo = initRepo(request, repoName, requestBody);
        response.setStatus(HttpStatus.CREATED.value());
        return repo;
    }

    @PutMapping(params = API_V2, consumes = {APPLICATION_FORM_URLENCODED_VALUE})
    public RepositoryInitRepo initRepositoryFromForm_v2(
            @PathVariable(name = "repoName")String repoName,
            @RequestBody MultiValueMap<String, String> requestBody,
            HttpServletRequest request, HttpServletResponse response)
            throws RepositoryConnectionException {
        RepositoryInitRepo repo = initRepo(request, repoName, requestBody);
        response.setStatus(HttpStatus.CREATED.value());
        return repo;
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }
}
