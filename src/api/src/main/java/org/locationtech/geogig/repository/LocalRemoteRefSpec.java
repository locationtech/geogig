/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.locationtech.geogig.model.Ref;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;

public class LocalRemoteRefSpec {

    final String remoteRef, localRef;

    final boolean force, allChildren;

    LocalRemoteRefSpec(String remoteRef, String localRef, boolean force, boolean allChildren) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(remoteRef));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(localRef));
        this.remoteRef = remoteRef;
        this.localRef = localRef;
        this.force = force;
        this.allChildren = allChildren;
        if (allChildren) {
            Preconditions.checkArgument(localRef != null);
        }
    }

    public String getLocal() {
        return localRef;
    }

    public String getRemote() {
        return remoteRef;
    }

    public boolean isForce() {
        return force;
    }

    public boolean isAllChildren() {
        return allChildren;
    }

    public static List<LocalRemoteRefSpec> parse(final String remoteName, final String refspecs) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(remoteName));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(remoteName.trim()));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(refspecs), "no refspecs provided");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(refspecs.trim()),
                "no refspecs provided");

        List<String> refs = Splitter.on(';').omitEmptyStrings().trimResults().splitToList(refspecs);
        return refs.stream().map(spec -> LocalRemoteRefSpec.parseSingle(remoteName, spec))
                .collect(Collectors.toList());
    }

    static LocalRemoteRefSpec parseSingle(final String remoteName, final String refspec) {
        List<String> refs = Splitter.on(':').omitEmptyStrings().trimResults().splitToList(refspec);
        Preconditions.checkArgument(refs.size() > 0 && refs.size() < 3,
                "Invalid refspec, please use [+]<remoteref>[:<localref>]. Got %s", refspec);

        String remoteref = refs.get(0);
        boolean force = remoteref.charAt(0) == '+';
        if (force) {
            remoteref = remoteref.substring(1);
        }
        String localref = refs.size() == 1 ? "" : refs.get(1);
        Preconditions.checkState(!Strings.isNullOrEmpty(remoteref));

        boolean isAllChildren = remoteref.endsWith("/*");
        if (isAllChildren) {
            remoteref = remoteref.substring(0, remoteref.length() - 2);
            if (localref.isEmpty()) {
                localref = String.format("refs/remotes/%s", remoteName);
            } else {
                Preconditions.checkArgument(localref.endsWith("/*"),
                        "If remote ref is a catch-all (ends in /*), local ref should also be");
                localref = localref.substring(0, localref.length() - 2);
            }
        } else {
            Preconditions.checkArgument(!localref.endsWith("/*"),
                    "If remote ref is not a catch-all (does not ends in /*), local ref should not be");
            if (-1 == remoteref.indexOf('/') && !Ref.HEAD.equals(remoteref)) {
                remoteref = Ref.append(Ref.HEADS_PREFIX, remoteref);
            }
            String remoteRefSimpleName = Ref.stripCommonPrefix(remoteref);
            if (localref.isEmpty()) {
                localref = remoteRefSimpleName;
            }
            if (localref.indexOf('/') == -1) {
                localref = String.format("refs/remotes/%s/%s", remoteName, localref);
            }
        }
        return new LocalRemoteRefSpec(remoteref, localref, force, isAllChildren);
    }

    public Optional<String> mapToLocal(final String remoteRef) {
        Preconditions.checkNotNull(remoteRef);
        String localRef = null;
        if (isAllChildren()) {
            if (Ref.isChild(this.remoteRef, remoteRef)) {
                final String remoteRefName = remoteRef.substring(this.remoteRef.length());
                localRef = Ref.append(this.localRef, remoteRefName);
            }
        } else {
            if (remoteRef.equals(this.remoteRef)) {
                localRef = this.localRef;
            }
        }
        return Optional.ofNullable(localRef);
    }

    public Optional<String> mapToRemote(final String local) {
        Preconditions.checkNotNull(local);
        String remoteRef = null;
        if (isAllChildren()) {
            if (Ref.isChild(this.localRef, local)) {
                final String localRefName = local.substring(this.localRef.length());
                remoteRef = Ref.append(this.remoteRef, localRefName);
            }
        } else {
            if (local.equals(this.localRef)) {
                remoteRef = this.remoteRef;
            }
        }
        return Optional.ofNullable(remoteRef);
    }

    public @Override String toString() {
        return String.format("%s%s%s:%s%s", isForce() ? "+" : "", remoteRef,
                isAllChildren() ? "/*" : "", localRef, isAllChildren() ? "/*" : "");
    }
}
