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

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.locationtech.geogig.base.Preconditions;
import org.locationtech.geogig.dsl.Geogig;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.RepositoryResolver;
import org.locationtech.geogig.repository.impl.GlobalContextBuilder;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConfigException;
import org.locationtech.geogig.storage.ConfigException.StatusCode;
import org.locationtech.geogig.storage.ConfigStore;
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
import org.locationtech.geogig.base.Strings;

import lombok.EqualsAndHashCode;
import lombok.NonNull;

/**
 * {@link RepositoryResolver} to handle fully on-heap, non-persistent, repositories, especially for
 * testing.
 * <p>
 * The repository URI format is {@code memory://<memory context>[parent context]#<repository name>},
 * for example: {@code memory://my_test/#repo1},
 * {@code memory://some.context.io/myTestName#RepositoryName}
 * <p>
 * That is, the URI schema is {@code memory}, the {@code host} portion of the URI defines a full
 * memory context, with its own global {@link ConfigStore}, so two memory URI's with different
 * context names won't share the global config. The URI {@link URI#getPath() path} allows to set a
 * user defined parent context in order to be able of creating a nested structure like a file
 * system, and the URI {@link URI#getFragment() fragment} defines the repository name.
 */
public class MemoryRepositoryResolver implements RepositoryResolver {

    static @VisibleForTesting @EqualsAndHashCode class StorageSet {

        private transient ConfigDatabase config;

        private transient ObjectDatabase objects;

        private transient IndexDatabase index;

        private transient RefDatabase refs;

        private transient ConflictsDatabase conflicts;

        private URI uri;

        public StorageSet(@NonNull URI uri, @NonNull ConfigStore globalConfig) {
            this.uri = uri;
            config = new HeapConfigDatabase(globalConfig);
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

        public ConfigDatabase config(boolean readOnly, boolean globalOnly) {
            return new ConfigDatabaseDecorator(config, readOnly, globalOnly);
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

    static @VisibleForTesting class MemoryContext {
        private final String name;

        private HeapConfigStore globalConfig = new HeapConfigStore();

        ConcurrentMap<URI, StorageSet> stores = new ConcurrentHashMap<>();

        MemoryContext(String name) {
            this.name = name;
            this.globalConfig.open();
        }

        StorageSet getOrCreateRepository(@NonNull URI repoURI) {
            return stores.computeIfAbsent(repoURI, uri -> new StorageSet(uri, globalConfig));
        }

        final @VisibleForTesting StorageSet getOrCreateRepository(
                @NonNull String nameOrPathAndFragment) {
            URI repoURI = URI
                    .create(String.format("memory://%s/%s", this.name, nameOrPathAndFragment))
                    .normalize();
            return stores.computeIfAbsent(repoURI, uri -> new StorageSet(uri, globalConfig));
        }

        public StorageSet remove(@NonNull URI repository) {
            return stores.remove(repository);
        }

        public void clear() {
            stores.clear();
            globalConfig.clear();
        }

        public boolean contains(@NonNull URI repoURI) {
            return stores.containsKey(repoURI);
        }
    }

    private static final ConcurrentMap<String, MemoryContext> CONTEXTS = new ConcurrentHashMap<>();

    public static @VisibleForTesting void removeContext(@NonNull String context) {
        MemoryContext ctx = CONTEXTS.remove(context);
        if (ctx != null) {
            ctx.clear();
        }
    }

    public static void removeAllContexts() {
        List<MemoryContext> contexts = new ArrayList<>(CONTEXTS.values());
        CONTEXTS.clear();
        contexts.forEach(MemoryContext::clear);
    }

    private static String parseContext(@NonNull URI uri) {
        return uri.getHost();
    }

    static @VisibleForTesting Optional<MemoryContext> getContext(@NonNull String context) {
        String cname = encodeName(context);
        return Optional.ofNullable(CONTEXTS.get(cname));
    }

    static @VisibleForTesting Optional<MemoryContext> getContext(@NonNull URI uri) {
        return Optional.ofNullable(CONTEXTS.get(parseContext(uri)));
    }

    static @VisibleForTesting MemoryContext getOrCreateContext(@NonNull URI uri) {
        String cname = parseContext(uri);
        return CONTEXTS.computeIfAbsent(cname, MemoryContext::new);
    }

    static @VisibleForTesting MemoryContext getOrCreateContext(@NonNull String context) {
        String cname = encodeName(context);
        return CONTEXTS.computeIfAbsent(cname, MemoryContext::new);
    }

    private StorageSet getStores(@NonNull URI repoURI) {
        Preconditions.checkArgument(canHandle(repoURI));
        MemoryContext context = getOrCreateContext(repoURI);
        return context.getOrCreateRepository(repoURI);
    }

    public @Override boolean canHandle(@NonNull URI repoURI) {
        String scheme = repoURI.getScheme();
        String host = repoURI.getHost();
        return canHandleURIScheme(scheme) && !Strings.isNullOrEmpty(host);
    }

    public @Override boolean canHandleURIScheme(String scheme) {
        return "memory".equals(scheme);
    }

    public @Override boolean repoExists(@NonNull URI repoURI) {
        MemoryContext context = CONTEXTS.get(parseContext(repoURI));
        return context != null && context.contains(repoURI);
    }

    public @Override URI buildRepoURI(@NonNull URI rootRepoURI, @NonNull String repoName) {
        try {
            String path = rootRepoURI.getPath();
            if (path.length() > 1 && !path.endsWith("/")) {
                path += "/";
            }
            return new URI(rootRepoURI.getScheme(), rootRepoURI.getHost(), path, repoName)
                    .normalize();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public URI createRootURI(@NonNull String contextName) {
        final URI base;
        try {
            String scheme = "memory";
            String host = encodeName(contextName);
            String path = "/";
            String fragment = null;
            base = new URI(scheme, host, path, fragment);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return base;
    }

    private static String encodeName(String contextName) {
        String host;
        try {
            host = URLEncoder.encode(contextName.trim(), "UTF-8").replaceAll("\\+", "");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        return host;
    }

    public @Override URI getRootURI(@NonNull URI repoURI) {
        Preconditions.checkArgument(canHandleURIScheme(repoURI.getScheme()));
        final URI base;
        try {
            String scheme = repoURI.getScheme();
            String host = repoURI.getHost();
            String path = repoURI.getPath();
            String fragment = null;
            base = new URI(scheme, host, path, fragment).normalize();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        return base;
    }

    public @Override List<String> listRepoNamesUnderRootURI(@NonNull URI rootRepoURI) {
        Preconditions.checkArgument(canHandleURIScheme(rootRepoURI.getScheme()));
        MemoryContext context = getContext(rootRepoURI).orElse(null);
        if (context == null) {
            return Collections.emptyList();
        }
        final String base;
        try {
            String scheme = rootRepoURI.getScheme();
            String host = rootRepoURI.getHost();
            String path = rootRepoURI.getPath();
            String fragment = rootRepoURI.getFragment();
            base = new URI(scheme, host, path, fragment).toString() + "#";
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        ConcurrentMap<URI, StorageSet> stores = context.stores;

        List<String> childNames = stores.keySet().stream()
                .filter(uri -> uri.toString().startsWith(base)).map(this::getName)
                .collect(Collectors.toList());
        return childNames;
    }

    public @Override String getName(@NonNull URI repoURI) {
        Preconditions.checkArgument(canHandle(repoURI));
        String repoName = repoURI.getFragment();
        return repoName;
    }

    public @Override void initialize(@NonNull URI repoURI) throws IllegalArgumentException {

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
        Geogig geoGIG = Geogig.of(context);

        Repository repository = geoGIG.getRepository();
        repository.open();

        return repository;
    }

    public @Override boolean delete(@NonNull URI repositoryLocation) throws Exception {
        Preconditions.checkArgument(canHandle(repositoryLocation), "Not a memory repository: %s",
                repositoryLocation);
        Optional<MemoryContext> context = getContext(repositoryLocation);
        StorageSet removed = null;
        if (context.isPresent()) {
            removed = context.get().remove(repositoryLocation);
        }
        return removed != null;
    }

    public @Override ConfigDatabase resolveConfigDatabase(@NonNull URI repoURI,
            @NonNull Context repoContext, boolean rootUri) {
        boolean readOnly = Hints.isRepoReadOnly(repoContext.hints());
        return getStores(repoURI).config(readOnly, rootUri);
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

    private static class RefDatabaseDecorator extends ForwardingRefDatabase {
        private boolean open, ro;

        public RefDatabaseDecorator(RefDatabase actual, boolean readOnly) {
            super(actual);
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

    private static class ConflictsDatabaseDecorator extends ForwardingConflictsDatabase {
        private boolean open, ro;

        public ConflictsDatabaseDecorator(ConflictsDatabase actual, boolean readOnly) {
            super(actual);
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

    private static class ConfigDatabaseDecorator extends ForwardingConfigDatabase {
        private boolean open, ro, globalOnly;

        public ConfigDatabaseDecorator(@NonNull ConfigDatabase odb, boolean readOnly,
                boolean globalOnly) {
            super(odb);
            this.ro = readOnly;
            this.globalOnly = globalOnly;
        }

        private void checkLocal() {
            if (this.globalOnly) {
                throw new ConfigException(StatusCode.INVALID_LOCATION);
            }
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

        public @Override Optional<String> get(String key) {
            checkLocal();
            return super.get(key);
        }

        public @Override <T> Optional<T> get(String key, Class<T> c) {
            checkLocal();
            return super.get(key, c);
        }

        public @Override Map<String, String> getAll() {
            checkLocal();
            return super.getAll();
        }

        public @Override Map<String, String> getAllSection(String section) {
            checkLocal();
            return super.getAllSection(section);
        }

        public @Override List<String> getAllSubsections(String section) {
            checkLocal();
            return super.getAllSubsections(section);
        }

        public @Override void put(String key, Object value) {
            checkLocal();
            super.put(key, value);
        }

        public @Override void putSection(@NonNull String section,
                @NonNull Map<String, String> kvp) {
            checkLocal();
            super.putSection(section, kvp);
        }

        public @Override void remove(String key) {
            checkLocal();
            super.remove(key);
        }

        public @Override void removeSection(String key) {
            checkLocal();
            super.removeSection(key);
        }
    }
}
