/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest.repository;

import static com.google.common.base.Preconditions.checkState;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.plumbing.TransactionBegin;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.GeogigTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Strings;

/**
 * Provides a safety net for remote pushes. This class keeps track of the IP addresses of remotes
 * that have pushed contents to this repository. If every object is successfully transfered, a
 * message will be sent to the PushManager to update the local references as indicated by the
 * remote.
 */
public class PushManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(PushManager.class);

    private Set<String> incomingIPs;

    private static PushManager instance = new PushManager();

    private PushManager() {
        incomingIPs = Collections.synchronizedSet(new HashSet<String>());
    }

    /**
     * @return the singleton instance of the {@code PushManager}
     */
    public static PushManager get() {
        return instance;
    }

    /**
     * Begins tracking incoming objects from the specified ip address.
     * 
     * @param ipAddress the remote machine that is pushing objects
     */
    public void connectionBegin(String ipAddress) {
        if (incomingIPs.contains(ipAddress)) {
            incomingIPs.remove(ipAddress);
        }
        if (incomingIPs.size() > 0) {
            // Fail?
        }
        incomingIPs.add(ipAddress);
    }

    /**
     * This is called when the machine at the specified ip address is finished pushing objects to
     * the server. This causes the ref given by {@code refSpec} to be updated to point to the given
     * {@code newCommit} object id, as well as the {@link Ref#WORK_HEAD WORK_HEAD} and
     * {@link Ref#STAGE_HEAD STAGE_HEAD} refs if {@code refSpec} is the current branch.
     * 
     * @param geogig the geogig of the local repository
     * @param ipAddress the remote machine that is pushing objects
     */
    public void connectionSucceeded(final Repository geogig, final String ipAddress,
            final String refspec, final ObjectId newCommit) {

        if (!incomingIPs.remove(ipAddress)) {// remove and check for existence in one shot
            throw new RuntimeException("Tried to end a connection that didn't exist.");
        }

        if(Strings.isNullOrEmpty(refspec)) {
            return;
        }
        // Do not use the geogig instance after this, but the tx one!
        GeogigTransaction tx = geogig.command(TransactionBegin.class).call();
        try {
            Optional<Ref> oldRef = tx.command(RefParse.class).setName(refspec).call();
            Optional<Ref> headRef = tx.command(RefParse.class).setName(Ref.HEAD).call();
            String refName = refspec;
            if (oldRef.isPresent()) {
                if (oldRef.get().getObjectId().equals(newCommit)) {
                    LOGGER.info("ref '{}' -> {} not updated, got same id", refName, newCommit);
                    return;
                }
                LOGGER.info("Updating ref '{}'[{}] -> {}", refName, oldRef.get().getObjectId(),
                        newCommit);
                refName = oldRef.get().getName();
            } else {
                LOGGER.info("Creating new ref '{}' -> {}", refName, newCommit);
            }
            if (headRef.isPresent() && headRef.get() instanceof SymRef) {
                if (((SymRef) headRef.get()).getTarget().equals(refName)) {
                    Optional<ObjectId> commitTreeId = tx.command(ResolveTreeish.class)
                            .setTreeish(newCommit).call();
                    checkState(commitTreeId.isPresent(), "Commit %s not found", newCommit);

                    tx.command(UpdateRef.class).setName(Ref.WORK_HEAD)
                            .setNewValue(commitTreeId.get()).call();
                    tx.command(UpdateRef.class).setName(Ref.STAGE_HEAD)
                            .setNewValue(commitTreeId.get()).call();
                }
            }
            tx.command(UpdateRef.class).setName(refName).setNewValue(newCommit).call();

            tx.commit();
        } catch (Exception e) {
            tx.abort();
            throw new RuntimeException(e);
        }
    }
}
