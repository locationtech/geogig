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

import static com.google.common.base.Optional.absent;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.Serializable;
import java.net.URI;
import java.util.Map;
import java.util.Map.Entry;

import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.plumbing.UpdateSymRef;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.RepositoryResolver;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConfigException;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.PluginDefaults;
import org.locationtech.geogig.storage.VersionedFormat;
import org.locationtech.geogig.storage.impl.Blobs;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.inject.Inject;

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

    private Map<String, String> config;

    private String filterFile;

    private Hints hints;

    /**
     * Constructs a new {@code InitOp} with the specified parameters.
     * 
     * @param platform where to get the current directory from
     * @param hints may contain where to get the repository from (using the
     *        {@link Hints#REPOSITORY_URL} argument)
     */
    @Inject
    public InitOp(Hints hints) {
        this.config = Maps.newTreeMap();
        this.hints = hints;
    }

    public InitOp setConfig(Map<String, String> suppliedConfiguration) {
        this.config = ImmutableMap.copyOf(suppliedConfiguration);
        return this;
    }

    /**
     * @deprecated must provide repository URI in {@link Hints} instead
     */
    @Deprecated
    public InitOp setTarget(File targetRepoDirectory) {
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
    @Override
    protected Repository _call() {
        final Platform platform = platform();
        Optional<URI> resolvedURI = new ResolveGeogigURI(platform, hints).call();
        if (!resolvedURI.isPresent()) {
            resolvedURI = Optional.of(platform.pwd().getAbsoluteFile().toURI());
        }

        URI repoURI = resolvedURI.get();

        final RepositoryResolver repoInitializer = RepositoryResolver.lookup(repoURI);
        final boolean repoExisted = repoInitializer.repoExists(repoURI);

        repoInitializer.initialize(repoURI, context());

        Map<String, String> effectiveConfigBuilder = Maps.newTreeMap();
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

                repository().blobStore().putBlob(Blobs.SPARSE_FILTER_BLOB_KEY,
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
                try (ConfigDatabase configDB = repoInitializer.getConfigDatabase(repoURI,
                        context)) {
                    PluginDefaults defaults = context.pluginDefaults();
                    addDefaults(configDB, defaults, effectiveConfigBuilder);
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
                        repository.configure();
                    } catch (RepositoryConnectionException e) {
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
                ObjectStore objectDatabase = repository.objectDatabase();
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

        if (!repoExisted) {
            createDefaultRefs();
        }
        return repository;
    }

    private void addDefaults(ConfigDatabase configDB, PluginDefaults defaults,
            Map<String, String> configProps) {

        final String refsKey = "storage.refs";
        final String objectsKey = "storage.objects";

        final Map<String, String> providedConfig = configDB.getAll();

        Optional<VersionedFormat> refs;
        Optional<VersionedFormat> objects;

        refs = providedConfig.containsKey(refsKey) ? absent() : defaults.getRefs();
        objects = providedConfig.containsKey(objectsKey) ? absent() : defaults.getObjects();

        if (refs.isPresent()) {
            configProps.put(refsKey, refs.get().getFormat());
            configProps.put(refs.get().getFormat() + ".version", refs.get().getVersion());
        }
        if (objects.isPresent()) {
            configProps.put(objectsKey, objects.get().getFormat());
            configProps.put(objects.get().getFormat() + ".version", objects.get().getVersion());
        }
    }

    private void createDefaultRefs() {
        Optional<Ref> master = command(RefParse.class).setName(Ref.MASTER).call();
        Preconditions.checkState(!master.isPresent(), Ref.MASTER + " was already initialized.");
        command(UpdateRef.class).setName(Ref.MASTER).setNewValue(ObjectId.NULL)
                .setReason("Repository initialization").call();

        Optional<Ref> head = command(RefParse.class).setName(Ref.HEAD).call();
        Preconditions.checkState(!head.isPresent(), Ref.HEAD + " was already initialized.");
        command(UpdateSymRef.class).setName(Ref.HEAD).setNewValue(Ref.MASTER)
                .setReason("Repository initialization").call();

        Optional<Ref> workhead = command(RefParse.class).setName(Ref.WORK_HEAD).call();
        Preconditions.checkState(!workhead.isPresent(),
                Ref.WORK_HEAD + " was already initialized.");
        command(UpdateRef.class).setName(Ref.WORK_HEAD).setNewValue(RevTree.EMPTY.getId())
                .setReason("Repository initialization").call();

        Optional<Ref> stagehead = command(RefParse.class).setName(Ref.STAGE_HEAD).call();
        Preconditions.checkState(!stagehead.isPresent(),
                Ref.STAGE_HEAD + " was already initialized.");
        command(UpdateRef.class).setName(Ref.STAGE_HEAD).setNewValue(RevTree.EMPTY.getId())
                .setReason("Repository initialization").call();

    }
}
