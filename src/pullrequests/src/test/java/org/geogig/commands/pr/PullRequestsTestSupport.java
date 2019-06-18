/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.geogig.commands.pr;

import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.locationtech.geogig.remotes.CloneOp;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.test.TestData;
import org.locationtech.geogig.test.TestRepository;

import lombok.NonNull;

public class PullRequestsTestSupport extends ExternalResource {

    private TestRepository repositorySupport = new TestRepository();

    public @Override Statement apply(Statement base, Description description) {
        repositorySupport.apply(base, description);
        return super.apply(base, description);
    }

    public @Override void after() {
        repositorySupport.after();
    }

    public TestData newRepo(@NonNull String name) {
        Repository repo = repositorySupport.createRepository(name);
        try {
            TestData repoWorker = new TestData(repo);
            repoWorker.init();
            return repoWorker;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public TestData getRepo(@NonNull String name) {
        Repository repo = repositorySupport.getRepo(name);
        return new TestData(repo);
    }

    public TestData clone(TestData origin, String name) {
        TestData clone = newRepo(name);
        clone.getRepo().command(CloneOp.class).setCloneIndexes(true)
                .setRemoteURI(origin.getRepo().getLocation()).call();
        return clone;
    }
}
