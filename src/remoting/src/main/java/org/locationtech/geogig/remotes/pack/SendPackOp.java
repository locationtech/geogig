/* Copyright (c) 2014-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.remotes.pack;

import static com.google.common.base.Preconditions.checkState;

import java.util.List;

import org.locationtech.geogig.hooks.Hookable;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.remotes.RefDiff;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.CommandFactory;

/**
 * Prepares a {@link Pack} of {@link RevObject}s for a {@link PackRequest} by means of
 * {@link PreparePackOp} and sends it to the provided {@link #setTarget target} repository by
 * calling {@link ReceivePackOp} on it.
 * <p>
 * Note this command <b>does not</b> update the state of any {@link Ref} in the remote (the one it's
 * being executed on) or the local (the one it's receiving the pack) repositories, but merely
 * transfers the missing set of {@link RevObject}s from the source to the target repo needed to
 * complement the receiving repository's object graph in order to contain all reachable contents for
 * the requested refs based on the {@link PackRequest}.
 * <p>
 * The returned list of {@link RefDiff} objects indicate that the remote has its revision objects up
 * to date up to each {@link RefDiff#getNewRef() newRef}'s object id and it's ok to
 * {@link UpdateRef} that ref to that object id.
 *
 */
@Hookable(name = "send-pack")
public class SendPackOp extends AbstractGeoGigOp<List<RefDiff>> {

    private PackRequest request;

    private CommandFactory targetRepo;

    @Override
    protected List<RefDiff> _call() {
        final PackRequest request = getRequest();
        final CommandFactory target = getTargetRepo();

        checkState(request != null, "no request specified");
        checkState(target != null, "no target repository specified");

        final Pack pack = preparePack(request);

        List<RefDiff> results = target.command(ReceivePackOp.class)//
                .setPack(pack)//
                .setProgressListener(getProgressListener())//
                .call();
        return results;
    }

    protected PackBuilder getPackBuilder() {
        return new LocalPackBuilder(repository());
    }

    public SendPackOp setRequest(PackRequest request) {
        this.request = request;
        return this;
    }

    public PackRequest getRequest() {
        return request;
    }

    protected Pack preparePack(PackRequest request) {
        PackBuilder packBuilder = getPackBuilder();
        return command(PreparePackOp.class)//
                .setRequest(request)//
                .setPackBuilder(packBuilder)//
                .setProgressListener(getProgressListener())//
                .call();
    }

    public SendPackOp setTarget(CommandFactory targetRepo) {
        this.targetRepo = targetRepo;
        return this;
    }

    public CommandFactory getTargetRepo() {
        return targetRepo;
    }
}
