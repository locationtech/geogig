package org.locationtech.geogig.repository;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.stream.Collectors;

import org.locationtech.geogig.base.Preconditions;
import org.locationtech.geogig.model.ServiceFinder;
import org.locationtech.geogig.storage.ConfigDatabase;

import com.google.common.annotations.VisibleForTesting;

import lombok.NonNull;

public class RepositoryFinder {

    /**
     * System property key for specifying disabled resolvers.
     */
    private static final String DISABLE_RESOLVER_KEY = "disableResolvers";

    /**
     * List of disabled resolver class names.
     */
    private static final List<String> DISABLED_RESOLVERS = new ArrayList<>();

    static {
        // look for a System property of disabled resolvers
        String disabledResolvers = System.getProperty(DISABLE_RESOLVER_KEY);
        if (disabledResolvers != null) {
            String[] disabledResolverArray = disabledResolvers.split(",");
            DISABLED_RESOLVERS.addAll(Arrays.asList(disabledResolverArray));
        }
    }

    public static RepositoryFinder INSTANCE = new RepositoryFinder();

    public @NonNull Repository createRepository(@NonNull URI uri)
            throws RepositoryConnectionException {
        return createRepository(uri, Hints.readWrite());
    }

    public @NonNull Repository createRepository(final @NonNull URI uri, @NonNull Hints hints)
            throws RepositoryConnectionException {

        hints.uri(uri);
        RepositoryResolver resolver = lookup(uri);
        ContextBuilder contextBuilder = new ServiceFinder().lookupService(ContextBuilder.class);
        Context context = contextBuilder.build(hints);
        resolver.initialize(uri);
        Repository repository = context.repository();
        repository.open();
        return repository;
    }

    /**
     * Finds a {@code RepositoryResolver} that {@link #canHandle(URI) can handle} the given URI, or
     * throws an {@code IllegalArgumentException} if no such initializer can be found.
     * <p>
     * The lookup method uses the standard JAVA SPI (Service Provider Interface) mechanism, by which
     * all the {@code META-INF/services/org.locationtech.geogig.repository.RepositoryResolver} files
     * in the classpath will be scanned for fully qualified names of implementing classes.
     * 
     * @param repoURI Repository location URI
     * @return A RepositoryResolver that can handle the supplied URI.
     * @throws IllegalArgumentException if no repository resolver is found capable of handling the
     *         given URI
     */
    public @NonNull RepositoryResolver lookup(@NonNull URI repoURI)
            throws IllegalArgumentException {
        List<RepositoryResolver> resolvers = lookupResolvers();
        RepositoryResolver resolver = null;
        for (RepositoryResolver resolverImpl : resolvers) {
            final String resolverClassName = resolverImpl.getClass().getName();
            if (!DISABLED_RESOLVERS.contains(resolverClassName)
                    && resolverImpl.canHandleURIScheme(repoURI.getScheme())) {
                resolver = resolverImpl;
                break;
            }
        }
        Preconditions.checkArgument(resolver != null,
                "No repository initializer found capable of handling this kind of URI: %s",
                repoURI.getScheme());

        Preconditions.checkArgument(resolver.canHandle(repoURI),
                "RepositoryResolver %s can't handle the provided URI",
                resolver.getClass().getSimpleName());
        return resolver;
    }

    /**
     * Determines if any {@code RepositoryResolver}s are able to handle a given URI Scheme.
     * <p>
     * The lookup method uses the standard JAVA SPI (Service Provider Interface) mechanism, by which
     * all the {@code META-INF/services/org.locationtech.geogig.repository.RepositoryResolver} files
     * in the classpath will be scanned for fully qualified names of implementing classes.
     * 
     * @param scheme the URI scheme
     * @return {@code true} if any resolver could handle the scheme, {@code false} otherwise
     */
    public boolean resolverAvailableForURIScheme(@NonNull String scheme) {
        List<RepositoryResolver> resolvers = lookupResolvers();
        for (RepositoryResolver resolverImpl : resolvers) {
            final String resolverClassName = resolverImpl.getClass().getName();
            if (!DISABLED_RESOLVERS.contains(resolverClassName)
                    && resolverImpl.canHandleURIScheme(scheme)) {
                return true;
            }
        }
        return false;
    }

    public List<RepositoryResolver> lookupResolvers() {

        List<RepositoryResolver> resolvers = ServiceLoader.load(RepositoryResolver.class).stream()
                .map(Provider::get).collect(Collectors.toList());
        if (resolvers.isEmpty()) {
            ClassLoader classLoader = RepositoryResolver.class.getClassLoader();
            resolvers = ServiceLoader.load(RepositoryResolver.class, classLoader).stream()
                    .map(Provider::get).collect(Collectors.toList());
        }
        return resolvers;
    }

    /**
     * Resolve the config database using the provided parameters.
     * 
     * @param repoURI the repository URI
     * @param repoContext the repository context
     * @param rootUri {@code true} if {@code repoURI} represents a root URI to a group of
     *        repositories
     * @return the config database
     */
    public ConfigDatabase resolveConfigDatabase(URI repoURI, Context repoContext, boolean rootUri) {
        RepositoryResolver initializer = RepositoryFinder.INSTANCE.lookup(repoURI);
        return initializer.resolveConfigDatabase(repoURI, repoContext, rootUri);
    }

    public URI resolveRepoUriFromString(Platform platform, String repoURI)
            throws URISyntaxException {
        URI uri;

        uri = new URI(repoURI.replace('\\', '/').replaceAll(" ", "%20"));

        String scheme = uri.getScheme();
        if (null == scheme) {
            Path p = Paths.get(repoURI);
            if (p.isAbsolute()) {
                uri = p.toUri();
            } else {
                uri = new File(platform.pwd(), repoURI).toURI();
            }
        } else if ("file".equals(scheme)) {
            File f = new File(uri);
            if (!f.isAbsolute()) {
                uri = new File(platform.pwd(), repoURI).toURI();
            }
        }
        return uri;
    }

    /**
     * @param repositoryURI the URI with the location of the repository to load
     * @return a {@link Repository} loaded from the given URI, already {@link Repository#open()
     *         open}
     * @throws IllegalArgumentException if no registered {@link RepositoryResolver} implementation
     *         can load the repository at the given location
     * @throws RepositoryConnectionException if the repository can't be opened
     */
    public @NonNull Repository open(@NonNull URI repositoryURI)
            throws RepositoryConnectionException {
        return open(repositoryURI, Hints.readWrite());
    }

    public @NonNull Repository open(@NonNull URI repositoryURI, @NonNull Hints hints)
            throws RepositoryConnectionException {
        RepositoryResolver initializer = RepositoryFinder.INSTANCE.lookup(repositoryURI);
        Repository repository = initializer.open(repositoryURI, hints);
        return repository;
    }

    /**
     * Sets or overrides the list of disabled resolvers.
     *
     * @param disabledResolvers List of class names of RepositoryResolver implementations that
     *        should be disabled.
     *        <p>
     *        Example: "org.locationtech.geogig.repository.impl.FileRepositoryResolver" to disable
     *        the File/Directory resolver for URI scheme "file".
     */
    @VisibleForTesting
    void setDisabledResolvers(List<String> disabledResolvers) {
        // clear any existing disabled resolvers
        clearDisabledResolvers();
        if (disabledResolvers != null) {
            DISABLED_RESOLVERS.addAll(disabledResolvers);
        }
    }

    /**
     * Clears the list of disabled RepositoryResolvers.
     */
    @VisibleForTesting
    void clearDisabledResolvers() {
        // clear any existing disabled resolvers
        DISABLED_RESOLVERS.clear();
    }

}
