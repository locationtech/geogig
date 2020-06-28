/* 
 * Copyright (c) 2012-2016 Boundless and others.
 * Copyright (c) 2020 Gabriel Roldan.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.dsl;

import java.net.URI;
import java.util.Optional;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.porcelain.InitOp;
import org.locationtech.geogig.repository.Command;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.RepositoryFinder;
import org.locationtech.geogig.repository.RepositoryResolver;

import com.google.common.base.Preconditions;

import lombok.Getter;
import lombok.NonNull;

/**
 * A facade to GeoGig operations presenting a friendlier API to commonly used context, repository,
 * and command operations.
 */
public class Geogig {

    private @NonNull @Getter Context context;

    private Repository repository;

    public static Geogig of(@NonNull Context repo) {
        return new Geogig(repo);
    }

    public static Geogig of(@NonNull Repository repo) {
        return new Geogig(repo);
    }

    public static Geogig create(@NonNull URI uri) throws RepositoryConnectionException {
        return Geogig.create(uri, Hints.readWrite());
    }

    public static Geogig create(@NonNull URI uri, @NonNull Hints hints)
            throws RepositoryConnectionException {
        Repository repo = new RepositoryFinder().createRepository(uri, hints);
        repo = repo.command(InitOp.class).call();
        return Geogig.of(repo);
    }

    public static Geogig open(@NonNull URI uri) throws RepositoryConnectionException {
        Repository repo = new RepositoryFinder().open(uri);
        return Geogig.of(repo);
    }

    public static Geogig openReadOnly(@NonNull URI uri) throws RepositoryConnectionException {
        Repository repo = new RepositoryFinder().open(uri, Hints.readOnly());
        return Geogig.of(repo);
    }

    private Geogig(Repository repo) {
        this.repository = repo;
        this.context = repo.context();
    }

    private Geogig(final Context context) {
        Preconditions.checkNotNull(context, "context");
        this.context = context;
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
    public <T extends Command<?>> T command(Class<T> commandClass) {
        return context.command(commandClass);
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
            if (RepositoryFinder.INSTANCE.lookup(repoLocation.get())
                    .repoExists(repoLocation.get())) {
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
     * Calls on {@link ResolveGeogigURI} to get the repository's URI, looks up the proper
     * {@link RepsitoryResolver} through the provided SPI mechanism, and calls
     * {@link RepositoryResolver#delete(URI)} to delete the repo.
     * <p>
     * Precondition: {#link #isOpen()} must return {code false}
     */
    public static void delete(URI repoURI) throws Exception {
        RepositoryResolver resolver = RepositoryFinder.INSTANCE.lookup(repoURI);
        resolver.delete(repoURI);

    }

    public Commands commands() {
        return new Commands(context);
    }

    public Refs refs() {
        return new Refs(context);
    }

    public Config config() {
        return new Config(context);
    }

    public Objects objects() {
        return new Objects(context);
    }

    public Indexes indexes() {
        return new Indexes(context);
    }

    public CommitGraph graph() {
        return new CommitGraph(context);
    }

    public TreeWorker head() {
        return objects().head();
    }

    public TreeWorker workHead() {
        return objects().workHead();
    }

    public TreeWorker stageHead() {
        return objects().stageHead();
    }

    public Conflicts conflicts() {
        return new Conflicts(context);
    }

    public Blobs blobs() {
        return new Blobs(context);
    }
}
