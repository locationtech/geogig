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
import java.util.List;

import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.storage.postgresql.config.Environment;
import org.locationtech.geogig.storage.postgresql.config.EnvironmentBuilder;
import org.locationtech.geogig.storage.postgresql.config.PGStorage;
import org.locationtech.geogig.storage.postgresql.config.PGStorageTableManager;
import org.locationtech.geogig.storage.postgresql.config.Version;

import com.google.common.base.Preconditions;

/**
 * 
 * @since 1.2.1
 */
public class CreateDDL extends AbstractGeoGigOp<List<String>> {
    private Environment environment;

    private URI baseURI;

    protected @Override List<String> _call() {
        Environment env = resolveEnvironment();
        Version dbVersion = PGStorage.getServerVersion(env);
        PGStorageTableManager tableManager = PGStorageTableManager.forVersion(dbVersion);
        List<String> ddl = tableManager.createDDL(env);
        return ddl;
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

    public CreateDDL setEnvironment(Environment env) {
        this.environment = env;
        this.baseURI = null;
        return this;
    }

    public CreateDDL setBaseURI(URI baseURI) {
        this.environment = null;
        this.baseURI = baseURI;
        return this;
    }
}
