package org.locationtech.geogig.test;

import java.io.File;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.locationtech.geogig.porcelain.ConfigOp;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.porcelain.InitOp;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.RepositoryFinder;
import org.locationtech.geogig.storage.memory.MemoryRepositoryResolver;

import com.google.common.base.Preconditions;

import lombok.NonNull;

public class TestRepository extends ExternalResource {

    private Map<URI, Repository> repositories = new HashMap<>();

    private String testClassName;

    private String testMethodName;

    private TestPlatform platform;

    public @Override Statement apply(Statement base, Description description) {
        String className = description.getClassName();
        this.testClassName = className.substring(className.lastIndexOf('.') + 1);
        this.testMethodName = description.getMethodName();
        return super.apply(base, description);
    }

    /**
     * Override to tear down your specific external resource.
     */
    protected @Override void after() {
        Collection<Repository> repos = repositories.values();
        for (Repository repo : repos) {
            closeAndDelete(repo);
        }
        MemoryRepositoryResolver.reset();
    }

    public String getTestMethodName() {
        return testMethodName;
    }

    public TestPlatform getPlatform() {
        if (platform == null) {
            File tmp = new File(System.getProperty("java.io.tmpdir"));
            platform = new TestPlatform(tmp, tmp);
        }
        return platform;
    }

    public Repository repository() {
        URI uri = getRepoURI();
        return repositories.computeIfAbsent(uri, this::createAndInitRepository);
    }

    public Repository createAndInitRepository(@NonNull String name) {
        return createAndInitRepository(getRepoURI(name));
    }

    public Repository createAndInitRepository(@NonNull URI uri) {
        Repository repository = createRepository(uri);
        repository.command(InitOp.class).call();
        ConfigOp configOp = repository.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET);
        configOp.setName("user.name").setValue("groldan").call();
        configOp.setName("user.email").setValue("groldan@example.com").call();
        return repository;
    }

    public Repository createRepository(@NonNull String name) {
        return createRepository(getRepoURI(name));
    }

    public Repository createRepository(@NonNull URI uri) {
        Preconditions.checkState(!this.repositories.containsKey(uri),
                "Repository %s already exists", uri);
        Repository repository;
        try {
            Hints hints = new Hints().platform(getPlatform());
            repository = RepositoryFinder.INSTANCE.createRepository(uri, hints);
        } catch (RepositoryConnectionException e) {
            throw new RuntimeException(e);
        }
        this.repositories.put(uri, repository);
        return repository;
    }

    public URI getRepoURI() {
        return getRepoURI("repo");
    }

    private Function<String, URI> uriBuilder = repositoryName -> URI.create(
            String.format("memory://%s/%s#%s", testClassName, testMethodName, repositoryName));

    public void setURIBuilder(@NonNull Function<String, URI> uriBuilder) {
        this.uriBuilder = uriBuilder;
    }

    public URI getRepoURI(@NonNull String repositoryName) {
        return uriBuilder.apply(repositoryName);
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
