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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.plumbing.MapRef;
import org.locationtech.geogig.porcelain.TagListOp;
import org.locationtech.geogig.remotes.LsRemoteOp;
import org.locationtech.geogig.remotes.RefDiff;
import org.locationtech.geogig.remotes.internal.IRemoteRepo;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;

import com.google.common.collect.Lists;
import com.google.common.collect.MapDifference;
import com.google.common.collect.MapDifference.ValueDifference;
import com.google.common.collect.Maps;

/**
 * Compares the refs of a remote stored locally against the current state of the remote refs and
 * returns the differences.
 * <p>
 * By default the refs diffs are returned in the remote namespace (i.e.
 * {@code refs/remotes/<remote>/} namespace). This behavior can be controlled through
 * {@link #normalizeToLocalRefs()} and {@link #normalizeToRemoteRefs()}.
 */
public class DiffRemoteRefsOp extends AbstractGeoGigOp<List<RefDiff>> {

    private IRemoteRepo remote;

    private boolean formatAsRemoteRefs = true;

    private boolean getTags = false;

    protected @Override List<RefDiff> _call() {
        checkState(remote != null, "no remote provided");
        // list of refs/remotes/<remote>/<refname> or refs/heads according to formatAsRemoteRefs
        Map<String, Ref> remotes;
        Map<String, Ref> locals;
        {
            // current live remote refs in the remote's local namespace (e.g. refs/heads/<branch>)
            Iterable<Ref> remoteRefs = getRemoteRefs();
            if (formatAsRemoteRefs) {
                // format refs returned by the remote in its local namespaces to our repository's
                // remotes namespace
                remoteRefs = command(MapRef.class)//
                        .setRemote(remote.getInfo())//
                        .convertToRemote()//
                        .addAll(remoteRefs)//
                        .call();
            }
            // current local local copy of the remote refs (e.g. refs/remotes/<remote>/<branch>
            List<Ref> remoteLocalRefs = Lists.newArrayList(getRemoteLocalRefs());
            if (!formatAsRemoteRefs) {
                // format local repository copies of the remote refs to the remote's local namespace
                remoteLocalRefs = command(MapRef.class)//
                        .setRemote(remote.getInfo())//
                        .convertToLocal()//
                        .addAll(remoteLocalRefs)//
                        .call();
            }
            if (this.getTags) {
                Map<String, RevTag> tags = Maps.uniqueIndex(command(TagListOp.class).call(),
                        RevTag::getName);
                for (Ref rf : remoteRefs) {
                    if (rf.getName().startsWith(Ref.TAGS_PREFIX)
                            && tags.containsKey(rf.localName())) {
                        RevTag tag = tags.get(rf.localName());
                        remoteLocalRefs.add(new Ref(Ref.TAGS_PREFIX + tag.getName(), tag.getId()));
                    }
                }
            }
            remotes = Maps.uniqueIndex(remoteRefs, Ref::getName);
            locals = Maps.uniqueIndex(remoteLocalRefs, Ref::getName);
        }
        final boolean mapped = remote.getInfo().getMapped();
        if (mapped) {
            // for a mapped remote, we are only interested in the branch we are mapped to
            final String mappedBranch = remote.getInfo().getMappedBranch();
            checkNotNull(mappedBranch);
            final String mappedBranchName = Ref.localName(mappedBranch);

            remotes = Maps.filterKeys(remotes,
                    name -> Ref.localName(name).equals(mappedBranchName));
            locals = Maps.filterKeys(locals, name -> Ref.localName(name).equals(mappedBranchName));
        }
        MapDifference<String, Ref> difference = Maps.difference(remotes, locals);

        // refs existing on the remote and not on the local repo
        Collection<Ref> newRemoteRefs = difference.entriesOnlyOnLeft().values();

        // remote refs existing on the local repo and not existing on the remote anymore
        Collection<Ref> removedRemoteRefs = difference.entriesOnlyOnRight().values();

        // refs existing both in local and remote with different objectIds
        Collection<ValueDifference<Ref>> changes = difference.entriesDiffering().values();

        List<RefDiff> diffs = new ArrayList<>();
        newRemoteRefs.forEach((r) -> diffs.add(RefDiff.added(r)));
        removedRemoteRefs.forEach((r) -> diffs.add(RefDiff.removed(r)));
        // v.leftValue() == new (remote copy), v.rightValue() == old (local copy)
        changes.forEach((v) -> diffs.add(RefDiff.updated(v.rightValue(), v.leftValue())));

        return diffs;
    }

    public DiffRemoteRefsOp setRemote(IRemoteRepo remote) {
        this.remote = remote;
        return this;
    }

    /**
     * If {@code true}, tags in the remote repo will be fetches and compared against local tags.
     * Defaults to {@code false}
     */
    public DiffRemoteRefsOp setGetRemoteTags(boolean getTags) {
        this.getTags = getTags;
        return this;
    }

    /**
     * Indicates to return branch and HEAD references as remote ones (i.e. in the
     * {@code refs/remotes/<remote>} namespace), tag references are always in {@code refs/tags} as
     * we don't track which remote each tag comes from. This is the default behavior.
     */
    public DiffRemoteRefsOp normalizeToRemoteRefs() {
        this.formatAsRemoteRefs = true;
        return this;
    }

    /**
     * Indicates to return branch refs as local refs (i.e. in the {@code refs/heads} namespace),
     * {@link Ref#HEAD} as a root ref; tag references are always in {@code refs/tags} as we don't
     * track which remote each tag comes from
     */
    public DiffRemoteRefsOp normalizeToLocalRefs() {
        this.formatAsRemoteRefs = false;
        return this;
    }

    private Set<Ref> getRemoteLocalRefs() {
        final Set<Ref> localRemoteRefs;
        localRemoteRefs = command(LsRemoteOp.class)//
                .retrieveHead(true)//
                .retrieveLocalRefs(true)//
                .setRemote(remote)//
                .call();
        return localRemoteRefs;
    }

    private Set<Ref> getRemoteRefs() {
        final boolean getTags = this.getTags;

        Set<Ref> remoteRemoteRefs;
        remoteRemoteRefs = command(LsRemoteOp.class)//
                .setRemote(remote)//
                .retrieveHead(true)//
                .retrieveLocalRefs(false)//
                .retrieveTags(getTags)//
                .call();
        return remoteRemoteRefs;
    }
}
