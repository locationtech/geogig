/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api.porcelain;

import java.util.List;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.Remote;
import org.locationtech.geogig.api.plumbing.LsRemote;
import org.locationtech.geogig.api.plumbing.UpdateRef;
import org.locationtech.geogig.api.porcelain.RemoteException.StatusCode;
import org.locationtech.geogig.storage.ConfigDatabase;

import com.google.common.base.Optional;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;

/**
 * Removes a remote from the local config database.
 * 
 * @see ConfigDatabase
 */
public class RemoteRemoveOp extends AbstractGeoGigOp<Remote> {

    private String name;

    /**
     * Executes the remote-remove operation.
     * 
     * @return the {@link Remote} that was removed, or {@link Optional#absent()} if the remote
     *         didn't exist.
     */
    @Override
    protected Remote _call() {
        if (name == null || name.isEmpty()) {
            throw new RemoteException(StatusCode.MISSING_NAME);
        }
        ConfigDatabase config = configDatabase();
        List<String> allRemotes = config.getAllSubsections("remote");
        if (!allRemotes.contains(name)) {
            throw new RemoteException(StatusCode.REMOTE_NOT_FOUND);
        }

        Remote remote = null;
        String remoteSection = "remote." + name;
        Optional<String> remoteFetchURL = config.get(remoteSection + ".url");
        Optional<String> remoteFetch = config.get(remoteSection + ".fetch");
        Optional<String> remotePushURL = Optional.absent();
        Optional<String> remoteMapped = config.get(remoteSection + ".mapped");
        Optional<String> remoteMappedBranch = config.get(remoteSection + ".mappedBranch");
        Optional<String> remoteUserName = config.get(remoteSection + ".username");
        Optional<String> remotePassword = config.get(remoteSection + ".password");
        if (remoteFetchURL.isPresent() && remoteFetch.isPresent()) {
            remotePushURL = config.get(remoteSection + ".pushurl");
        }

        remote = new Remote(name, remoteFetchURL.or(""), remotePushURL.or(remoteFetchURL.or("")),
                remoteFetch.or(""), remoteMapped.or("false").equals("true"),
                remoteMappedBranch.orNull(), remoteUserName.orNull(), remotePassword.orNull());

        config.removeSection(remoteSection);

        // Remove refs
        final ImmutableSet<Ref> localRemoteRefs = command(LsRemote.class).retrieveLocalRefs(true)
                .setRemote(Suppliers.ofInstance(Optional.of(remote))).call();

        for (Ref localRef : localRemoteRefs) {
            command(UpdateRef.class).setDelete(true).setName(localRef.getName()).call();
        }

        return remote;
    }

    /**
     * @param name the name of the remote to remove
     * @return {@code this}
     */
    public RemoteRemoveOp setName(String name) {
        this.name = name;
        return this;
    }

}
