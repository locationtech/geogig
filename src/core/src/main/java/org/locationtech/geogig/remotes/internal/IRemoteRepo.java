/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.remotes.internal;

import java.io.Closeable;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.remotes.ReceivePack;
import org.locationtech.geogig.remotes.SendPack;
import org.locationtech.geogig.remotes.SynchronizationException;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.RepositoryConnectionException;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;

/**
 * Provides an interface for interacting with remote repositories.
 */
public interface IRemoteRepo extends Closeable {

    /**
     * Opens the remote repository.
     * <p>
     * This method is idempotent.
     * 
     * @throws RepositoryConnectionException
     */
    public void open() throws RepositoryConnectionException;

    /**
     * Quietly closes the remote repository.
     * <p>
     * This method is idempotent.
     */
    public @Override void close();

    /**
     * List the remote's {@link Ref refs}.
     * 
     * @param getHeads whether to return refs in the {@code refs/heads} namespace
     * @param getTags whether to return refs in the {@code refs/tags} namespace
     * @return an immutable set of refs from the remote
     */
    public ImmutableSet<Ref> listRefs(boolean getHeads, boolean getTags);

    /**
     * @return the remote's HEAD {@link Ref}.
     */
    public Ref headRef();

    /**
     * Fetch all new objects from the specified {@link Ref} from the remote by invoking
     * {@link SendPack} in the remote repository with the local repository as the send-pack's
     * remote.
     * 
     * @param ref the remote ref that points to new commit data
     * @param newRef
     * @param fetchLimit the maximum depth to fetch
     * @param subProgress
     */
    public void fetchNewData(Ref newRef, Optional<Integer> fetchLimit, ProgressListener progress);

    /**
     * Push all new objects from the specified {@link Ref} by invoking {@link ReceivePack} in the
     * remote repository with the local repository as the receive-pack's remote.
     * 
     * @param ref the local ref that points to new commit data
     */
    public void pushNewData(Ref ref, ProgressListener progress) throws SynchronizationException;

    /**
     * Push all new objects from the specified {@link Ref} to the given refspec by invoking
     * {@link ReceivePack} in the remote repository.
     * 
     * @param ref the local ref that points to new commit data
     * @param refspec the refspec to push to
     */
    public void pushNewData(Ref ref, String refspec, ProgressListener progress)
            throws SynchronizationException;

    /**
     * Delete the given refspec from the remote repository.
     * 
     * @param refspec the refspec to delete
     * @return the deleted ref, {@link Optional#absent() absent} if it didn't exist
     */
    public @Nullable Optional<Ref> deleteRef(String refspec);

    /**
     * @return the depth of the repository, or {@link Optional#absent} if the repository is not
     *         shallow
     */
    public Optional<Integer> getDepth();

}
