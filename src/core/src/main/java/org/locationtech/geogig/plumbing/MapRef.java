/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Remote;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Receives a list of refs for a remote and converts them either from local namespace to remotes
 * namespace or vice-versa.
 *
 */
public class MapRef extends AbstractGeoGigOp<List<Ref>> {

    private Remote remote;

    /**
     * If true, the provided refs are in a local namespace and need to be converted to the remotes
     * namespace, if false, the provided refs are in the remotes namespace and need to be converted
     * to the local namespace
     */
    private Boolean toRemote;

    private List<Ref> refs = new ArrayList<>();

    @Override
    protected List<Ref> _call() {
        checkState(remote != null, "remote not provided");
        checkState(toRemote != null,
                "must indicate whether to convert refs to local or remotes namespace");

        Function<Ref, Ref> function = (r) -> toRemote.booleanValue() ? toRemote(r) : toLocal(r);
        return Lists.newArrayList(Iterables.transform(refs, function));
    }

    private Ref toRemote(Ref localRef) {

        Optional<String> remoteName = remote.mapToLocal(localRef.getName());
        Preconditions.checkArgument(remoteName.isPresent(), "Can't map %s to remote ref using %s",
                localRef.getName(), remote.getFetchSpec());

        Ref remoteRef;
        if (localRef instanceof SymRef) {
            Ref target = toRemote(localRef.peel());
            remoteRef = new SymRef(remoteName.get(), target);
        } else {
            remoteRef = new Ref(remoteName.get(), localRef.getObjectId());
        }
        return remoteRef;
    }

    private Ref toLocal(Ref remoteRef) {
        Optional<String> localName = remote.mapToRemote(remoteRef.getName());
        Preconditions.checkArgument(localName.isPresent(), "Can't map %s to local ref using %s",
                remoteRef.getName(), remote.getFetchSpec());

        Ref localRef;
        if (remoteRef instanceof SymRef) {
            Ref target = toLocal(remoteRef.peel());
            localRef = new SymRef(localName.get(), target);
        } else {
            localRef = new Ref(localName.get(), remoteRef.getObjectId());
        }
        return localRef;
    }

    public MapRef add(Ref ref) {
        checkNotNull(ref);
        refs.add(ref);
        return this;
    }

    public MapRef addAll(Iterable<Ref> refs) {
        for (Ref ref : refs) {
            add(ref);
        }
        return this;
    }

    public MapRef setRemote(Remote remote) {
        checkNotNull(remote);
        this.remote = remote;
        return this;
    }

    public MapRef convertToLocal() {
        this.toRemote = Boolean.FALSE;
        return this;
    }

    public MapRef convertToRemote() {
        this.toRemote = Boolean.TRUE;
        return this;
    }
}
