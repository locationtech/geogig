/* Copyright (c) 2012-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing.remotes;

import java.util.List;
import java.util.Optional;

import org.locationtech.geogig.plumbing.remotes.RemoteException.StatusCode;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.storage.ConfigDatabase;

import com.google.common.base.Supplier;

/**
 * Finds a remote
 * 
 * @see Remote
 */
public class RemoteResolve extends AbstractGeoGigOp<Optional<Remote>>
        implements Supplier<Optional<Remote>> {

    private String name;

    /**
     * @param name the name of the remote
     * @return {@code this}
     */
    public RemoteResolve setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Executes the remote-add operation.
     * 
     * @return the {@link Remote} that was added.
     */
    protected @Override Optional<Remote> _call() {
        if (name == null || name.isEmpty()) {
            throw new RemoteException(StatusCode.MISSING_NAME);
        }

        Optional<Remote> remoteOpt = Optional.empty();

        ConfigDatabase config = configDatabase();
        List<String> allRemotes = config.getAllSubsections("remote");
        if (allRemotes.contains(name)) {

            String remoteSection = "remote." + name;
            Optional<String> remoteFetchURL = config.get(remoteSection + ".url");
            Optional<String> remoteFetch = config.get(remoteSection + ".fetch");
            Optional<String> remoteMapped = config.get(remoteSection + ".mapped");
            Optional<String> remoteMappedBranch = config.get(remoteSection + ".mappedBranch");
            Optional<String> remoteUserName = config.get(remoteSection + ".username");
            Optional<String> remotePassword = config.get(remoteSection + ".password");
            Optional<String> remotePushURL = Optional.empty();
            if (remoteFetchURL.isPresent() && remoteFetch.isPresent()) {
                remotePushURL = config.get(remoteSection + ".pushurl");
            }
            String fetch = remoteFetchURL.orElse("");
            Remote remote = new Remote(name, fetch, remotePushURL.orElse(fetch),
                    remoteFetch.orElse(""), remoteMapped.orElse("false").equals("true"),
                    remoteMappedBranch.orElse(null), remoteUserName.orElse(null),
                    remotePassword.orElse(null));
            remoteOpt = Optional.of(remote);
        }
        return remoteOpt;
    }

    public @Override Optional<Remote> get() {
        return call();
    }
}
