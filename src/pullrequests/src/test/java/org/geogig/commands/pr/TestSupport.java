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

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.porcelain.InitOp;
import org.locationtech.geogig.remotes.CloneOp;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.GlobalContextBuilder;
import org.locationtech.geogig.test.TestData;

import com.google.common.base.Preconditions;

import lombok.NonNull;

public class TestSupport extends ExternalResource {

    private TemporaryFolder tmp;

    private Map<String, TestData> repos;

    public @Override void before() throws IOException {
        tmp = new TemporaryFolder();
        tmp.create();
        repos = new HashMap<>();
    }

    public @Override void after() {
        repos.values().forEach(r -> r.getRepo().close());
        tmp.delete();
    }

    public TestData newRepo(@NonNull String name) {
        Preconditions.checkState(!repos.containsKey(name));
        URI uri;
        try {
            uri = tmp.newFolder(name).toURI();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Hints hints = Hints.readWrite().uri(uri);
        hints.set(Hints.REPOSITORY_NAME, name);
        Context context = GlobalContextBuilder.builder().build(hints);
        Repository repo = context.command(InitOp.class).call();
        try {
            repo.open();
            TestData repoWorker = new TestData(repo);
            repoWorker.init();
            repos.put(name, repoWorker);
            return getRepo(name);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public TestData getRepo(@NonNull String name) {
        TestData testData = repos.get(name);
        Preconditions.checkNotNull(testData);
        return testData;
    }

    public TestData clone(TestData origin, String name) {
        TestData clone = newRepo(name);
        clone.getRepo().command(CloneOp.class).setCloneIndexes(true).setRemoteURI(origin.getRepo().getLocation()).call();
        return clone;
    }
}
