/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.remotes;

import static com.google.common.base.Preconditions.checkArgument;

import org.locationtech.geogig.remotes.internal.IRemoteRepo;
import org.locationtech.geogig.remotes.internal.RemoteResolver;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.RepositoryConnectionException;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * Connects to a remote repository and returns its {@link IRemoteRepo} facade in an
 * {@link IRemoteRepo#open() open} state; callers must ensure to {@link IRemoteRepo#close() close}
 * it once it's no longer needed.
 */
public class OpenRemote extends AbstractGeoGigOp<IRemoteRepo> {

    private Remote remote;

    private Hints remoteHints = new Hints();

    public OpenRemote setRemote(Remote remote) {
        this.remote = remote;
        return this;
    }

    public Remote getRemote() {
        return remote;
    }

    public OpenRemote readOnly() {
        remoteHints.set(Hints.REMOTES_READ_ONLY, Boolean.TRUE);
        return this;
    }

    @Override
    protected IRemoteRepo _call() {
        Preconditions.checkNotNull(remote, "remote to connect to not provided");
        Optional<IRemoteRepo> opRemote = RemoteResolver.newRemote(remote, remoteHints);
        checkArgument(opRemote.isPresent(), "Unknown remote type: " + remote.getFetchURL());

        IRemoteRepo remoteRepo = opRemote.get();
        try {
            remoteRepo.open();
        } catch (RepositoryConnectionException e) {
            throw new IllegalStateException("Failed to connect to remote: " + e.getMessage(), e);
        }
        return remoteRepo;
    }

}
