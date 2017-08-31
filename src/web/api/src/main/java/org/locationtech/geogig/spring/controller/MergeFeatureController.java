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
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.DELETE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.OPTIONS;
import static org.springframework.web.bind.annotation.RequestMethod.PATCH;
import static org.springframework.web.bind.annotation.RequestMethod.POST;
import static org.springframework.web.bind.annotation.RequestMethod.PUT;
import static org.springframework.web.bind.annotation.RequestMethod.TRACE;

import java.util.HashSet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.spring.dto.MergeFeatureRequest;
import org.locationtech.geogig.spring.dto.MergeFeatureResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.base.Optional;

/**
 *
 */
@RestController
@RequestMapping(path = "/" + BASE_REPOSITORY_ROUTE + "/{repoName}/repo/mergefeature")
public class MergeFeatureController extends AbstractRepositoryController {

    private static final Logger LOGGER = LoggerFactory.getLogger(MergeFeatureController.class);

    private static final HashSet<String> SUPPORTED_METHODS = new HashSet<>(1);

    static {
        MergeFeatureController.SUPPORTED_METHODS.add(POST.toString());
    }

    @RequestMapping(method = {GET, PUT, DELETE, PATCH, TRACE, OPTIONS})
    public void catchAll() {
        // if we hit this controller, it's a 405
        supportedMethods(SUPPORTED_METHODS);
    }

    @PostMapping
    public void invalidPostData() {
        // this matches any POST requests that don't have a MergeFeatureRequest entity
        throw new IllegalArgumentException("Invalid POST data.");
    }

    @PostMapping(consumes = APPLICATION_JSON_VALUE)
    public void postMerge(@PathVariable(name = "repoName") String repoName,
            @RequestBody MergeFeatureRequest mergeRequest,
            HttpServletRequest request, HttpServletResponse response) {
        // get the provider
        Optional<RepositoryProvider> optional = getRepoProvider(request);
        if (optional.isPresent()) {
            final RepositoryProvider provider = optional.get();
            // ensure the repo exists and is opened
            if (!isOpenRepository(provider, repoName, response)) {
                // done
                return;
            }
            // mergeRequest won't be null as Spring will instantiate one by this point
            // if any of the pieces are not set/missing in the request, we have bad post data
            if (mergeRequest.getMerges() == null || mergeRequest.getOurs() == null ||
                mergeRequest.getPath() == null || mergeRequest.getTheirs() == null) {
                throw new IllegalArgumentException("Invalid POST data.");
            }
            // merge the features
            RevFeature mergeFeatures = repositoryService.mergeFeatures(provider, repoName,
                    mergeRequest);
            // build the response
            MergeFeatureResponse mfResponse = new MergeFeatureResponse().setMergedFeature(
                    mergeFeatures);
            // encode the response
            encode(mfResponse, request, response);
        } else {
            throw NO_PROVIDER;
        }
    }

    @Override
    protected void preEncodeResponse(HttpServletRequest request, HttpServletResponse response) {
        // setup the content-type
        response.setContentType(TEXT_PLAIN_VALUE);
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

}
