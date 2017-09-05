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

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.spring.dto.LegacyRepoResponse;
import org.locationtech.geogig.spring.service.RepositoryService;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Common Controller functionality for controllers handling /repos/<i>repoName</i>/repo/* endpoints.
 */
public abstract class AbstractRepositoryController extends AbstractController {

    @Autowired
    protected RepositoryService repositoryService;

    /**
     * Verifies that the specified repository exists and is open. Subclasses can call this to assert
     * a given repository is open and, if not, write the appropriate error out to the response.
     *
     * @param provider The RepositoryProvider extracted form the request.
     * @param repoName The repository that should exist and be opened.
     * @param response The Response to which an error should be written if the repository does not
     * exist or is not open.
     *
     * @return true if the provided repository name identifies an existing, open repository, false
     * otherwise.
     */
    protected boolean isOpenRepository(RepositoryProvider provider, String repoName,
            HttpServletResponse response) {
        Repository repository = repositoryService.getRepository(provider, repoName);
        if (repository == null || !repository.isOpen()) {
            response.setStatus(SC_NOT_FOUND);
            response.setContentType(TEXT_PLAIN_VALUE);
            try (PrintWriter writer = response.getWriter()) {
                writer.print("Repository not found.");
            } catch (IOException ioe) {
                getLogger().error("Error writing response", ioe);
            }
            return false;
        }
        return true;
    }

    private ObjectId getValidObjectId(String objectId, HttpServletResponse response,
            boolean required, String validMessageFragment, String missingMessageFragment) {
        if (required && objectId == null) {
            // id required, but is null
            response.setStatus(SC_BAD_REQUEST);
            response.setContentType(TEXT_PLAIN_VALUE);
            try (PrintWriter writer = response.getWriter()) {
                writer.print("You must specify " + missingMessageFragment + " id.");
            } catch (IOException ioe) {
                getLogger().error("Error writing response", ioe);
            }
        } else if (objectId != null) {
            try {
                return ObjectId.valueOf(objectId);
            } catch (Exception ex) {
                // debug log this, just in case there is an error writing to the output stream
                getLogger().debug("Invalid commit hash in request", ex);
                response.setStatus(SC_BAD_REQUEST);
                response.setContentType(TEXT_PLAIN_VALUE);
                try (PrintWriter writer = response.getWriter()) {
                    writer.print("You must specify a valid " + validMessageFragment + " id.");
                } catch (IOException ioe) {
                    getLogger().error("Error writing response", ioe);
                }
            }
        }
        return null;
    }
    /**
     * Validates the supplied ObjectId string.
     * @param objectId String representation of the ObjectId to validate.
     * @param response Response to which errors should be written.
     * @param required true if the provided String id must not be null, false otherwise.
     * @return a non-null ObjectId if the provided string is valid, null otherwise.
     */
    protected ObjectId getValidObjectId(String objectId, HttpServletResponse response,
            boolean required) {
        return getValidObjectId(objectId, response, required, "object", "an object");
    }

    protected ObjectId getValidCommitId(String objectId, HttpServletResponse response,
            boolean required) {
        return getValidObjectId(objectId, response, required, "commit", "a commit");
    }

    protected final void encodeToStream(LegacyRepoResponse responseBean,
            final HttpServletRequest request, final HttpServletResponse response) {
        // Extract the baseURL from the request (NOTE: not reliable if proxies are involved)
        final String baseURL = getBaseUrl(request);
        // determine requested output format
        final MediaType requestedResponseFormat = responseBean
                .resolveMediaType(getMediaType(request));
        // set the Content-Type since we aren't using Spring's framework here
        response.setContentType(requestedResponseFormat.toString());
        // set the status
        response.setStatus(responseBean.getStatus().value());
        // write the LegacyResponse object out to the Response stream
        try {
            responseBean.encode(response.getOutputStream(), requestedResponseFormat, baseURL);
        } catch (Exception e) {
            throw new CommandSpecException("Error writing response",
                    HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }
}
