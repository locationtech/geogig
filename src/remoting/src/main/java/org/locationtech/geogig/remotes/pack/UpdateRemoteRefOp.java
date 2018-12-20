/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.remotes.pack;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.MapRef;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.plumbing.UpdateSymRef;
import org.locationtech.geogig.remotes.RefDiff;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Remote;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

public class UpdateRemoteRefOp extends AbstractGeoGigOp<List<RefDiff>> {

    /**
     * result of {@link SendPackOp}, as a list of ref diffs in the remote's local namespace (i.e.
     * refs/heads/<branch>)
     */
    private List<RefDiff> refUpdates = new ArrayList<>();

    private Remote remote;

    /**
     * Applies result of {@link SendPackOp} (a list of ref diffs in the remote's local
     * {@code refs/heads/<branch>} namespace and returns the list translated to the local
     * repository's remotes namespace (i.e. {@code refs/remotes/<remote>/<branch>}
     */
    @Override
    protected List<RefDiff> _call() {
        checkArgument(remote != null, "remote not provided");

        final List<RefDiff> remoteLocalRefs = this.refUpdates;

        final List<RefDiff> localRemoteRefs = convertToRemote(remoteLocalRefs);

        // (r) -> !isSymRef(r)
        Predicate<RefDiff> fn =  new Predicate<RefDiff>() {
            @Override
            public boolean apply(RefDiff r) {
                return !isSymRef(r);
            }};

        // (r) -> isSymRef(r)
        Predicate<RefDiff> fn2 =  new Predicate<RefDiff>() {
            @Override
            public boolean apply(RefDiff r) {
                return isSymRef(r);
            }};

        // update symrefs after refs so their target are present
        updateRefs(Iterables.filter(localRemoteRefs, fn));
        updateRefs(Iterables.filter(localRemoteRefs, fn2));

        return localRemoteRefs;
    }

    private boolean isSymRef(RefDiff cr) {
        final boolean delete = cr.isDelete();
        final boolean symRef = (delete ? cr.getOldRef() : cr.getNewRef()) instanceof SymRef;
        return symRef;
    }

    private void updateRefs(final Iterable<RefDiff> localRemoteRefs) {
        for (RefDiff cr : localRemoteRefs) {
            final Ref oldRef = cr.getOldRef();
            final Ref newRef = cr.getNewRef();

            final boolean delete = cr.isDelete();
            final boolean symRef = isSymRef(cr);
            final String name = delete ? oldRef.getName() : newRef.getName();

            if (symRef) {
                String oldTarget = delete ? ((SymRef) oldRef).getTarget() : null;
                String newTarget = delete ? null : ((SymRef) newRef).getTarget();

                UpdateSymRef cmd = command(UpdateSymRef.class)//
                        .setName(name)//
                        .setDelete(delete);
                if (!delete) {
                    cmd.setOldValue(oldTarget)//
                            .setNewValue(newTarget);
                }
                cmd.call();
            } else {
                final ObjectId oldValue = cr.isNew() ? null : oldRef.getObjectId();
                final ObjectId newValue = delete ? null : newRef.getObjectId();
                UpdateRef cmd = command(UpdateRef.class)//
                        .setName(name)//
                        .setDelete(delete);
                if (!delete) {
                    cmd.setOldValue(oldValue)//
                            .setNewValue(newValue);
                }
                cmd.call();
            }
        }
    }

    private List<RefDiff> convertToRemote(List<RefDiff> remoteLocalRefs) {
        List<RefDiff> localRemoteRefs = new ArrayList<>(remoteLocalRefs.size());
        for (RefDiff cr : remoteLocalRefs) {
            RefDiff localRemoteRef = convertToRemote(cr);
            localRemoteRefs.add(localRemoteRef);
        }
        return localRemoteRefs;
    }

    private RefDiff convertToRemote(RefDiff cr) {
        Ref oldRef = convertToRemote(cr.getOldRef());
        Ref newRef = convertToRemote(cr.getNewRef());
        return new RefDiff(oldRef, newRef);
    }

    private @Nullable Ref convertToRemote(@Nullable Ref remoteLocalRef) {
        Ref localRemoteRef = null;
        if (remoteLocalRef != null && !remoteLocalRef.getObjectId().isNull()) {
            // converd local refs namespaces to remotes namespace
            localRemoteRef = command(MapRef.class)//
                    .setRemote(remote)//
                    .add(remoteLocalRef)//
                    .convertToRemote()//
                    .call()//
                    .get(0);
        }
        return localRemoteRef;
    }

    public UpdateRemoteRefOp add(RefDiff diff) {
        checkNotNull(diff);
        refUpdates.add(diff);
        return this;
    }

    public UpdateRemoteRefOp addAll(Iterable<RefDiff> diffs) {
        checkNotNull(diffs);
        diffs.forEach((r) -> add(r));
        return this;
    }

    public UpdateRemoteRefOp setRemote(Remote remote) {
        this.remote = remote;
        return this;
    }
}
