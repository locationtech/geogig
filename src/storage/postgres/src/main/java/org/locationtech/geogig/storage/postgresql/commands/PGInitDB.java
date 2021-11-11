/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql.commands;

import java.net.URI;

import org.locationtech.geogig.base.Preconditions;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;
import org.locationtech.geogig.storage.postgresql.config.ConnectionConfig;
import org.locationtech.geogig.storage.postgresql.config.Environment;
import org.locationtech.geogig.storage.postgresql.config.EnvironmentBuilder;
import org.locationtech.geogig.storage.postgresql.config.PGStorage;

import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Initializes a PostgreSQL database to host geogig repositories
 */
@Accessors(chain = true)
public class PGInitDB extends AbstractGeoGigOp<Void> {

    private @Setter Environment environment;

    private @Setter URI baseURI;

    protected @Override Void _call() {
        final Environment env = resolveEnvironment();
        getProgressListener().started();
        ConnectionConfig conf = env.getConnectionConfig();
        getProgressListener().setDescription("Initializing geogig on host %s:%d, db %s, schema %s",
                conf.getServer(), conf.getPortNumber(), conf.getDatabaseName(), conf.getSchema());
        try {
            PGStorage.createTables(env);
        } finally {
            if (this.environment == null) {
                env.close();
            }
        }
        return null;
    }

    private Environment resolveEnvironment() {
        Preconditions.checkArgument(environment != null || baseURI != null,
                "Environment or base URI argument not provided");
        if (environment != null) {
            return environment;
        }
        EnvironmentBuilder environmentBuilder = new EnvironmentBuilder(baseURI, true);
        Environment env = environmentBuilder.build();
        return env;
    }
}
