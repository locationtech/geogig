/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.porcelain;

import java.util.List;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.Remote;
import org.locationtech.geogig.api.porcelain.RemoteException.StatusCode;
import org.locationtech.geogig.storage.ConfigDatabase;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;

/**
 * Finds a remote
 * 
 * @see Remote
 */
public class RemoteResolve extends AbstractGeoGigOp<Optional<Remote>> implements
        Supplier<Optional<Remote>> {

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
    @Override
    protected Optional<Remote> _call() {
        if (name == null || name.isEmpty()) {
            throw new RemoteException(StatusCode.MISSING_NAME);
        }

        Optional<Remote> result = Optional.absent();

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
            if (remoteFetchURL.isPresent() && remoteFetch.isPresent()) {
                Optional<String> remotePushURL = config.get(remoteSection + ".pushurl");

                Remote remote = new Remote(name, remoteFetchURL.get(),
                        remotePushURL.or(remoteFetchURL.get()), remoteFetch.get(), remoteMapped.or(
                                "false").equals("true"), remoteMappedBranch.orNull(),
                        remoteUserName.orNull(), remotePassword.orNull());
                result = Optional.of(remote);
            }
        }
        return result;
    }

    @Override
    public Optional<Remote> get() {
        return call();
    }
}
