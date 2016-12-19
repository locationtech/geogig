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

import static org.locationtech.geogig.porcelain.ConfigOp.ConfigAction.CONFIG_SET;
import static org.locationtech.geogig.porcelain.ConfigOp.ConfigScope.LOCAL;

import java.util.Map;

import org.locationtech.geogig.porcelain.ConfigOp;
import org.restlet.data.Request;
import org.restlet.data.Status;
import org.restlet.resource.Representation;
import org.restlet.resource.Variant;

/**
 * CommandResource extension to handle repository initialization requests specifically. The main reason for this
 * extension is to support setting the 2 required configuration elements ("user.name" and "user.email") with a single
 * "init" command, as opposed to requiring clients to make 3 Web API requests, 1 to initialize the repository and 1
 * each to set "user.name" and "user.email".
 */
public class InitCommandResource extends CommandResource {

    /**
     * GeoGig author name.
     */
    public static final String AUTHOR_NAME = "authorName";
    /**
     * GeoGig author email.
     */
    public static final String AUTHOR_EMAIL = "authorEmail";

    public static final String INIT_CMD = "init";

    @Override
    protected String getCommandName() {
        return INIT_CMD;
    }

    @Override
    protected Representation runCommand(Variant variant, Request request) {
        Representation representation = super.runCommand(variant, request);

        if (getResponse().getStatus() == Status.SUCCESS_CREATED) {
            // set the Author name and email from the Init request
            setAuthor(request);
        }
        return representation;
    }

    private void setAuthor(Request request) {
        // get request attributes. If author info was requested, it will be stored there.
        Map<String, Object> attributes = request.getAttributes();
        if (attributes.containsKey(AUTHOR_NAME)) {
            // set the author name
            geogig.get().command(ConfigOp.class).setAction(CONFIG_SET).setScope(LOCAL)
                    .setName("user.name").setValue(attributes.get(AUTHOR_NAME).toString()).call();
        }
        if (attributes.containsKey(AUTHOR_EMAIL)) {
            // set the author email
            geogig.get().command(ConfigOp.class).setAction(CONFIG_SET).setScope(LOCAL)
                    .setName("user.email").setValue(attributes.get(AUTHOR_EMAIL).toString()).call();
        }
    }

}
