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
import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.List;

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
        if (localRef.namespace().equals(Ref.TAGS_PREFIX)) {
            return localRef;
        }
        checkArgument(!localRef.getName().startsWith(Ref.REMOTES_PREFIX),
                "ref is already in a remotes namespace: %s", localRef);

        final String remoteNamespace = Ref.REMOTES_PREFIX + remote.getName() + "/";
        final String remoteRefName = remoteNamespace + localRef.localName();
        Ref remoteRef;
        if (localRef instanceof SymRef) {
            SymRef sr = (SymRef) localRef;
            String localtarget = sr.getTarget();
            Ref remoteTarget = toRemote(new Ref(localtarget, sr.getObjectId()));
            remoteRef = new SymRef(remoteRefName, remoteTarget);
        } else {
            remoteRef = new Ref(remoteRefName, localRef.getObjectId());
        }
        return remoteRef;
    }

    private Ref toLocal(Ref localRemoteRef) {
        final Remote remote = this.remote;
        if (localRemoteRef.namespace().equals(Ref.TAGS_PREFIX)) {
            return localRemoteRef;
        }
        final String localName = localRemoteRef.localName();
        final String remoteNamespace = localRemoteRef.namespace();
        final String expectedRemotePrefix = Ref.REMOTES_PREFIX + remote.getName() + "/";
        Preconditions.checkArgument(remoteNamespace.equals(expectedRemotePrefix));

        final String localPrefix = Ref.HEAD.equals(localName) ? "" : Ref.HEADS_PREFIX;
        final String localRefName = localPrefix + localName;
        Ref ref = null;
        if (localRemoteRef instanceof SymRef) {
            SymRef sr = (SymRef) localRemoteRef;
            Ref localTarget = toLocal(new Ref(sr.getTarget(), sr.getObjectId()));
            ref = new SymRef(localRefName, localTarget);
        } else {
            ref = new Ref(localRefName, localRemoteRef.getObjectId());
        }
        return ref;
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
