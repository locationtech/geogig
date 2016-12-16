/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.test.functional;

import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.impl.GlobalContextBuilder;
import org.locationtech.geogig.test.TestPlatform;

import com.google.common.base.Preconditions;

import cucumber.api.java.After;
import cucumber.api.java.Before;
import cucumber.runtime.java.StepDefAnnotation;

/**
 * A global state that allows to set up repositories for cucumber functional tests, and can be
 * shared among step definition classes, without using extra dependencies like
 * {@code cucumber-guice} or {@code cucumber-picocontainer}.
 * <p>
 * Cucumber step definition classes (annotated with {@link StepDefAnnotation @StepDefAnnotation})
 * can share a single instance of this class for each test scenario by acquiring it through the
 * {@link #get()} static method, which uses a {@link ThreadLocal} variable to hold on the thread
 * singleton. (Note: coulnd't make {@code cucumber-picocontainer} nor {@code cucumnber-guice} as
 * expected, hence this solution).
 * <p>
 * The acquired instance shall be initialized and disposed through {@link #before()} and
 * {@link #dispose()}, respectively, which are both idempotent; and shall be called by any step
 * definition class method annotated with {@link Before @cucumber.api.java.Before} and
 * {@link After @cucumber.api.java.After}.
 */
public class CLIContextProvider {

    private static ThreadLocal<CLIContextProvider> threadLocal = new ThreadLocal<CLIContextProvider>() {
        @Override
        protected CLIContextProvider initialValue() {
            return new CLIContextProvider();
        }
    };

    /**
     * Returns the test case's singleton {@link CLIContextProvider}
     */
    public static CLIContextProvider get() {
        return threadLocal.get();
    }

    public TemporaryFolder tempFolder;

    private File homeDir;

    private File workingDir;

    private Map<String, CLIContext> repositories = new HashMap<>();

    private TestRepoURIBuilder URIBuilder;

    public TestRepoURIBuilder getURIBuilder() {
        return URIBuilder;
    }

    /**
     * Sets the URI builder used to create repository URI's for the {@link CLIContext}s created by
     * this provider.
     * <p>
     * This allows to run the same set of functional tests against different kinds of repository
     * backends, by providing a URI builder that creates the appropriate type of URI (e.g.
     * {@code file://} or {@code postgresql://} URI's).
     * <p>
     * NOTE this method must be called before {@link #before()}, sorry for the temporal coupling.
     */
    public void setURIBuilder(TestRepoURIBuilder uriBuilder) {
        this.URIBuilder = uriBuilder;
    }

    /**
     * Initializes this context provider to set up the initial context (temporary folder with
     * "userhome" and "data" directories to be used as the repositories home and workingdir folders
     * respectively; initialization of the {@link GlobalContextBuilder} to use
     * {@link CLITestContextBuilder}, and {@link TestRepoURIBuilder#before()} initialization in case
     * it needs to set up temporary resources.
     * <p>
     * This mehtod is idempotent, only the first call makes effect, hence it's safe to call it from
     * any step definition class where it's used.
     */
    public synchronized void before() throws Throwable {
        if (tempFolder != null) {
            return;
        }
        URIBuilder.before();

        tempFolder = new TemporaryFolder();
        tempFolder.create();
        TestFeatures.setupFeatures();

        this.homeDir = tempFolder.newFolder("userhome");
        this.workingDir = tempFolder.newFolder("data");

        CLITestContextBuilder testContextBuilder = new CLITestContextBuilder(
                new TestPlatform(workingDir, homeDir));
        GlobalContextBuilder.builder(testContextBuilder);
    }

    /**
     * {@link CLIContext#dispose() disposes} all repository contexts created by this provider, and
     * calls {@link TestRepoURIBuilder#after()} for it to release resources like database
     * connections or temporary files.
     * <p>
     * This mehtod is idempotent, only the first call makes effect, hence it's safe to call it from
     * any step definition class where it's used.
     */
    public synchronized void after() {
        if (tempFolder == null) {
            return;
        }
        try {
            URIBuilder.after();
        } finally {
            try {
                repositories.values().forEach((c) -> c.dispose());
                repositories.clear();
            } finally {
                if (tempFolder != null) {
                    try {
                        tempFolder.delete();
                    } finally {
                        tempFolder = null;
                        threadLocal.remove();
                    }
                }
            }
        }
        System.gc();
        System.runFinalization();

    }

    /**
     * Returns the repository context for the repo named after {@code name}, fails with an
     * {@link IllegalStateException} if no such context exists.
     */
    public CLIContext getRepositoryContext(String name) {
        CLIContext context = repositories.get(name);
        Preconditions.checkState(context != null, "Repository %s does not exist", name);
        return context;
    }

    /**
     * Obtains or creates the repository context for the repository named after {@code repoName}
     * <p>
     * The context's {@link Platform} is initialized to point to this provider's temporaty folder
     * home and working dir directories , and it's global config database is initialized with
     * default {@code user.name} and {@code user.email} values.
     */
    public CLIContext getOrCreateRepositoryContext(final String repoName) throws Exception {
        CLIContext context = this.repositories.get(repoName);
        if (context == null) {
            File homeDirectory = this.homeDir;
            File workDir = this.workingDir;
            TestPlatform platform = new TestPlatform(workDir, homeDirectory);

            URI repoURI = URIBuilder.newRepositoryURI(repoName, platform);
            context = new CLIContext(repoURI, platform);
            context.geogigCLI.execute("config", "--global", "user.name", "gabriel");
            context.geogigCLI.execute("config", "--global", "user.email", "gabriel@example.com");
            repositories.put(repoName, context);
        }
        return context;
    }

}
