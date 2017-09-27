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
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_XML_VALUE;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.HashSet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.repository.ParameterSetFactory;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.spring.dto.LegacyResponse;
import org.locationtech.geogig.spring.dto.RepositoryInfo;
import org.locationtech.geogig.spring.service.RepositoryService;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandBuilder;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.StreamResponse;
import org.locationtech.geogig.web.api.StreamingWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.google.common.base.Optional;

/**
 * Controller for repository commands and repository info.
 */
@RestController
@RequestMapping(path = GEOGIG_ROUTE_PREFIX + "/" + BASE_REPOSITORY_ROUTE
        + "/{repoName}",
        produces = {APPLICATION_XML_VALUE, APPLICATION_JSON_VALUE})
public class RepositoryCommandController extends AbstractController {

    public static final String UPLOAD_FILE_KEY = "fileUpload";

    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryCommandController.class);

    @Autowired
    private RepositoryService repositoryService;

    @GetMapping
    public void getRepositoryInfo(@PathVariable String repoName,
            final HttpServletRequest request, HttpServletResponse response) {
        Optional<RepositoryProvider> repoProvider = getRepoProvider(request);
        if (repoProvider.isPresent()) {
            RepositoryInfo repositoryInfo =
                    repositoryService.getRepositoryInfo(repoProvider.get(), repoName);
            if (repositoryInfo != null) {
                encode(repositoryInfo, request, response);
            } else {
                // not found
                response.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE);
                response.setStatus(HttpStatus.NOT_FOUND.value());
                try (PrintWriter out = response.getWriter()) {
                    out.print("not found");
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
            }
        } else {
            throw NO_PROVIDER;
        }
    }

    @DeleteMapping
    public void deleteRepository(@PathVariable String repoName,
            @RequestParam MultiValueMap<String, String> params, final HttpServletRequest request,
            HttpServletResponse response, RequestEntity<String> entity) {
        Optional<RepositoryProvider> repoProvider = getRepoProvider(request);
        if (repoProvider.isPresent()) {
            RepositoryProvider provider = repoProvider.get();
            if (!provider.hasGeoGig(repoName)) {
                throw new CommandSpecException("No repository to delete.",
                        HttpStatus.NOT_FOUND);
            }

            Repository geogig = provider.getGeogig(repoName).get();
            ParameterSet parameters = ParameterSet.concat(getParamsFromEntity(entity),
                    ParameterSetFactory.buildParameterSet(params));
            final String deleteToken = parameters.getFirstValue("token");
            if (deleteToken == null) {
                throw new CommandSpecException(
                        "You must specify the correct token to delete a repository.",
                        HttpStatus.BAD_REQUEST);
            }

            final String deleteKey = deleteKeyForToken(deleteToken);

            Optional<byte[]> blobValue = geogig.blobStore().getBlob(deleteKey);
            if (!blobValue.isPresent()) {
                throw new CommandSpecException("The specified token does not exist or has expired.",
                        HttpStatus.BAD_REQUEST);
            }

            provider.delete(repoName);
            encode(new LegacyResponse() {

                @Override
                protected void encodeInternal(StreamingWriter writer, MediaType format,
                        String baseUrl) {
                    writer.writeElement("deleted", repoName);
                }

            }, request, response);
        } else {
            throw new CommandSpecException("No repository to delete.",
                    HttpStatus.NOT_FOUND);
        }
    }

    public static String deleteKeyForToken(String token) {
        return "command/delete/" + token;
    }

    /**
     * Runs the given command.
     *
     * @param repoName the repository name
     * @param command the command name
     * @param params request parameters
     * @param file File to upload
     * @param request the request
     * @param response the response object
     * @param entity the RequestEntity payload, if any
     * @throws IOException
     */
    @RequestMapping(value = "/{command}")
    public void runCommand(@PathVariable String repoName, @PathVariable String command,
            @RequestParam MultiValueMap<String, String> params,
            @RequestParam(required = false, name = UPLOAD_FILE_KEY) MultipartFile file,
            HttpServletRequest request, HttpServletResponse response, RequestEntity<String> entity)
            throws IOException {
        AbstractWebAPICommand webCommand = buildCommand(command);
        RequestMethod method = RequestMethod.valueOf(request.getMethod());
        File uploadedFile = null;
        if (file != null) {
            uploadedFile = File.createTempFile(
                    "geogig-" + UPLOAD_FILE_KEY + "-", ".tmp");
            uploadedFile.deleteOnExit();
            // copy the upload data
            try (InputStream uploadInputStream = file.getInputStream();
                    OutputStream uploadOutputStream = new BufferedOutputStream(
                            new FileOutputStream(uploadedFile))) {
                FileCopyUtils.copy(uploadInputStream, uploadOutputStream);
            }
        }
        if (webCommand.supports(method)) {
            // build the PArameterSet from the request entity and parameters
            webCommand.setParameters(ParameterSet.concat(getParamsFromEntity(entity),
                    ParameterSetFactory.buildParameterSet(params, uploadedFile)));
            SpringContext context = buildContext(request, repoName, webCommand);
            webCommand.run(context);
            encode(context.getResponseContent(), request, response);
        } else {
            // determined allowed set of methods
            HashSet<String> allowedMethods = new HashSet<>(4);
            if (webCommand.supports(RequestMethod.GET)) {
                allowedMethods.add(RequestMethod.GET.toString());
            }
            if (webCommand.supports(RequestMethod.POST)) {
                allowedMethods.add(RequestMethod.POST.toString());
            }
            if (webCommand.supports(RequestMethod.PUT)) {
                allowedMethods.add(RequestMethod.PUT.toString());
            }
            if (webCommand.supports(RequestMethod.DELETE)) {
                allowedMethods.add(RequestMethod.DELETE.toString());
            }
            throw new CommandSpecException("The request method is unsupported for this operation.",
                    HttpStatus.METHOD_NOT_ALLOWED, allowedMethods);
        }
    }

    /**
     * Build an {@link AbstractWebAPICommand} from a command name.
     *
     * @param commandName the name of the command
     * @return
     */
    protected AbstractWebAPICommand buildCommand(String commandName) {
        return CommandBuilder.build(commandName);
    }

    private SpringContext buildContext(HttpServletRequest request, String repoName,
            AbstractWebAPICommand webCommand) {
        Optional<RepositoryProvider> repoProvider = getRepoProvider(request);
        RepositoryProvider provider = null;
        Repository repository = null;
        if (repoProvider.isPresent()) {
            provider = repoProvider.get();
            Optional<Repository> repo = provider.getGeogig(repoName);
            if (repo.isPresent() && repo.get().isOpen()) {
                repository = repo.get();
            }
        }
        return new SpringContext(provider, repository, request);
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

    static class SpringContext implements CommandContext {

        LegacyResponse responseContent = null;

        final Repository repository;

        final HttpServletRequest request;

        final RepositoryProvider provider;

        SpringContext(RepositoryProvider provider, Repository repository,
                HttpServletRequest request) {
            this.provider = provider;
            this.repository = repository;
            this.request = request;
        }

        @Override
        public Repository getRepository() {
            return repository;
        }

        @Override
        public RequestMethod getMethod() {
            return RequestMethod.valueOf(request.getMethod());
        }

        @Override
        public void setResponseContent(LegacyResponse responseContent) {
            this.responseContent = responseContent;
        }

        public LegacyResponse getResponseContent() {
            return this.responseContent;
        }

        @Override
        public String getBaseURL() {
            return request.getRequestURI();
        }

        @Override
        public void setResponseContent(StreamResponse responseContent) {
            // TODO Auto-generated method stub

        }

        @Override
        public RepositoryProvider getRepositoryProvider() {
            return provider;
        }

    }
}
