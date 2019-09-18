/* Copyright (c) 2012-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.plumbing.remotes;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.UpdateRefs;
import org.locationtech.geogig.plumbing.remotes.RemoteException.StatusCode;
import org.locationtech.geogig.porcelain.BranchConfig;
import org.locationtech.geogig.porcelain.BranchConfigOp;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;
import org.locationtech.geogig.storage.ConfigDatabase;

import lombok.extern.slf4j.Slf4j;

/**
 * Removes a remote from the local config database.
 * 
 * @see ConfigDatabase
 */
@Slf4j
public class RemoteRemoveOp extends AbstractGeoGigOp<Remote> {

    private String name;

    /**
     * Executes the remote-remove operation.
     * 
     * @return the {@link Remote} that was removed, or {@link Optional#empty()} if the remote didn't
     *         exist.
     */
    protected @Override Remote _call() {
        if (name == null || name.isEmpty()) {
            throw new RemoteException(StatusCode.MISSING_NAME);
        }

        final String remoteName = this.name;
        final Remote remote = command(RemoteResolve.class).setName(remoteName).call()
                .orElseThrow(() -> new RemoteException(StatusCode.REMOTE_NOT_FOUND));

        final String remoteConfigSection = "remote." + remoteName;

        configDatabase().removeSection(remoteConfigSection);

        // Remove refs
        final String remotePrefix = Ref.append(Ref.REMOTES_PREFIX, remoteName);

        final List<Ref> remoteRefs = refDatabase().getAll(remotePrefix);
        UpdateRefs updateRefs = command(UpdateRefs.class).setReason("remote-remove: " + remoteName);
        remoteRefs.stream().map(Ref::getName).forEach(updateRefs::remove);
        updateRefs.call();

        Stream<BranchConfig> branchesMappedToThisRemote = command(BranchConfigOp.class).getAll()
                .stream().filter(c -> remoteName.equals(c.getRemoteName().orElse(null)));

        branchesMappedToThisRemote.forEach(c -> {
            Ref localBranch = c.getBranch();
            command(BranchConfigOp.class).setName(localBranch.getName()).setRemoteBranch(null)
                    .setRemoteName(null).setDescription(c.getDescription().orElse(null)).set();
            String msg = String.format("Removed branch tracking of %s to %s/%s", localBranch,
                    remoteName, c.getRemoteBranch().orElse(null));
            log.debug(msg);
        });
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

    public String getName() {
        return name;
    }

}
