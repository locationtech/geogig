/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest.repository;

import static org.locationtech.geogig.rest.repository.InitCommandResource.AUTHOR_EMAIL;
import static org.locationtech.geogig.rest.repository.InitCommandResource.AUTHOR_NAME;
import static org.locationtech.geogig.web.api.RESTUtils.getStringAttribute;
import static org.restlet.data.Status.CLIENT_ERROR_BAD_REQUEST;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;
import javax.servlet.http.HttpServletRequest;

import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.RepositoryResolver;
import org.locationtech.geogig.rest.RestletException;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Status;
import org.restlet.resource.Representation;

import com.google.common.base.Optional;

/**
 * Utility for handling GeoGIG repository init requests. This class will pull repository creation
 * details (like parent directory, or PostgreSQL database connection parameters) from the Request
 * and build a GeoGIG repository form them, by converting the request into a
 * {@link org.locationtech.geogig.repository.Hints Hints}.
 */
public class InitRequestUtil {

    private static final InitRequestUtil INSTANCE = new InitRequestUtil();

    private static final String SCHEME = "postgresql";
    private static final String USER = "user";
    private static final String PASSWORD = "password";
    private static final String SLASH = "/";
    private static final String UTF8 = StandardCharsets.UTF_8.name();

    static final String REPO_ATTR = "repository";

    // Form parameter names
    /**
     * Directory option for Parent Directory.
     */
    static final String DIR_PARENT_DIR = "parentDirectory";
    /**
     * Database option for Host.
     */
    static final String DB_HOST = "dbHost";
    /**
     * Database option for Port.
     */
    static final String DB_PORT = "dbPort";
    /**
     * Database option for database name.
     */
    static final String DB_NAME = "dbName";
    /**
     * Database option for schema name.
     */
    static final String DB_SCHEMA = "dbSchema";
    /**
     * Database option for username.
     */
    static final String DB_USER = "dbUser";
    /**
     * Database option for password.
     */
    static final String DB_PASSWORD = "dbPassword";

    private void addParameter(Map<String, String> params, String key, String value) {
        if (value != null) {
            params.put(key, value);
        }
    }

    @Deprecated
    private void updateRequestWithAuthor(Request request, Map<String, String> params) {
        // store author name and email in the attributes, if they were provided
        if (params.containsKey(AUTHOR_NAME)) {
            request.getAttributes().put(AUTHOR_NAME, params.get(AUTHOR_NAME));
        }
        if (params.containsKey(AUTHOR_EMAIL)) {
            request.getAttributes().put(AUTHOR_EMAIL, params.get(AUTHOR_EMAIL));
        }
    }

    private void updateRequestWithAuthor(HttpServletRequest request, Map<String, String> params) {
        // store author name and email in the attributes, if they were provided
        if (params.containsKey(AUTHOR_NAME)) {
            request.setAttribute(AUTHOR_NAME, params.get(AUTHOR_NAME));
        }
        if (params.containsKey(AUTHOR_EMAIL)) {
            request.setAttribute(AUTHOR_EMAIL, params.get(AUTHOR_EMAIL));
        }
    }

    /**
     * Adds JSON request parameters to the parameter Map. Look for known parameters in the JSON
     * object and populate the supplied map with requested values. This method does the same thing as
     * the {@link #addParameters(java.util.Map, org.restlet.data.Form)} version, except this one is
     * for Requests with a JSON payload, as opposed to a URL encoded form.
     *
     * @param params Map to hold request parameters.
     * @param json   JSONObject from a Request with parameters in a JSON payload.
     */
    private void addParameters(Map<String, String> params, JsonObject json) {
        addParameter(params, DIR_PARENT_DIR, json.getString(DIR_PARENT_DIR, null));
        addParameter(params, DB_HOST, json.getString(DB_HOST, null));
        addParameter(params, DB_PORT, json.getString(DB_PORT, null));
        addParameter(params, DB_NAME, json.getString(DB_NAME, null));
        addParameter(params, DB_SCHEMA, json.getString(DB_SCHEMA, null));
        addParameter(params, DB_USER, json.getString(DB_USER, null));
        addParameter(params, DB_PASSWORD, json.getString(DB_PASSWORD, null));
        addParameter(params, AUTHOR_NAME, json.getString(AUTHOR_NAME, null));
        addParameter(params, AUTHOR_EMAIL, json.getString(AUTHOR_EMAIL, null));
    }

    /**
     * Adds URL encoded request parameters to the parameter Map. Look for known parameters in the Form
     * and populate the supplied map with requested values. This method does the same thing as the
     * {@link #addParameters(java.util.Map, org.json.JSONObject)} version, except this one is for
     * Requests with a URL encoded form, as opposed to a JSON payload.
     *
     * @param params Map to hold the request parameters.
     * @param form   URL encoded Form from a Request with parameters encoded.
     */
    private void addParameters(Map<String, String> params, Form form) {
        addParameter(params, DIR_PARENT_DIR, form.getFirstValue(DIR_PARENT_DIR, null));
        addParameter(params, DB_HOST, form.getFirstValue(DB_HOST, null));
        addParameter(params, DB_PORT, form.getFirstValue(DB_PORT, null));
        addParameter(params, DB_NAME, form.getFirstValue(DB_NAME, null));
        addParameter(params, DB_SCHEMA, form.getFirstValue(DB_SCHEMA, null));
        addParameter(params, DB_USER, form.getFirstValue(DB_USER, null));
        addParameter(params, DB_PASSWORD, form.getFirstValue(DB_PASSWORD, null));
        addParameter(params, AUTHOR_NAME, form.getFirstValue(AUTHOR_NAME, null));
        addParameter(params, AUTHOR_EMAIL, form.getFirstValue(AUTHOR_EMAIL, null));
    }

    private void addFormParameters(Map<String, String> params, HttpServletRequest request) {
        addParameter(params, DIR_PARENT_DIR, request.getParameter(DIR_PARENT_DIR));
        addParameter(params, DB_HOST, request.getParameter(DB_HOST));
        addParameter(params, DB_PORT, request.getParameter(DB_PORT));
        addParameter(params, DB_NAME, request.getParameter(DB_NAME));
        addParameter(params, DB_SCHEMA, request.getParameter(DB_SCHEMA));
        addParameter(params, DB_USER, request.getParameter(DB_USER));
        addParameter(params, DB_PASSWORD, request.getParameter(DB_PASSWORD));
        addParameter(params, AUTHOR_NAME, request.getParameter(AUTHOR_NAME));
        addParameter(params, AUTHOR_EMAIL, request.getParameter(AUTHOR_EMAIL));
    }

    /**
     * Parses Request entity for repository creation specifics and adds them to a key-value map. Currently, only
     * URL Encoded web forms or JSON objects are supported in the Request entity. Any other Content-Type/MediaType in
     * the request will be rejected with a 400 Bad Request. If the request entity is empty (for example, "{}" for a
     * JSON payload), the default repository provider will be chosen when creating repositories. It is recommended to
     * provide either a "parentDirectory" element (for file-backed GeoGig repositories) or the database elements (for
     * Database-backed GeoGig repositories). If BOTH are specified, the "parentDirectory" will be used for a file-backed
     * repository.
     * @param request GeoGig Repository initialization request
     * @return Key-Value Map of initialization parameters specified in the request entity.
     */
    @Deprecated
    private Map<String, String> getRequestParameters(Request request) {
        HashMap<String, String> params = new HashMap<>(10);
        if (request.isEntityAvailable()) {
            Representation entity = request.getEntity();
            final MediaType reqMediaType = entity.getMediaType();

            if (MediaType.APPLICATION_WWW_FORM.equals(reqMediaType, true)) {
                // URL encoded form parameters
                try {
                    Form form = request.getEntityAsForm();
                    addParameters(params, form);
                } catch (Exception ex) {
                    throw new RestletException("Error parsing URL encoded form request",
                            CLIENT_ERROR_BAD_REQUEST, ex);
                }
            } else if (MediaType.APPLICATION_JSON.equals(reqMediaType, true)) {
                // JSON encoded parameters
                try {
                    String jsonRep = entity.getText();
                    JsonObject jsonObj = Json.createReader(new StringReader(jsonRep)).readObject();
                    addParameters(params, jsonObj);
                } catch (IOException ex) {
                    throw new RestletException("Error parsing JSON request",CLIENT_ERROR_BAD_REQUEST, ex);
                }
            } else if (null != reqMediaType) {
                // unsupported MediaType
                throw new RestletException("Unsupported Request MediaType: " + reqMediaType,
                        CLIENT_ERROR_BAD_REQUEST);
            }
            // no parameters specified
        }
        // the request body was just consumed and can't be retrieved again. If we parsed Author info,
        // store that on the request for later processing.
        updateRequestWithAuthor(request, params);
        return params;
    }

    /**
     * Parses Request entity for repository creation specifics and adds them to a key-value map.
     * Currently, only URL Encoded web forms or JSON objects are supported in the Request entity.
     * Any other Content-Type/MediaType in the request will be rejected with a 400 Bad Request. If
     * the request entity is empty (for example, "{}" for a JSON payload), the default repository
     * provider will be chosen when creating repositories. It is recommended to provide either a
     * "parentDirectory" element (for file-backed GeoGig repositories) or the database elements (for
     * Database-backed GeoGig repositories). If BOTH are specified, the "parentDirectory" will be
     * used for a file-backed repository.
     *
     * @param request GeoGig Repository initialization request
     *
     * @return Key-Value Map of initialization parameters specified in the request entity.
     */
    private Map<String, String> getRequestParameters(HttpServletRequest request) {
        HashMap<String, String> params = new HashMap<>(10);
        // get the content type
        String contentType = request.getContentType();
        if (null != contentType) {
            switch (contentType) {
                case APPLICATION_FORM_URLENCODED_VALUE :
                    addFormParameters(params, request);
                    break;
                case APPLICATION_JSON_VALUE :
                    try {
                        JsonObject jsonObject = Json.createReader(request.getReader()).readObject();
                        addParameters(params, jsonObject);
                    } catch (IOException ioe) {
                        throw new RuntimeException(ioe);
                    }
                default :
                    throw new RuntimeException("Unsupported Request MediaType: " + contentType);
            }
        }
        // the request body was just consumed and can't be retrieved again. If we parsed Author info,
        // store that on the request for later processing.
        updateRequestWithAuthor(request, params);
        return params;
    }

    /**
     * Updates the provided Hints instance with a GeoGig Repository URL based on the supplied parameter map. This
     * method builds the Repository location URI form the supplied parameter map and sets it on the provided Hints
     * instance so that the RepositoryResolver can correctly build the repository at the requested location.
     *
     * @param hints Hints instance that will be used to build the requested GeoGig Repository.
     * @param params Key-Value map of repository location specifics.
     * @throws UnsupportedEncodingException
     * @throws URISyntaxException
     * @throws IOException
     * @throws RepositoryConnectionException
     */
    private void updateHintsWithParams(Hints hints, Map<String, String> params)
            throws UnsupportedEncodingException, URISyntaxException, IOException, RepositoryConnectionException {
        // get parameters
        final String parentDir = params.get(DIR_PARENT_DIR);
        final String dbHost = params.getOrDefault(DB_HOST, "localhost");
        final String dbPort = params.getOrDefault(DB_PORT, "5432");
        final String dbName = params.get(DB_NAME);
        final String dbSchema = params.getOrDefault(DB_SCHEMA, "public");
        final String dbUser = params.getOrDefault(DB_USER, "postgres");
        final String dbPassword = params.get(DB_PASSWORD);
        // use parent directory if present
        if (parentDir != null) {
            final String newRepoName = hints.get(Hints.REPOSITORY_NAME).get().toString();
            final URI parentUri = new File(parentDir).getCanonicalFile().toURI();
            final RepositoryResolver resolver = RepositoryResolver.lookup(parentUri);
            // use the existing repo URI
            URI repoURI = resolver.buildRepoURI(parentUri, newRepoName);
            hints.uri(repoURI);
        } else if (dbName != null && dbPassword != null) {
            // try to build a URI from the db parameters
            // build the path from database, schema and repository name
            final String repoName = hints.get(Hints.REPOSITORY_NAME).get().toString();
            final StringBuilder pathBuilder = new StringBuilder(128);
            // build the path as "/dbName/schema/repoName"
            pathBuilder.append(SLASH).append(dbName).append(SLASH).append(dbSchema).append(SLASH).append(repoName);
            // now build the query part, i.e. user=username&password=password
            // need to URLEncode these to make sure things are encoded that need to be, and things don't get double
            // encoded
            final StringBuilder queryBuilder = new StringBuilder(64);

            queryBuilder.append(USER).append('=').append(URLEncoder.encode(dbUser, UTF8))
                    .append('&').append(PASSWORD).append('=').append(URLEncoder.encode(dbPassword, UTF8));
            // convert the port to an Integer
            int port = 5432;
            try {
                port = Integer.parseInt(dbPort);
            } catch (NumberFormatException nfe) {
                // just ues the default of 5432
            }
            final URI repoUri = new URI(
                    SCHEME, null, dbHost, port, pathBuilder.toString(), queryBuilder.toString(), null);
            hints.set(Hints.REPOSITORY_URL, repoUri);
        }
    }

    @Deprecated
    public static Hints createHintsFromRequest(Request request) throws UnsupportedEncodingException, URISyntaxException, IOException,
            RepositoryConnectionException {
        // get the repository name from the request
        final Optional<String> nameOptional = Optional.fromNullable(getStringAttribute(request,
                REPO_ATTR));
        if (!nameOptional.isPresent()) {
            // no repo name provided
            throw new RestletException(String.format(
                    "Cannot create GeoGIG repository. Missing '%s' resource", REPO_ATTR),
                    Status.CLIENT_ERROR_BAD_REQUEST);
        }
        final String repoName = nameOptional.get();
        final Hints hints = new Hints();
        hints.set(Hints.REPOSITORY_NAME, repoName);
        // try to build the Repo URI from any Request parameters.
        INSTANCE.updateHintsWithParams(hints, INSTANCE.getRequestParameters(request));
        return hints;
    }

    public static Hints createHintsFromRequest(HttpServletRequest request)
            throws UnsupportedEncodingException, URISyntaxException, IOException,
            RepositoryConnectionException {
        // get the repository name from the request
        final Optional<String> nameOptional = Optional.fromNullable(getStringAttribute(request,
                REPO_ATTR));
        if (!nameOptional.isPresent()) {
            // no repo name provided
            throw new RestletException(String.format(
                    "Cannot create GeoGIG repository. Missing '%s' resource", REPO_ATTR),
                    Status.CLIENT_ERROR_BAD_REQUEST);
        }
        final String repoName = nameOptional.get();
        final Hints hints = new Hints();
        hints.set(Hints.REPOSITORY_NAME, repoName);
        // try to build the Repo URI from any Request parameters.
        INSTANCE.updateHintsWithParams(hints, INSTANCE.getRequestParameters(request));
        return hints;
    }
}
