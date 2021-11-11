/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.porcelain;

import static org.locationtech.geogig.model.Ref.HEAD;
import static org.locationtech.geogig.model.Ref.MASTER;
import static org.locationtech.geogig.model.Ref.STAGE_HEAD;
import static org.locationtech.geogig.model.Ref.WORK_HEAD;
import static org.locationtech.geogig.model.RevTree.EMPTY_TREE_ID;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.Serializable;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.plumbing.UpdateRefs;
import org.locationtech.geogig.plumbing.UpdateSymRef;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.RepositoryFinder;
import org.locationtech.geogig.repository.RepositoryResolver;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConfigException;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.storage.impl.Blobs;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.io.Files;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.Setter;

/**
 * Creates or "initializes" a repository in the {@link Platform#pwd() working directory}.
 * <p>
 * This command tries to find an existing {@code .geogig} repository directory in the current
 * directory's hierarchy. It is safe to call it from inside a directory that's a child of a
 * repository.
 * <p>
 * If no repository directory is found, then a new one is created on the current directory.
 * 
 * @see ResolveGeogigURI
 * @see RefParse
 * @see UpdateRef
 * @see UpdateSymRef
 */
@CanRunDuringConflict
public class InitOp extends AbstractGeoGigOp<Repository> {

    private @Setter(value = AccessLevel.PACKAGE) @VisibleForTesting RepositoryFinder repositoryFinder = RepositoryFinder.INSTANCE;

    private Map<String, String> config = new TreeMap<>();

    private String filterFile;

    public InitOp setConfig(Map<String, String> suppliedConfiguration) {
        this.config = Map.copyOf(suppliedConfiguration);
        return this;
    }

    public InitOp setFilterFile(String filterFile) {
        this.filterFile = filterFile;
        return this;
    }

    /**
     * Executes the Init operation.
     * 
     * @return the initialized repository
     * @throws IllegalStateException if a repository cannot be created on the current directory or
     *         re-initialized in the current dir or one if its parents as determined by
     *         {@link ResolveGeogigURI}
     */
    protected @Override Repository _call() {
        final Hints hints = context().hints();
        final Platform platform = platform();
        final URI repoURI = new ResolveGeogigURI(platform, hints).call()
                .orElseGet(() -> platform.pwd().getAbsoluteFile().toURI());

        final RepositoryResolver repoInitializer = repositoryFinder.lookup(repoURI);
        final boolean repoExisted = repoInitializer.repoExists(repoURI);

        repoInitializer.initialize(repoURI);

        Map<String, String> effectiveConfigBuilder = new TreeMap<>();
        Optional<Serializable> repoName = hints.get(Hints.REPOSITORY_NAME);
        if (repoName.isPresent()) {
            effectiveConfigBuilder.put("repo.name", String.valueOf(repoName.get()));
        }

        if (filterFile != null) {
            try {

                File oldFilterFile = new File(filterFile);
                if (!oldFilterFile.exists()) {
                    throw new FileNotFoundException("No filter file found at " + filterFile + ".");
                }

                repository().context().blobStore().putBlob(Blobs.SPARSE_FILTER_BLOB_KEY,
                        Files.toByteArray(oldFilterFile));
            } catch (Exception e) {
                throw new IllegalStateException("Unable to copy filter file at path " + filterFile
                        + " to the new repository.", e);
            }
        }

        Repository repository;
        try {
            if (!repoExisted) {
                // use a config database appropriate for the kind of repo URI
                try (ConfigDatabase configDB = repoInitializer.resolveConfigDatabase(repoURI,
                        context)) {
                    if (config != null) {
                        effectiveConfigBuilder.putAll(config);
                    }
                    try {
                        for (Entry<String, String> pair : effectiveConfigBuilder.entrySet()) {
                            String key = pair.getKey();
                            String value = pair.getValue();
                            configDB.put(key, value);
                        }
                        repository = repository();
                    } catch (Exception e) {
                        throw new IllegalStateException(
                                "Unable to initialize repository for the first time: "
                                        + e.getMessage(),
                                e);
                    }
                }
            } else {
                repository = repository();
            }
            try {
                repository.open();
                // make sure the repo has the empty tree
                ObjectStore objectDatabase = repository.context().objectDatabase();
                objectDatabase.put(RevTree.EMPTY);
            } catch (RepositoryConnectionException e) {
                throw new IllegalStateException(
                        "Error opening repository databases: " + e.getMessage(), e);
            }
        } catch (ConfigException e) {
            throw e;
        } catch (Exception e) {
            Throwables.throwIfInstanceOf(e, IllegalStateException.class);
            throw new IllegalStateException("Can't access repository at '" + repoURI + "'", e);
        }

        createDefaultRefs(repository);
        return repository;
    }

    private void createDefaultRefs(@NonNull Repository repository) {
        RefDatabase refDatabase = repository.context().refDatabase();
        Map<String, Ref> initialRefs = refDatabase
                .getAllPresent(Arrays.asList(HEAD, MASTER, WORK_HEAD, STAGE_HEAD)).stream()
                .collect(Collectors.toMap(Ref::getName, ref -> ref));

        UpdateRefs updateRefs = repository.context().command(UpdateRefs.class)
                .setReason(initialRefs.isEmpty() ? "init: repository initialization"
                        : "init: repository re-initialization");

        final Ref master = initialRefs.computeIfAbsent(MASTER,
                name -> new Ref(name, ObjectId.NULL));

        ObjectId defaultWorkHead = master.getObjectId().isNull() ? EMPTY_TREE_ID
                : master.getObjectId();
        ObjectId defaultStageHead = defaultWorkHead;

        initialRefs.putIfAbsent(HEAD, new SymRef(HEAD, master));
        initialRefs.putIfAbsent(WORK_HEAD, new Ref(WORK_HEAD, defaultWorkHead));
        initialRefs.putIfAbsent(STAGE_HEAD, new Ref(STAGE_HEAD, defaultStageHead));

        initialRefs.values().forEach(updateRefs::add);
        updateRefs.call();
    }
}
