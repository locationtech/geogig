package org.locationtech.geogig.test;

import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryFinder;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.repository.impl.PluginsContextBuilder;
import org.locationtech.geogig.storage.memory.HeapConfigDatabase;

import lombok.NonNull;

public class TestRepository extends ExternalResource {

    private Map<URI, Repository> repositories = new HashMap<>();

    private String testClassName;

    private String testMethodName;

    private TestPlatform platform;

    public @Override Statement apply(Statement base, Description description) {
        this.testClassName = description.getClassName();
        this.testMethodName = description.getMethodName();
        return super.apply(base, description);
    }

    protected @Override void before() throws Throwable {
        File tmp = new File(System.getProperty("java.io.tmpdir"));
        platform = new TestPlatform(tmp, tmp);
    }

    /**
     * Override to tear down your specific external resource.
     */
    protected @Override void after() {
        repositories.values().forEach(TestRepository::closeAndDelete);
        HeapConfigDatabase.clearGlobal();
    }

    public String getTestMethodName() {
        return testMethodName;
    }

    public Platform getPlatform() {
        return platform;
    }

    public Repository repository() {
        URI uri = buildRepoURI();
        return repositories.computeIfAbsent(uri, this::createRepository);
    }

    public Repository createRepository(@NonNull URI uri) {
        Hints hints = Hints.repository(uri).platform(platform);
        Context context = new PluginsContextBuilder().build(hints);
        Repository repository = new GeoGIG(context).getOrCreateRepository();
        return repository;
    }

    private URI buildRepoURI() {
        return URI.create(String.format("memory://%s/%s", testClassName, testMethodName));
    }

    public static void closeAndDelete(Repository repo) {
        if (repo != null) {
            URI repoURI = repo.getLocation();
            try {
                repo.close();
            } finally {
                try {
                    RepositoryFinder.INSTANCE.lookup(repoURI).delete(repoURI);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
