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

import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.spring.dto.InitRequest;
import org.locationtech.geogig.spring.dto.RepositoryInitRepo;
import org.locationtech.geogig.spring.service.RepositoryInitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;

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

    @PutMapping
    public RepositoryInitRepo initRepositoryNoBody(@PathVariable(name = "repoName") String repoName,
            HttpServletRequest request) throws RepositoryConnectionException {
        Optional<RepositoryProvider> repoProvider = getRepoProvider(request);
        if (repoProvider.isPresent()) {
            System.out.println("repo provider present");
            return repositoryInitService.initRepository(repoProvider.get(), repoName,
                    Maps.newHashMap());
        } else if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("No GeoGig repository provider set in the request.");
        }
        return null;
    }

    @PutMapping(consumes = {APPLICATION_JSON_VALUE, APPLICATION_XML_VALUE})
    public RepositoryInitRepo initRepositoryFromJson(
            @PathVariable(name = "repoName")String repoName,
            @RequestBody InitRequest requestBody, HttpServletRequest request)
            throws RepositoryConnectionException {
        Optional<RepositoryProvider> repoProvider = getRepoProvider(request);
        if (repoProvider.isPresent()) {
            System.out.println("repo provider present");
            return repositoryInitService.initRepository(repoProvider.get(), repoName,
                    requestBody.getParameters());
        } else if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("No GeoGig repository provider set in the request.");
        }
        return null;
    }

    @PutMapping(consumes = {APPLICATION_FORM_URLENCODED_VALUE})
    public RepositoryInitRepo initRepositoryFromForm(
            @PathVariable(name = "repoName")String repoName,
            @RequestBody MultiValueMap<String, String> request) {
        // parse the request
        System.out.println("Init Request FORM body:");
        for (MultiValueMap.Entry entry : request.entrySet()) {
            System.out.println("\t" + entry.getKey() + " = " + entry.getValue());
        }
        return new RepositoryInitRepo().setName(repoName);
    }

    @Override
    protected Logger getLogger() {
        // TODO Auto-generated method stub
        return null;
    }
}
