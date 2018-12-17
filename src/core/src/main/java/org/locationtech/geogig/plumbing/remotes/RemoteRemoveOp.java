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

import java.util.stream.Stream;

import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.ForEachRef;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.plumbing.remotes.RemoteException.StatusCode;
import org.locationtech.geogig.porcelain.BranchConfig;
import org.locationtech.geogig.porcelain.BranchConfigOp;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.storage.ConfigDatabase;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;

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
     * @return the {@link Remote} that was removed, or {@link Optional#absent()} if the remote
     *         didn't exist.
     */
    @Override
    protected Remote _call() {
        if (name == null || name.isEmpty()) {
            throw new RemoteException(StatusCode.MISSING_NAME);
        }

        final String name = this.name;
        final Optional<Remote> remote = command(RemoteResolve.class).setName(name).call();
        if (!remote.isPresent()) {
            throw new RemoteException(StatusCode.REMOTE_NOT_FOUND);
        }

        String remoteSection = "remote." + name;
        configDatabase().removeSection(remoteSection);

        // Remove refs
        final String remotePrefix = Ref.append(Ref.REMOTES_PREFIX, name);

        // r -> Ref.isChild(remotePrefix, r.getName())
        Predicate<Ref> fn =  new Predicate<Ref>() {
            @Override
            public boolean apply(Ref r) {
                return Ref.isChild(remotePrefix, r.getName());
            }};

        ImmutableSet<Ref> localRemoteRefs = command(ForEachRef.class)
                .setFilter(fn).call();
        for (Ref localRef : localRemoteRefs) {
            command(UpdateRef.class).setDelete(true).setName(localRef.getName()).call();
        }

        Stream<BranchConfig> branchesMappedToThisRemote = command(BranchConfigOp.class).getAll()
                .stream().filter(c -> name.equals(c.getRemoteName().orElse(null)));

        branchesMappedToThisRemote.forEach(c -> {
            Ref localBranch = c.getBranch();
            command(BranchConfigOp.class).setName(localBranch.getName()).setRemoteBranch(null)
                    .setRemoteName(null).setDescription(c.getDescription().orElse(null)).set();
            String msg = String.format("Removed branch tracking of %s to %s/%s", localBranch, name,
                    c.getRemoteBranch().orElse(null));
            log.debug(msg);
        });
        return remote.get();
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
