/* Copyright (c) 2012-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.remotes;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.storage.ConfigDatabase;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 * Return a list of all of the remotes from the local config database.
 * 
 * @see ConfigDatabase
 */
public class RemoteListOp extends AbstractGeoGigOp<ImmutableList<Remote>> {

    /**
     * Executes the remote-list operation.
     * 
     * @return {@code List<Remote>} of all remotes found in the config database, may be empty.
     */
    @Override
    protected ImmutableList<Remote> _call() {
        ConfigDatabase config = configDatabase();
        List<String> remotes = config.getAllSubsections("remote");
        List<Remote> allRemotes = new ArrayList<Remote>();
        for (String remoteName : remotes) {
            String remoteSection = "remote." + remoteName;
            Optional<String> remoteFetchURL = config.get(remoteSection + ".url");
            Optional<String> remoteFetch = config.get(remoteSection + ".fetch");
            Optional<String> remoteMapped = config.get(remoteSection + ".mapped");
            Optional<String> remoteMappedBranch = config.get(remoteSection + ".mappedBranch");
            Optional<String> remoteUserName = config.get(remoteSection + ".username");
            Optional<String> remotePassword = config.get(remoteSection + ".password");
            if (remoteFetchURL.isPresent() && remoteFetch.isPresent()) {
                Optional<String> remotePushURL = config.get(remoteSection + ".pushurl");
                allRemotes.add(new Remote(remoteName, remoteFetchURL.get(),
                        remotePushURL.or(remoteFetchURL.get()), remoteFetch.get(),
                        remoteMapped.or("false").equals("true"), remoteMappedBranch.orNull(),
                        remoteUserName.orNull(), remotePassword.orNull()));
            }
        }
        return ImmutableList.copyOf(allRemotes);
    }
}
