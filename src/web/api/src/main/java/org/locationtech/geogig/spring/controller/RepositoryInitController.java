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
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_XML_VALUE;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.spring.dto.InitRequest;
import org.locationtech.geogig.spring.dto.RepositoryInitRepo;
import org.locationtech.geogig.spring.service.RepositoryInitService;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Controller for handing Repository INIT requests.
 */
@RestController
@RequestMapping(path = "/" + BASE_REPOSITORY_ROUTE + "/{repoName}/init",
        produces = {APPLICATION_XML_VALUE, APPLICATION_JSON_VALUE})
public class RepositoryInitController extends AbstractController {

    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryInitController.class);

    @Autowired
    private RepositoryInitService repositoryInitService;

    @RequestMapping(method = {RequestMethod.GET, RequestMethod.DELETE, RequestMethod.POST,
            RequestMethod.PUT})
    public void initRepositoryNoBody(@PathVariable(name = "repoName") String repoName,
            HttpServletRequest request, HttpServletResponse response)
            throws RepositoryConnectionException {
        if (request.getMethod().equals(RequestMethod.PUT.toString())) {
            RepositoryInitRepo repo = initRepo(request, repoName);
            response.setStatus(HttpStatus.CREATED.value());
            encode(repo, request, response);
        } else {
            throw new CommandSpecException("The request method is unsupported for this operation.",
                    HttpStatus.METHOD_NOT_ALLOWED, Sets.newHashSet(RequestMethod.PUT.toString()));
        }
    }

    @RequestMapping(method = {RequestMethod.GET, RequestMethod.DELETE, RequestMethod.POST,
            RequestMethod.PUT}, consumes = {APPLICATION_JSON_VALUE, APPLICATION_XML_VALUE})
    public void initRepositoryFromJsonOrXml(
            @PathVariable(name = "repoName")String repoName,
            @RequestBody InitRequest requestBody,
            HttpServletRequest request, HttpServletResponse response)
            throws RepositoryConnectionException {
        if (request.getMethod().equals(RequestMethod.PUT.toString())) {
            RepositoryInitRepo repo = initRepo(request, repoName, requestBody);
            response.setStatus(HttpStatus.CREATED.value());
            encode(repo, request, response);
        } else {
            throw new CommandSpecException("The request method is unsupported for this operation.",
                    HttpStatus.METHOD_NOT_ALLOWED, Sets.newHashSet(RequestMethod.PUT.toString()));
        }
    }

    @RequestMapping(method = {RequestMethod.GET, RequestMethod.DELETE, RequestMethod.POST,
            RequestMethod.PUT},  consumes = {APPLICATION_FORM_URLENCODED_VALUE})
    public void initRepositoryFromForm(
            @PathVariable(name = "repoName")String repoName,
            @RequestBody MultiValueMap<String, String> requestBody,
            HttpServletRequest request, HttpServletResponse response)
            throws RepositoryConnectionException {
        if (request.getMethod().equals(RequestMethod.PUT.toString())) {
            RepositoryInitRepo repo = initRepo(request, repoName, requestBody);
            response.setStatus(HttpStatus.CREATED.value());
            encode(repo, request, response);
        } else {
            throw new CommandSpecException("The request method is unsupported for this operation.",
                    HttpStatus.METHOD_NOT_ALLOWED, Sets.newHashSet(RequestMethod.PUT.toString()));
        }
    }

    private RepositoryInitRepo initRepo(HttpServletRequest request, String repoName)
            throws RepositoryConnectionException {
        Optional<RepositoryProvider> repoProvider = getRepoProvider(request);
        if (repoProvider.isPresent()) {
            return repositoryInitService.initRepository(repoProvider.get(), repoName,
                    Maps.newHashMap());
        } else {
            throw new CommandSpecException("No GeoGig repository provider set in the request.");
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
            throw new CommandSpecException("No GeoGig repository provider set in the request.");
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
            throw new CommandSpecException("No GeoGig repository provider set in the request.");
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
