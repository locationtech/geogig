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
import org.locationtech.geogig.plumbing.MapRef;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.plumbing.remotes.RemoteResolve;
import org.locationtech.geogig.porcelain.BranchListOp;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.remotes.OpenRemote;
import org.locationtech.geogig.remotes.RefDiff;
import org.locationtech.geogig.remotes.SynchronizationException;
import org.locationtech.geogig.remotes.SynchronizationException.StatusCode;
import org.locationtech.geogig.remotes.TransferSummary;
import org.locationtech.geogig.remotes.internal.IRemoteRepo;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.ObjectDatabase;

import com.google.common.base.Function;
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
 * For the {@link #setRemote remote} specified, the fetch process consists of three basic steps:
 * <ul>
 * <li>Parse the {@link #addRefSpec refSpecs} and resolve the {@link RefDiff} differences between
 * the local copies of the remote refs and the current state of the remote refs in the remote
 * repository
 * <li>Having resolved the outdated refs in the remote, prepare a {@link PackRequest} and call
 * {@link SendPackOp} on the local repository with that request, and the remote repository as the
 * target. This transfers all the missing {@link RevObject} instances from the local to the remote
 * repository, as {@link SendPackOp} will call {@link ReceivePackOp} on the local repo.
 * <li>Finally, update refs on the remote repo as well as the local copies of the remote references
 * so they point to the {@link RevObject}s identified in the first step.
 * </ul>
 * 
 * <p>
 * This process is essentially the same than {@link FetchOp} with inverted source and target
 * repositories. That is, {@link FetchOp} calls {@link SendPackOp} on the remote repository with the
 * local as target, and {@code PushOp} calls {@link SendPackOp} on the local repository with the
 * remote as target. Then both update the needed {@link Ref refs} at either side.
 * <p>
 * The result is a {@link TransferSummary} whose {@link TransferSummary#getRefDiffs() refDiffs} map
 * is keyed by each remote's {@link Remote#getFetchURL() fetchURL} with one {@link RefDiff} entry
 * for each "local remote" reference updated (i.e. the refs in the local repository under the
 * {@code refs/remotes/<remote>/} or {@code refs/tags} namespaces that were created, deleted, or
 * updated.
 * 
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

    private boolean pushIndexes;

    protected @Override TransferSummary _call() {
        final Remote remote = resolveRemote();
        final Repository localRepo = repository();

        final ProgressListener progress = getProgressListener();

        final TransferSummary summary = new TransferSummary();

        try (IRemoteRepo remoteRepo = openRemote(remote)) {
            final Set<Ref> remoteRefs = getRemoteRefs(remoteRepo);
            final List<PushReq> pushRequests = parseRequests(remoteRefs);
            final PackRequest request = prepareRequest(pushRequests, remoteRefs);
            request.syncIndexes(pushIndexes);
            
            List<RefDiff> dataTransferResults = localRepo.command(SendPackOp.class)//
                    .setRequest(request)//
                    .setTarget(remoteRepo)//
                    .setProgressListener(progress)//
                    .call();
            // the remote has all the objects needed for the refs to be updated to the objectids
            // they point to
            List<RefDiff> updateResults = updateRemoteRefs(pushRequests, remoteRefs, remoteRepo);
            summary.addAll(remote.getPushURL(), updateResults);
        }

        return summary;
    }

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
        String localrefspec;
        String remoterefspec;
        boolean force = false;
        boolean delete = false;
        if (refspec.startsWith(":") && !refspec.equals(":")) {
            delete = true;
            localrefspec = null;
            remoterefspec = refspec.substring(1);
        } else {
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
            localrefspec = refs[0];
            remoterefspec = refs[refs.length == 2 ? 1 : 0];
            force = refspec.startsWith("+");
            delete = localrefspec == null && remoterefspec != null;
        }
        PushReq req;
        if (delete) {
            Optional<Ref> remoteRef = resolveRemoteRef(remoterefspec, remoteRefs);
            Preconditions.checkArgument(remoteRef.isPresent(),
                    "ref %s does not exist in the remote repository", remoterefspec);
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
                    remoteRefName = ref.getName();
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

    /**
     * @param pushRequests what was actually requested to push
     * @param previousRemoteRefs the state of the remote refs before the data transfer
     * @param remoteRepo the remote repo
     * @return the updated list of what's currently in the remote and what's been updated on the
     *         remote refs once this method finishes
     */
    private List<RefDiff> updateRemoteRefs(List<PushReq> pushRequests, Set<Ref> previousRemoteRefs,
            IRemoteRepo remoteRepo) {

        //Ref::getName, but friendly for Fortify
        Function<Ref, String> fn_ref_getName =  new Function<Ref, String>() {
            @Override
            public String apply(Ref ref) {
                return ref.getName();
            }};

        final Map<String, Ref> beforeRemoteRefs = Maps.uniqueIndex(previousRemoteRefs,
                fn_ref_getName);

        List<RefDiff> results = new ArrayList<>();

        final Repository local = repository();

        for (PushReq pr : pushRequests) {
            if (pr.delete) {
                // REVISIT: should remote remove the ref only if it still has the same value it had
                // before the op?
                Optional<Ref> deleted = remoteRepo.deleteRef(pr.remoteRef);
                if (deleted.isPresent()) {
                    results.add(RefDiff.removed(deleted.get()));
                }
                continue;
            }
            final String updateRefName = pr.remoteRef;
            final @Nullable Ref oldRef = beforeRemoteRefs.get(updateRefName);
            final @Nullable ObjectId oldValue = oldRef == null ? null : oldRef.getObjectId();
            final ObjectId updateValue = pr.localRef.getObjectId();
            if (updateValue.equals(oldValue)) {
                continue;
            }
            // will fail if current value has changed
            Optional<Ref> remoteRef = remoteRepo.command(UpdateRef.class)//
                    .setName(updateRefName)//
                    .setOldValue(oldRef == null ? null : oldRef.getObjectId())//
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

            RefDiff updateRemoteRef = new RefDiff(oldRef, remoteRef.get());
            results.add(updateRemoteRef);
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

        //Ref::getName, but friendly for Fortify
        Function<Ref, String> fn_ref_getName =  new Function<Ref, String>() {
            @Override
            public String apply(Ref ref) {
                return ref.getName();
            }};

        final Map<String, Ref> remoteRefsByName = Maps.uniqueIndex(remoteRefs, fn_ref_getName);

        for (PushReq preq : pushRequests) {
            if (preq.delete) {
                continue;// deletes are handled after data transfer
            }
            final Ref localRef = preq.localRef;
            final String remoteRefName = preq.remoteRef;
            checkNotNull(localRef);
            checkNotNull(remoteRefName);

            final ObjectId want = localRef.getObjectId();
            Ref resolvedRemoteRef = remoteRefsByName.get(remoteRefName);
            final @Nullable ObjectId have;
            if (preq.forceUpdate) {


                //(r) -> r.getObjectId()
                Function<Ref, ObjectId> fn =  new Function<Ref, ObjectId>() {
                    @Override
                    public ObjectId apply(Ref f) {
                        return f.getObjectId();
                    }};

                have = findShallowestCommonAncestor(want,
                        Sets.newHashSet(Iterables.transform(remoteRefs, fn)));
            } else {
                try {
                    checkPush(localRef, resolvedRemoteRef);
                } catch (SynchronizationException e) {
                    if (e.statusCode == StatusCode.NOTHING_TO_PUSH) {
                        continue;
                    }
                    throw e;
                }
                if (resolvedRemoteRef == null) {
                    resolvedRemoteRef = remoteRefsByName.get(localRef.getName());
                }
                if (resolvedRemoteRef == null) {

                    //(r) -> r.getObjectId()
                    Function<Ref, ObjectId> fn =  new Function<Ref, ObjectId>() {
                        @Override
                        public ObjectId apply(Ref f) {
                            return f.getObjectId();
                        }};

                    // creating a new branch on the remote from a branch in the local repo, lets
                    // check if we can figure out a common ancestor
                    have = findShallowestCommonAncestor(want, Sets
                            .newHashSet(Iterables.transform(remoteRefs, fn)));
                } else {
                    // have is guaranteed to be in the local repo because of checkPush above
                    have = resolvedRemoteRef.getObjectId();
                }
            }

            RefRequest refReq = RefRequest.want(localRef, have);
            req.addRef(refReq);
        }

        return req;
    }

    private @Nullable ObjectId findShallowestCommonAncestor(ObjectId tip, Set<ObjectId> otherTips) {
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

    /**
     * Determine if it is safe to push to the remote repository.
     * 
     * @param localRef the ref to push
     * @param remoteRefOpt the ref to push to
     * @throws SynchronizationException if its not safe or possible to push to the given remote ref
     *         (see {@link StatusCode} for the possible reasons)
     */
    private void checkPush(final Ref localRef, final @Nullable Ref remoteRef)
            throws SynchronizationException {
        if (null == remoteRef) {
            return;// safe to push
        }
        if (remoteRef instanceof SymRef) {
            throw new SynchronizationException(StatusCode.CANNOT_PUSH_TO_SYMBOLIC_REF);
        }
        final ObjectId localObjectId = localRef.getObjectId();
        final ObjectId remoteObjectId = remoteRef.getObjectId();
        if (remoteObjectId.equals(localObjectId)) {
            // The branches are equal, no need to push.
            throw new SynchronizationException(StatusCode.NOTHING_TO_PUSH);
        } else if (objectDatabase().exists(remoteObjectId)) {
            Optional<ObjectId> ancestor = command(FindCommonAncestor.class)
                    .setLeftId(remoteObjectId).setRightId(localObjectId).call();
            if (!ancestor.isPresent()) {
                // There is no common ancestor, a push will overwrite history
                throw new SynchronizationException(StatusCode.REMOTE_HAS_CHANGES);
            } else if (ancestor.get().equals(localObjectId)) {
                // My last commit is the common ancestor, the remote already has my data.
                throw new SynchronizationException(StatusCode.NOTHING_TO_PUSH);
            } else if (!ancestor.get().equals(remoteObjectId)) {
                // The remote branch's latest commit is not my ancestor, a push will cause a
                // loss of history.
                throw new SynchronizationException(StatusCode.REMOTE_HAS_CHANGES);
            }
        } else if (!remoteObjectId.isNull()) {
            // The remote has data that I do not, a push will cause this data to be lost.
            throw new SynchronizationException(StatusCode.REMOTE_HAS_CHANGES);
        }
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

    public PushOp setPushIndexes(boolean pushIndexes) {
        this.pushIndexes = pushIndexes;
        return this;
    }

}
