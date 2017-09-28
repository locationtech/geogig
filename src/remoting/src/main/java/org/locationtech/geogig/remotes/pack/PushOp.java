/* Copyright (c) 2012-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.remotes.pack;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.geogig.hooks.Hookable;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.ForEachRef;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.remotes.RefDiff;
import org.locationtech.geogig.remotes.OpenRemote;
import org.locationtech.geogig.remotes.RemoteResolve;
import org.locationtech.geogig.remotes.SendPack.TransferableRef;
import org.locationtech.geogig.remotes.TransferSummary;
import org.locationtech.geogig.remotes.internal.IRemoteRepo;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.Repository;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;

/**
 * Update remote refs along with associated objects.
 * 
 * <b>NOTE:</b> so far we don't have the ability to merge non conflicting changes. Instead, the diff
 * list we get acts on whole objects, , so its possible that this operation overrides non
 * conflicting changes when pushing a branch that has non conflicting changes at both sides. This
 * needs to be revisited once we get more merge tools.
 */
@Hookable(name = "push")
public class PushOp extends AbstractGeoGigOp<TransferSummary> {

    private boolean all;

    private List<String> refSpecs = new ArrayList<String>();

    private String remoteName;

    /**
     * @param all if {@code true}, push all refs under refs/heads/
     * @return {@code this}
     */
    public PushOp setAll(final boolean all) {
        this.all = all;
        return this;
    }

    /**
     * @param refSpec the refspec of a remote branch
     * @return {@code this}
     */
    public PushOp addRefSpec(final String refSpec) {
        refSpecs.add(refSpec);
        return this;
    }

    public List<String> getRefSpecs() {
        return refSpecs;
    }

    /**
     * @param remoteName the name or URL of a remote repository to push to
     * @return {@code this}
     */
    public PushOp setRemote(final String remoteName) {
        checkNotNull(remoteName);
        this.remoteName = remoteName;
        return this;
    }

    public String getRemoteName() {
        return remoteName;
    }

    public Optional<Remote> getRemote() {
        try {
            return Optional.of(resolveRemote());
        } catch (IllegalArgumentException e) {
            return Optional.absent();
        }
    }

    /**
     * Executes the push operation.
     * 
     * @return {@code null}
     * @see org.locationtech.geogig.repository.AbstractGeoGigOp#call()
     */
    @Override
    protected TransferSummary _call() {
        final Remote remote = resolveRemote();
        final Repository localRepo = repository();

        final ProgressListener progress = getProgressListener();

        final TransferSummary summary = new TransferSummary();

        try (IRemoteRepo remoteRepo = openRemote(remote)) {

            final PackRequest request = prepareRequest(localRepo);

            List<RefDiff> remoteResults = localRepo.command(SendPackOp.class)//
                    .setRequest(request)//
                    .setTarget(remoteRepo)//
                    .setProgressListener(progress)//
                    .call();

            summary.addAll(remote.getPushURL(), remoteResults);
        }

        return summary;
        // final List<TransferableRef> refsToPush = resolveRefs();
        //
        // SendPack sendPack = command(SendPack.class);
        // sendPack.setRemote(remote);
        // sendPack.setRefs(refsToPush);
        // sendPack.setProgressListener(getProgressListener());
        // TransferSummary result = sendPack.call();
        // return result;
    }

    private PackRequest prepareRequest(Repository localRepo) {
        // TODO Auto-generated method stub
        return null;
    }

    private IRemoteRepo openRemote(Remote remote) {
        return command(OpenRemote.class).setRemote(remote).readOnly().call();
    }

    private List<TransferableRef> resolveRefs() {
        List<TransferableRef> refsToPush = new ArrayList<>();

        List<String> refSpecsArg = this.refSpecs;

        if (refSpecsArg.isEmpty()) {
            if (all) {
                ImmutableSet<Ref> localRefs = getLocalRefs();
                for (Ref ref : localRefs) {
                    String localRef = ref.getName();
                    String remoteRef = null;
                    boolean forceUpdate = false;
                    boolean delete = false;
                    refsToPush.add(new TransferableRef(localRef, remoteRef, forceUpdate, delete));
                }
            } else {
                // push current branch
                Ref currentBranch = resolveHeadTarget();
                String localRef = currentBranch.getName();
                String remoteRef = null;
                boolean forceUpdate = false;
                boolean delete = false;
                refsToPush.add(new TransferableRef(localRef, remoteRef, forceUpdate, delete));
            }
        } else {
            for (String refspec : refSpecsArg) {
                String[] refs = refspec.split(":");
                if (refs.length == 0) {
                    refs = new String[2];
                    refs[0] = resolveHeadTarget().getName();
                    refs[1] = null;
                } else {
                    if (refs[0].startsWith("+")) {
                        refs[0] = refs[0].substring(1);
                    }
                    for (int i = 0; i < refs.length; i++) {
                        if (refs[i].trim().isEmpty()) {
                            refs[i] = null;
                        }
                    }
                }
                checkArgument(refs.length < 3,
                        "Invalid refspec, please use [+][<localref>][:][<remoteref>].");

                boolean force = refspec.startsWith("+");
                String localrefspec = refs[0];
                boolean delete = localrefspec == null;
                String remoterefspec = refs[refs.length == 2 ? 1 : 0];
                refsToPush.add(new TransferableRef(localrefspec, remoterefspec, force, delete));
            }
        }
        return refsToPush;
    }

    private Remote resolveRemote() {
        final String remoteName = this.remoteName == null ? "origin" : this.remoteName;

        Optional<Remote> pushRemote = command(RemoteResolve.class).setName(remoteName).call();

        checkArgument(pushRemote.isPresent(), "Remote could not be resolved.");

        return pushRemote.get();
    }

    private ImmutableSet<Ref> getLocalRefs() {
        Predicate<Ref> filter = new Predicate<Ref>() {
            final String prefix = Ref.HEADS_PREFIX;

            @Override
            public boolean apply(Ref input) {
                return !(input instanceof SymRef) && input.getName().startsWith(prefix);
            }
        };
        ImmutableSet<Ref> localRefs = command(ForEachRef.class).setFilter(filter).call();
        return localRefs;
    }

    private Ref resolveHeadTarget() {
        final Optional<Ref> currHead = refParse(Ref.HEAD);
        checkState(currHead.isPresent(), "Repository has no HEAD, can't push.");
        checkState(currHead.get() instanceof SymRef, "Can't push from detached HEAD");

        final Optional<Ref> headTarget = refParse(((SymRef) currHead.get()).getTarget());
        checkState(headTarget.isPresent());
        return headTarget.get();
    }

    private Optional<Ref> refParse(String refSpec) {
        return command(RefParse.class).setName(refSpec).call();
    }

}
