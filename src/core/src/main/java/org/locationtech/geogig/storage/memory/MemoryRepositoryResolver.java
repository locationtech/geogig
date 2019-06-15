/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.memory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.RepositoryResolver;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.repository.impl.GlobalContextBuilder;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.storage.decorator.ForwardingConfigDatabase;
import org.locationtech.geogig.storage.decorator.ForwardingConflictsDatabase;
import org.locationtech.geogig.storage.decorator.ForwardingIndexDatabase;
import org.locationtech.geogig.storage.decorator.ForwardingObjectDatabase;
import org.locationtech.geogig.storage.decorator.ForwardingRefDatabase;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import lombok.EqualsAndHashCode;
import lombok.NonNull;

public class MemoryRepositoryResolver implements RepositoryResolver {

    private @EqualsAndHashCode static class StorageSet {

        private transient ConfigDatabase config;

        private transient ObjectDatabase objects;

        private transient IndexDatabase index;

        private transient RefDatabase refs;

        private transient ConflictsDatabase conflicts;

        private URI uri;

        public StorageSet(URI uri) {
            this.uri = uri;
            config = new HeapConfigDatabase();
            objects = new HeapObjectDatabase();
            index = new HeapIndexDatabase();
            refs = new HeapRefDatabase();
            conflicts = new HeapConflictsDatabase();
            config.open();
            objects.open();
            index.open();
            conflicts.open();
            refs.open();
        }

        public ConfigDatabase config(boolean readOnly) {
            return new ConfigDatabaseDecorator(config, readOnly);
        }

        public ObjectDatabase objects(boolean readOnly) {
            return new ObjectDatabaseDecorator(objects, readOnly);
        }

        public IndexDatabase index(boolean readOnly) {
            return new IndexDatabaseDecorator(index, readOnly);
        }

        public RefDatabase refs(boolean readOnly) {
            return new RefDatabaseDecorator(refs, readOnly);
        }

        public ConflictsDatabase conflicts(boolean readOnly) {
            return new ConflictsDatabaseDecorator(conflicts, readOnly);
        }
    }

    private static ConcurrentMap<URI, StorageSet> STORES = new ConcurrentHashMap<>();

    public static @VisibleForTesting void reset() {
        STORES.clear();
        HeapConfigDatabase.clearGlobal();
    }

    private StorageSet getStores(@NonNull URI repoURI) {
        Preconditions.checkArgument(canHandle(repoURI));
        StorageSet stores = STORES.computeIfAbsent(repoURI, StorageSet::new);
        return stores;
    }

    public @Override boolean canHandle(@NonNull URI repoURI) {
        return canHandleURIScheme(repoURI.getScheme()) && !Strings.isNullOrEmpty(repoURI.getHost());
    }

    public @Override boolean canHandleURIScheme(String scheme) {
        return "memory".equals(scheme);
    }

    public @Override boolean repoExists(@NonNull URI repoURI) {
        return STORES.containsKey(repoURI);
    }

    public @Override URI buildRepoURI(@NonNull URI rootRepoURI, @NonNull String repoName) {
        try {
            String base = new URI(rootRepoURI.getScheme(), rootRepoURI.getHost(),
                    rootRepoURI.getPath(), null).toString() + "/";
            return URI.create(base + repoName);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public @Override List<String> listRepoNamesUnderRootURI(@NonNull URI rootRepoURI) {
        Preconditions.checkArgument(canHandleURIScheme(rootRepoURI.getScheme()));
        final String base;
        try {
            base = new URI(rootRepoURI.getScheme(), rootRepoURI.getHost(), rootRepoURI.getPath(),
                    null).toString() + "/";
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        List<String> childNames = STORES.keySet().stream()
                .filter(uri -> uri.toString().startsWith(base)).map(this::getName)
                .collect(Collectors.toList());
        return childNames;
    }

    public @Override String getName(@NonNull URI repoURI) {
        Preconditions.checkArgument(canHandle(repoURI));
        String[] segments = repoURI.getPath().split("/");
        return segments[segments.length - 1];
    }

    public @Override void initialize(@NonNull URI repoURI, @NonNull Context repoContext)
            throws IllegalArgumentException {

        this.getStores(repoURI);
    }

    public @Override Repository open(@NonNull URI repositoryURI)
            throws RepositoryConnectionException {
        return open(repositoryURI, Hints.readWrite());
    }

    public @Override Repository open(@NonNull URI repositoryURI, @NonNull Hints hints)
            throws RepositoryConnectionException {

        Preconditions.checkArgument(canHandle(repositoryURI), "Not a memory repository: %s",
                repositoryURI.getScheme());

        if (!repoExists(repositoryURI)) {
            throw new RepositoryConnectionException(
                    "The provided location is not a geogig repository");
        }

        Context context = GlobalContextBuilder.builder().build(hints.uri(repositoryURI));
        GeoGIG geoGIG = new GeoGIG(context);

        Repository repository = geoGIG.getRepository();
        repository.open();

        return repository;
    }

    public @Override boolean delete(@NonNull URI repositoryLocation) throws Exception {
        Preconditions.checkArgument(canHandle(repositoryLocation), "Not a memory repository: %s",
                repositoryLocation);

        StorageSet removed = STORES.remove(repositoryLocation);
        return removed != null;
    }

    public @Override ConfigDatabase resolveConfigDatabase(@NonNull URI repoURI,
            @NonNull Context repoContext, boolean rootUri) {
        return getStores(repoURI).config(false);
    }

    public @Override ObjectDatabase resolveObjectDatabase(@NonNull URI repoURI, Hints hints) {
        return getStores(repoURI).objects(Hints.isRepoReadOnly(hints));
    }

    public @Override IndexDatabase resolveIndexDatabase(@NonNull URI repoURI, Hints hints) {
        return getStores(repoURI).index(Hints.isRepoReadOnly(hints));
    }

    public @Override RefDatabase resolveRefDatabase(@NonNull URI repoURI, Hints hints) {
        return getStores(repoURI).refs(Hints.isRepoReadOnly(hints));
    }

    public @Override ConflictsDatabase resolveConflictsDatabase(@NonNull URI repoURI, Hints hints) {
        return getStores(repoURI).conflicts(Hints.isRepoReadOnly(hints));
    }

    private static class ConfigDatabaseDecorator extends ForwardingConfigDatabase {
        private boolean open, ro;

        public ConfigDatabaseDecorator(@NonNull ConfigDatabase odb, boolean readOnly) {
            super(odb);
            this.ro = readOnly;
        }

        public @Override boolean isReadOnly() {
            return ro;
        }

        public @Override boolean isOpen() {
            return open;
        }

        public @Override void open() {
            open = true;
        }

        public @Override void close() {
            open = false;
        }
    }

    private static class ObjectDatabaseDecorator extends ForwardingObjectDatabase {
        private boolean open, ro;

        public ObjectDatabaseDecorator(@NonNull ObjectDatabase odb, boolean readOnly) {
            super(odb);
            this.ro = readOnly;
        }

        public @Override boolean isReadOnly() {
            return ro;
        }

        public @Override boolean isOpen() {
            return open;
        }

        public @Override void open() {
            open = true;
        }

        public @Override void close() {
            open = false;
        }
    }

    private static class IndexDatabaseDecorator extends ForwardingIndexDatabase {
        private boolean open, ro;

        public IndexDatabaseDecorator(IndexDatabase actual, boolean readOnly) {
            super(actual);
        }

        public @Override boolean isReadOnly() {
            return ro;
        }

        public @Override boolean isOpen() {
            return open;
        }

        public @Override void open() {
            open = true;
        }

        public @Override void close() {
            open = false;
        }
    }

    private static class RefDatabaseDecorator extends ForwardingRefDatabase {
        private boolean open, ro;

        public RefDatabaseDecorator(RefDatabase actual, boolean readOnly) {
            super(actual);
        }

        public @Override boolean isReadOnly() {
            return ro;
        }

        public @Override boolean isOpen() {
            return open;
        }

        public @Override void open() {
            open = true;
        }

        public @Override void close() {
            open = false;
        }
    }

    private static class ConflictsDatabaseDecorator extends ForwardingConflictsDatabase {
        private boolean open, ro;

        public ConflictsDatabaseDecorator(ConflictsDatabase actual, boolean readOnly) {
            super(actual);
        }

        public @Override boolean isReadOnly() {
            return ro;
        }

        public @Override boolean isOpen() {
            return open;
        }

        public @Override void open() {
            open = true;
        }

        public @Override void close() {
            open = false;
        }
    }

}
