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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.List;

import org.locationtech.geogig.hooks.Hookable;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.remotes.RefDiff;
import org.locationtech.geogig.remotes.TransferSummary;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;

/**
 * Applies all the {@link RevObject}s coming in the {@link Pack} to the local repository's
 * {@link ObjectDatabase}, but does not update any {@link Ref}; the returned {@link TransferSummary}
 * can be used by the calling code to do so.
 *
 */
@Hookable(name = "receive-pack")
public class ReceivePackOp extends AbstractGeoGigOp<List<RefDiff>> {

    private Pack pack;

    @Override
    protected List<RefDiff> _call() {
        checkState(pack != null, "No pack supplied");

        PackProcessor processor = getPackProcessor();
        List<RefDiff> appliedChanges = pack.applyTo(processor, getProgressListener());
        checkNotNull(appliedChanges);
        appliedChanges.forEach((c) -> checkNotNull(c));
        return appliedChanges;
    }

    protected PackProcessor getPackProcessor() {
        Repository repo = repository();
        ObjectDatabase store = repo.objectDatabase();
        IndexDatabase index = repo.indexDatabase();
        return new LocalPackProcessor(store, index);
    }

    public ReceivePackOp setPack(Pack pack) {
        this.pack = pack;
        return this;
    }

    public Pack getPack() {
        return pack;
    }
}
