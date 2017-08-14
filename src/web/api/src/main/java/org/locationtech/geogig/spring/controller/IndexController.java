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
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_XML_VALUE;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.BooleanUtils;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.spring.dto.CommandResponseBean;
import org.locationtech.geogig.spring.dto.IndexInfoBean;
import org.locationtech.geogig.spring.dto.IndexList;
import org.locationtech.geogig.spring.dto.LegacyResponse;
import org.locationtech.geogig.spring.service.IndexService;
import org.locationtech.geogig.web.api.StreamingWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.base.Optional;

/**
 * Controller for handing Repository INIT requests.
 */
@RestController
@RequestMapping(path = "/" + BASE_REPOSITORY_ROUTE + "/{repoName}/index",
        produces = {APPLICATION_XML_VALUE, APPLICATION_JSON_VALUE})
public class IndexController extends AbstractController {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskController.class);

    @Autowired
    private IndexService indexService;

    @GetMapping(path="/list", params = API_V2)
    public CommandResponseBean listIndexesV2(@PathVariable String repoName,
            @RequestAttribute(required = false) String treeName,
            final HttpServletRequest request) {
        return new CommandResponseBean(true, getIndexList(request, repoName, treeName));
    }

    @GetMapping(path = "/list")
    public void listIndexes(@PathVariable String repoName,
            @RequestAttribute(required = false) String treeName,
            final HttpServletRequest request, final HttpServletResponse response) {
        IndexList list = getIndexList(request, repoName, treeName);
        encodeCommandResponse(true, list, request, response);
    }

    private IndexList getIndexList(HttpServletRequest request, String repoName, String treeName) {
        IndexList list = new IndexList();
        // get the repositoryProvider form the request
        Optional<RepositoryProvider> repoProvider = getRepoProvider(request);
        if (repoProvider.isPresent()) {
            Optional<Repository> repo = repoProvider.get().getGeogig(repoName);
            if (repo.isPresent()) {
                return indexService.getIndexList(repo.get(), treeName);
            } else if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("No repo.");
            }
        } else if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("No GeoGig repository provider set in the request.");
        }
        return list;
    }

    @PutMapping(path = "/create", params = API_V2)
    public CommandResponseBean createIndexV2(@PathVariable String repoName,
            @RequestAttribute String treeRefSpec,
            @RequestAttribute(required = false) String geometryAttributeName,
            @RequestAttribute(required = false) List<String> extraAttributes,
            @RequestAttribute(required = false) Boolean indexHistory,
            @RequestAttribute(required = false) String bounds, final HttpServletRequest request) {
        IndexInfoBean index = createIndex(repoName, treeRefSpec, geometryAttributeName,
                extraAttributes, indexHistory, bounds, request);
        return new CommandResponseBean(true, index);
    }

    @PutMapping(path = "/create")
    public void createIndex(@PathVariable String repoName, @RequestAttribute String treeRefSpec,
            @RequestAttribute(required = false) String geometryAttributeName,
            @RequestAttribute(required = false) List<String> extraAttributes,
            @RequestAttribute(required = false) Boolean indexHistory,
            @RequestAttribute(required = false) String bounds,
            final HttpServletRequest request,
            final HttpServletResponse response) {
        IndexInfoBean index = createIndex(repoName, treeRefSpec, geometryAttributeName,
                extraAttributes, indexHistory, bounds, request);
        encodeCommandResponse(true, index, request, response);
    }

    private IndexInfoBean createIndex(String repoName, String treeRefSpec,
            String geometryAttributeName, List<String> extraAttributes, Boolean indexHistory,
            String bounds, HttpServletRequest request) {
        Optional<RepositoryProvider> repoProvider = getRepoProvider(request);
        if (repoProvider.isPresent()) {
            Optional<Repository> repo = repoProvider.get().getGeogig(repoName);
            if (repo.isPresent()) {
                return indexService.createIndex(repo.get(), treeRefSpec, geometryAttributeName,
                        extraAttributes, BooleanUtils.isTrue(indexHistory), bounds);
            } else if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("No repo.");
            }
        } else if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("No GeoGig repository provider set in the request.");
        }
        return null;
    }

    @PutMapping(path = "/update", params = API_V2)
    public CommandResponseBean updateIndexV2(@PathVariable String repoName,
            @RequestAttribute String treeRefSpec,
            @RequestAttribute(required = false) String geometryAttributeName,
            @RequestAttribute(required = false) List<String> extraAttributes,
            @RequestAttribute(required = false) Boolean indexHistory,
            @RequestAttribute(required = false) Boolean add,
            @RequestAttribute(required = false) Boolean overwrite,
            @RequestAttribute(required = false) String bounds, final HttpServletRequest request) {
        IndexInfoBean index = updateIndex(repoName, treeRefSpec, geometryAttributeName,
                extraAttributes, indexHistory, add, overwrite, bounds, request);
        return new CommandResponseBean(true, index);
    }

    @PutMapping(path = "/update")
    public void updateIndex(@PathVariable String repoName, @RequestAttribute String treeRefSpec,
            @RequestAttribute(required = false) String geometryAttributeName,
            @RequestAttribute(required = false) List<String> extraAttributes,
            @RequestAttribute(required = false) Boolean indexHistory,
            @RequestAttribute(required = false) Boolean add,
            @RequestAttribute(required = false) Boolean overwrite,
            @RequestAttribute(required = false) String bounds, final HttpServletRequest request,
            final HttpServletResponse response) {
        IndexInfoBean index = updateIndex(repoName, treeRefSpec, geometryAttributeName,
                extraAttributes, indexHistory, add, overwrite, bounds, request);
        encodeCommandResponse(true, index, request, response);
    }

    private IndexInfoBean updateIndex(String repoName, String treeRefSpec,
            String geometryAttributeName, List<String> extraAttributes, Boolean indexHistory,
            Boolean add, Boolean overwrite, String bounds, HttpServletRequest request) {
        Optional<RepositoryProvider> repoProvider = getRepoProvider(request);
        if (repoProvider.isPresent()) {
            Optional<Repository> repo = repoProvider.get().getGeogig(repoName);
            if (repo.isPresent()) {
                return indexService.updateIndex(repo.get(), treeRefSpec, geometryAttributeName,
                        extraAttributes, BooleanUtils.isTrue(indexHistory),
                        BooleanUtils.isTrue(add), BooleanUtils.isTrue(overwrite), bounds);
            } else if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("No repo.");
            }
        } else if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("No GeoGig repository provider set in the request.");
        }
        return null;
    }

    @PostMapping(path = "/rebuild", params = API_V2)
    public CommandResponseBean rebuildIndexV2(@PathVariable String repoName,
            @RequestAttribute String treeRefSpec,
            @RequestAttribute(required = false) String geometryAttributeName,
            HttpServletRequest request) {
        final Integer treesRebuilt = rebuildIndex(repoName, treeRefSpec, geometryAttributeName,
                request);
        return new CommandResponseBean(true, treesRebuilt);
    }

    @PostMapping(path = "/rebuild")
    public void rebuildIndex(@PathVariable String repoName, @RequestAttribute String treeRefSpec,
            @RequestAttribute(required = false) String geometryAttributeName,
            HttpServletRequest request, HttpServletResponse response) {
        final Integer treesRebuilt = rebuildIndex(repoName, treeRefSpec, geometryAttributeName,
                request);
        encodeCommandResponse(true, new LegacyResponse() {
            @Override
            public void encode(StreamingWriter writer, MediaType format, String baseUrl) {
                writer.writeElement("treesRebuilt", Integer.toString(treesRebuilt));
            }
        }, request, response);
    }

    private Integer rebuildIndex(String repoName, String treeRefSpec,
            String geometryAttributeName, HttpServletRequest request) {
        Optional<RepositoryProvider> repoProvider = getRepoProvider(request);
        if (repoProvider.isPresent()) {
            Optional<Repository> repo = repoProvider.get().getGeogig(repoName);
            if (repo.isPresent()) {
                return indexService.rebuildIndex(repo.get(), treeRefSpec, geometryAttributeName);
            } else if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("No repo.");
            }
        } else if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("No GeoGig repository provider set in the request.");
        }
        return null;
    }

    @DeleteMapping(path = "/drop", params = API_V2)
    public CommandResponseBean dropIndexV2(@PathVariable String repoName,
            @RequestAttribute String treeRefSpec,
            @RequestAttribute(required = false) String geometryAttributeName,
            HttpServletRequest request) {
        IndexInfoBean index = dropIndex(repoName, treeRefSpec, geometryAttributeName, request);
        return new CommandResponseBean(true, index);
    }

    @PostMapping(path = "/drop")
    public void dropIndex(@PathVariable String repoName, @RequestAttribute String treeRefSpec,
            @RequestAttribute(required = false) String geometryAttributeName,
            HttpServletRequest request, HttpServletResponse response) {
        IndexInfoBean index = dropIndex(repoName, treeRefSpec, geometryAttributeName, request);
        index.setTagName("dropped");
        encodeCommandResponse(true, index, request, response);
    }

    private IndexInfoBean dropIndex(String repoName, String treeRefSpec,
            String geometryAttributeName, HttpServletRequest request) {
        Optional<RepositoryProvider> repoProvider = getRepoProvider(request);
        if (repoProvider.isPresent()) {
            Optional<Repository> repo = repoProvider.get().getGeogig(repoName);
            if (repo.isPresent()) {
                return indexService.dropIndex(repo.get(), treeRefSpec, geometryAttributeName);
            } else if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("No repo.");
            }
        } else if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("No GeoGig repository provider set in the request.");
        }
        return null;
    }


    @Override
    protected Logger getLogger() {
        return LOGGER;
    }
}
