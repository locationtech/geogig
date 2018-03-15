/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository.impl;

import static com.google.common.base.Preconditions.checkState;

import java.net.URI;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.porcelain.InitOp;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.DiffObjectCount;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.RepositoryResolver;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * A facade to GeoGig operations.
 * <p>
 * Represents the checkout of user's working tree and repository and provides the operations to work
 * on them.
 * </p>
 * 
 */
public class GeoGIG {

    private Context context;

    private Repository repository;

    public GeoGIG(Repository repo) {
        this.repository = repo;
        this.context = repo.context();
    }

    /**
     * Constructs a new instance of the GeoGig facade with the given Guice injector
     * 
     * @param injector the injector to use
     * @see Context
     */
    public GeoGIG(final Context injector) {
        Preconditions.checkNotNull(injector, "injector");
        this.context = injector;
    }

    /**
     * Closes the current repository.
     */
    public void close() {
        if (repository != null) {
            repository.close();
            repository = null;
        }
        context = null;
    }

    /**
     * Finds and returns an instance of a command of the specified class.
     * 
     * @param commandClass the kind of command to locate and instantiate
     * @return a new instance of the requested command class, with its dependencies resolved
     */
    public <T extends AbstractGeoGigOp<?>> T command(Class<T> commandClass) {
        return context.command(commandClass);
    }

    /**
     * Sets the repository for this GeoGIG instance.
     * 
     * @param repository
     */
    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    /**
     * Obtains the repository for the current directory or creates a new one and returns it if no
     * repository can be found on the current directory.
     * 
     * @return the existing or newly created repository, never {@code null}
     * @throws RuntimeException if the repository cannot be created at the current directory
     * @see InitOp
     */
    public Repository getOrCreateRepository() {
        if (repository == null) {
            repository = command(InitOp.class).call();
            checkState(repository != null,
                    "Repository shouldn't be null as we checked it didn't exist before calling init");
        }
        return repository;
    }

    /**
     * @return the configured repository or {@code null} if no repository is found on the current
     *         directory
     */
    public synchronized @Nullable Repository getRepository() {
        if (repository != null) {
            return repository;
        }

        final Optional<URI> repoLocation = command(ResolveGeogigURI.class).call();
        if (repoLocation.isPresent()) {
            if (RepositoryResolver.lookup(repoLocation.get()).repoExists(repoLocation.get())) {
                repository = context.repository();
                try {
                    repository.open();
                } catch (RepositoryConnectionException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return repository;
    }

    /**
     * @return the platform for this GeoGig facade
     */
    public Platform getPlatform() {
        return context.platform();
    }

    /**
     * @return
     */
    public Context getContext() {
        return context;
    }

    public DiffObjectCount countUnstaged() {
        return getRepository().workingTree().countUnstaged(null);
    }

    public DiffObjectCount countStaged() {
        return getRepository().index().countStaged(null);
    }

    public boolean isOpen() {
        return repository != null && repository.isOpen();
    }

    /**
     * Calls on {@link ResolveGeogigURI} to get the repository's URI, looks up the proper
     * {@link RepsitoryResolver} through the provided SPI mechanism, and calls
     * {@link RepositoryResolver#delete(URI)} to delete the repo.
     * <p>
     * Precondition: {#link #isOpen()} must return {code false}
     */
    public static void delete(URI repoURI) throws Exception {
        RepositoryResolver resolver = RepositoryResolver.lookup(repoURI);
        resolver.delete(repoURI);

    }
}
