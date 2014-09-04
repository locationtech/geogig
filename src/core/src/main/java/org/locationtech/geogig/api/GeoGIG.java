/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api;

import static com.google.common.base.Preconditions.checkState;

import java.io.File;
import java.net.URL;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.plumbing.ResolveGeogigDir;
import org.locationtech.geogig.api.plumbing.diff.DiffObjectCount;
import org.locationtech.geogig.api.porcelain.InitOp;
import org.locationtech.geogig.repository.Repository;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

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

    /**
     * Constructs a new instance of the GeoGig facade.
     */
    public GeoGIG() {
        context = GlobalContextBuilder.builder.build();
    }

    /**
     * Constructs a new instance of the GeoGig facade with the given working directory.
     * 
     * @param workingDir the working directory for this instance of GeoGig
     */
    public GeoGIG(File workingDir) {
        this();
        context.platform().setWorkingDir(workingDir);
    }

    /**
     * Constructs a new instance of the GeoGig facade with the given Guice injector
     * 
     * @param injector the injector to use
     * @see Context
     */
    public GeoGIG(final Context injector) {
        this(injector, null);
    }

    /**
     * Constructs a new instance of the GeoGig facade with the given Guice injector and working
     * directory.
     * 
     * @param injector the injector to use
     * @param workingDir the working directory for this instance of GeoGig
     * @see Context
     */
    public GeoGIG(final Context injector, @Nullable final File workingDir) {
        Preconditions.checkNotNull(injector, "injector");
        this.context = injector;
        if (workingDir != null) {
            Platform instance = injector.platform();
            instance.setWorkingDir(workingDir);
        }
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
        if (getRepository() == null) {
            try {
                repository = command(InitOp.class).call();
                checkState(repository != null,
                        "Repository shouldn't be null as we checked it didn't exist before calling init");
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
        return repository;
    }

    /**
     * @return the configured repository or {@code null} if no repository is found on the current
     *         directory
     */
    public synchronized Repository getRepository() {
        if (repository != null) {
            return repository;
        }

        final Optional<URL> repoLocation = command(ResolveGeogigDir.class).call();
        if (repoLocation.isPresent()) {
            try {
                repository = context.repository();
                repository.open();
            } catch (Exception e) {
                throw Throwables.propagate(e);
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
        return repository != null;
    }
}
