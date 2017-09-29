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
import static org.locationtech.geogig.model.Ref.HEADS_PREFIX;
import static org.locationtech.geogig.model.Ref.TAGS_PREFIX;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.hooks.Hookable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.FindCommonAncestor;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.porcelain.BranchListOp;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.remotes.OpenRemote;
import org.locationtech.geogig.remotes.RefDiff;
import org.locationtech.geogig.remotes.RemoteResolve;
import org.locationtech.geogig.remotes.SynchronizationException;
import org.locationtech.geogig.remotes.SynchronizationException.StatusCode;
import org.locationtech.geogig.remotes.TransferSummary;
import org.locationtech.geogig.remotes.internal.IRemoteRepo;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Updates refs on a remote repository using local refs, while sending objects necessary to complete
 * the given refs.
 * 
 * <p>
 * The {@code refSpec} must follow the format {@code [+]<src>[:<remoteref>] | :<remoteref>}, where:
 * <ul>
 * <li>The leading {@code [+]} plus sign indicates a forced update even if the result is not a
 * fast-forward update (i.e. the remote and local branches have diverged in a way that the changes
 * in the local branch can't be applied to the remote's without re-writing history)
 * <li>The {@code [<src>]} is often the name of the branch you would want to push, but it can be any
 * arbitrary "SHA-1 expression" that resolves to a {@link RevCommit commit} or {@link RevTag tag},
 * such as {@code master~4} or {@code tags/v1.0.0}. If not present, then {@code [+]} must also be
 * absent, and the expression is required to be {@code :<remoteref>} indicating to delete the remote
 * ref.
 * <li>{@code [:] } separates the local refspec (addressing which contents to push), from the remote
 * ref name (indicating to which branch on the remote to push to).
 * <li>{@code [<remoteref>] } resolves to a branch or tag in the remote repository where to push to.
 * If the expression has no {@code <src>}, then it means the remote ref shall be deleted.
 * </ul>
 * <p>
 * <b>NOTE:</b> so far we don't have the ability to merge non conflicting changes. Instead, the diff
 * list we get acts on whole objects, , so its possible that this operation overrides non
 * conflicting changes when pushing a branch that has non conflicting changes at both sides. This
 * needs to be revisited once we get more merge tools.
 */
@Hookable(name = "push")
public class PushOp extends AbstractGeoGigOp<TransferSummary> {

    /**
     * if {@code true}, push all refs under the local repo's {@code refs/heads} namespace to the
     * remote. Overrides the list in {@link #refSpecs}.
     */
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
     * Adds a "refspec" representing a command indicating which source refs to push to which remote
     * ref.
     * <p>
     * See this class' header javadocs for an explanation of the refSpec format.
     * 
     * @param refSpec the refspec indicatin what to push from the local repository and where to push
     *        it on the remote repository.
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
            final Set<Ref> remoteRefs = getRemoteRefs(remoteRepo);

            final List<PushReq> pushRequests = parseRequests(remoteRefs);
            final PackRequest request = prepareRequest(pushRequests, remoteRefs);

            List<RefDiff> dataTransferResults = localRepo.command(SendPackOp.class)//
                    .setRequest(request)//
                    .setTarget(remoteRepo)//
                    .setProgressListener(progress)//
                    .call();
            // the remote has all the objects needed for the refs to be updated to the objectids
            // they point to
            List<RefDiff> updateResults = updateRemoteRefs(pushRequests, dataTransferResults,
                    remoteRepo);
            summary.addAll(remote.getPushURL(), updateResults);
        }

        return summary;
    }

    private Set<Ref> getRemoteRefs(IRemoteRepo remote) {
        Set<Ref> remoteRefs = Sets.newHashSet(remote.listRefs(repository(), true, true));
        Optional<Ref> headRef = remote.headRef();
        if (headRef.isPresent()) {
            remoteRefs.add(headRef.get());
        }
        return remoteRefs;
    }

    /**
     * Prepares the list of {@link PushReq} objects based on the command arguments (whether all refs
     * are to be pushed, or specific ones through {@link #addRefSpec(String)}}
     * 
     * @param remoteRefs the current state of the remote refs in it's local refs namespace (i.e. as
     *        {@code refs/heads/*}, not {@code refs/remotes/...})
     */
    private List<PushReq> parseRequests(Set<Ref> remoteRefs) {
        List<PushReq> pushReqs = new ArrayList<>();
        if (this.all) {
            List<Ref> localBranches;
            localBranches = command(BranchListOp.class).setLocal(true).setRemotes(false).call();
            localBranches.forEach((r) -> pushReqs.add(PushReq.update(r, r.getName(), false)));
        } else if (this.refSpecs.isEmpty()) {
            // local branch only
            Ref headTarget = resolveHeadTarget();
            pushReqs.add(PushReq.update(headTarget, headTarget.getName(), false));
        } else {
            for (String refSpec : this.refSpecs) {
                PushReq pushReq = parseRefSpec(refSpec, remoteRefs);
                pushReqs.add(pushReq);
            }
        }

        return pushReqs;
    }

    private PushReq parseRefSpec(final String refspec, final Set<Ref> remoteRefs) {
        checkArgument(!Strings.isNullOrEmpty(refspec), "No refspec provided");
        String[] refs = refspec.split(":");
        checkArgument(refs.length < 3,
                "Invalid refspec, please use [+][<localref>][:][<remoteref>].");

        if (refs.length == 0) {
            refs = new String[2];
        } else {
            if (refs[0].startsWith("+")) {
                refs[0] = refs[0].substring(1);
            }
            for (int i = 0; i < refs.length; i++) {
                if (Strings.isNullOrEmpty(refs[i])) {
                    refs[i] = null;
                }
            }
        }

        String localrefspec = refs[0];
        String remoterefspec = refs[refs.length == 2 ? 1 : 0];
        final boolean force = refspec.startsWith("+");
        final boolean delete = localrefspec == null && remoterefspec != null;

        PushReq req;
        if (delete) {
            Optional<Ref> remoteRef = resolveRemoteRef(remoterefspec, remoteRefs);
            Preconditions.checkArgument(remoteRef.isPresent(),
                    "ref %s does not exist in the remote repository");
            req = PushReq.delete(remoteRef.get().getName());
        } else {
            final Ref localRef;
            if (localrefspec == null) {
                localRef = resolveHeadTarget();
            } else {
                Optional<Ref> lr = refParse(localrefspec);
                checkArgument(lr.isPresent(),
                        "%s does not resolve to a ref in the local repository", localrefspec);
                localRef = lr.get();
            }

            String remoteRefName;
            if (remoterefspec == null) {
                remoteRefName = localRef.getName();
            } else {
                Optional<Ref> remoteRef = resolveRemoteRef(remoterefspec, remoteRefs);
                if (remoteRef.isPresent()) {
                    Ref ref = remoteRef.get();
                    if (ref instanceof SymRef) {
                        throw new SynchronizationException(StatusCode.CANNOT_PUSH_TO_SYMBOLIC_REF);
                    }
                    remoteRefName = ref.getName();
                } else if (Ref.HEAD.equals(remoterefspec)) {
                    remoteRefName = Ref.HEAD;
                } else {
                    final String specParentPath = Ref.parentPath(remoterefspec);
                    final boolean isTag;
                    final String localName;
                    if (specParentPath.isEmpty()) {
                        localName = remoterefspec;
                        isTag = localRef.getName().startsWith(TAGS_PREFIX);
                    } else {
                        localName = Ref.localName(remoterefspec);
                        isTag = remoterefspec.contains("tags/");
                    }
                    if (isTag) {
                        checkArgument(localRef.getName().startsWith(TAGS_PREFIX),
                                "%s is not a tag, only tags can be pushed as a tag",
                                localRef.getName());
                    }
                    remoteRefName = (isTag ? TAGS_PREFIX : HEADS_PREFIX) + localName;
                }
            }
            req = PushReq.update(localRef, remoteRefName, force);
        }
        return req;
    }

    private Optional<Ref> resolveRemoteRef(String remoterefspec, Set<Ref> remoteRefs) {
        for (Ref remoteRef : remoteRefs) {
            String refName = remoteRef.getName();
            if (refName.equals(remoterefspec) || refName.endsWith("/" + remoterefspec)) {
                return Optional.of(remoteRef);
            }
        }
        return Optional.absent();
    }

    private List<RefDiff> updateRemoteRefs(List<PushReq> pushRequests, List<RefDiff> dataResults,
            IRemoteRepo remoteRepo) {

        List<RefDiff> results = new ArrayList<>();

        final Repository local = repository();

        for (PushReq pr : pushRequests) {
            if (pr.delete) {
                Optional<Ref> deleted = remoteRepo.deleteRef(pr.remoteRef);
                if (deleted.isPresent()) {
                    results.add(RefDiff.removed(deleted.get()));
                }
                continue;
            }
            final String updateRefName = pr.remoteRef;
            ObjectId updateValue = pr.localRef.getObjectId();
            Optional<Ref> remoteRef = remoteRepo.command(UpdateRef.class)//
                    .setName(updateRefName)//
                    .setNewValue(updateValue)//
                    .call();

            Preconditions.checkArgument(remoteRef.isPresent());
            Ref localRemoteRef = local.command(MapRef.class)//
                    .setRemote(remoteRepo.getInfo())//
                    .add(remoteRef.get())//
                    .convertToRemote().call().get(0);
            local.command(UpdateRef.class)//
                    .setName(localRemoteRef.getName())//
                    .setNewValue(localRemoteRef.getObjectId())//
                    .call();
        }
        return results;
    }

    /**
     * Prepares a request upon which {@link SendPackOp} will resolve the set of {@link RevObject}s
     * to transfer from the local to the remote repo.
     * 
     * @param pushRequests the resolved push requests
     * @param remoteRefs the current state of the remote refs in it's local refs namespace (i.e. as
     *        {@code refs/heads/*}, not {@code refs/remotes/...})
     */
    private PackRequest prepareRequest(List<PushReq> pushRequests, Set<Ref> remoteRefs) {

        PackRequest req = new PackRequest();

        final Map<String, Ref> remoteRefsByName = Maps.uniqueIndex(remoteRefs, (r) -> r.getName());

        for (PushReq preq : pushRequests) {
            if (preq.delete) {
                continue;// deletes are handled after data transfer
            }
            final Ref localRef = preq.localRef;
            final String remoteRefName = preq.remoteRef;
            checkNotNull(localRef);
            checkNotNull(remoteRefName);

            ObjectId have = null;
            final ObjectDatabase localdb = objectDatabase();

            Ref resolvedRemoteRef = remoteRefsByName.get(remoteRefName);
            if (resolvedRemoteRef == null) {
                resolvedRemoteRef = remoteRefsByName.get(localRef.getName());
            }
            if (resolvedRemoteRef == null) {
                final ObjectId want = localRef.getObjectId();
                have = findShallowestCommonAncestor(want,
                        Sets.newHashSet(Iterables.transform(remoteRefs, (r) -> r.getObjectId())));
            } else {
                have = resolvedRemoteRef.getObjectId();
                if (!have.equals(localRef.getObjectId())) {
                    if (!localdb.exists(have)) {
                        if (preq.forceUpdate) {
                            have = null;
                        } else {
                            throw new SynchronizationException(StatusCode.REMOTE_HAS_CHANGES);
                        }
                    }
                }
            }

            RefRequest refReq = RefRequest.want(localRef, have);
            req.addRef(refReq);
        }

        return req;
    }

    private ObjectId findShallowestCommonAncestor(ObjectId tip, Set<ObjectId> otherTips) {
        ObjectDatabase localdb = objectDatabase();
        Set<ObjectId> commonAncestors = new HashSet<>();
        for (ObjectId remoteTip : otherTips) {
            if (!commonAncestors.contains(remoteTip) && localdb.exists(remoteTip)) {
                Optional<ObjectId> commonAncestor = command(FindCommonAncestor.class).setLeftId(tip)
                        .setRightId(remoteTip).call();
                if (commonAncestor.isPresent()) {
                    commonAncestors.add(commonAncestor.get());
                }
            }
        }

        int depth = Integer.MAX_VALUE;
        ObjectId shallowestCommonAncestor = null;
        for (ObjectId ca : commonAncestors) {
            int depthTo = depthTo(tip, ca);
            if (depthTo < depth) {
                shallowestCommonAncestor = ca;
                depth = depthTo;
            }
        }
        return shallowestCommonAncestor;
    }

    private int depthTo(ObjectId tip, ObjectId ca) {
        Iterator<RevCommit> commits = command(LogOp.class)//
                .setSince(ca)//
                .setUntil(tip)//
                .call();
        int depth = Iterators.size(commits);
        return depth;
    }

    private IRemoteRepo openRemote(Remote remote) {
        return command(OpenRemote.class).setRemote(remote).readOnly().call();
    }

    private Remote resolveRemote() {
        final String remoteName = this.remoteName == null ? "origin" : this.remoteName;

        Optional<Remote> pushRemote = command(RemoteResolve.class).setName(remoteName).call();

        checkArgument(pushRemote.isPresent(), "Remote could not be resolved.");

        return pushRemote.get();
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

    private static class PushReq {

        public final Ref localRef;

        public final String remoteRef;

        public final boolean forceUpdate;

        public boolean delete;

        private PushReq(final @Nullable Ref localRef, final @Nullable String remoteRef,
                final boolean forceUpdate, final boolean delete) {
            checkArgument(delete || localRef != null,
                    "localRef can only be null if delete == true");
            checkArgument(!delete || remoteRef != null,
                    "remoteRef can't be null if delete == true");

            this.localRef = localRef;
            this.remoteRef = remoteRef;
            this.forceUpdate = forceUpdate;
            this.delete = delete;
        }

        public static PushReq delete(String remoterefspec) {
            return new PushReq(null, remoterefspec, true, true);
        }

        public static PushReq update(Ref local, String remoteRefName, boolean force) {
            checkNotNull(local);
            checkNotNull(remoteRefName);
            return new PushReq(local, remoteRefName, force, false);
        }

        public @Override String toString() {
            return String.format("%s%s:%s", //
                    forceUpdate ? "+" : "", //
                    localRef == null ? "" : localRef.getName(), //
                    remoteRef == null ? "" : remoteRef);
        }
    }

}
