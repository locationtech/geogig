/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import java.util.Optional;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.storage.ObjectDatabase;

import com.google.common.base.Preconditions;

/**
 * Resolves the branch specified or the current branch one if no branch name was given
 * <p>
 * Returns {@link Optional#empty() empty} if no such object is found. Throws
 * {@code IllegalArgumentException} if the ref-spec resolves to an object from which a commit cannot
 * be derived.
 */
public class ResolveCommit extends AbstractGeoGigOp<java.util.Optional<RevCommit>> {

    private String commitIsh;

    protected @Override java.util.Optional<RevCommit> _call() {
        Preconditions.checkNotNull(commitIsh, "commit-ish not provided");

        final ObjectId id = context.command(RevParse.class).setRefSpec(commitIsh).call().orNull();

        RevCommit commit = null;
        if (id != null) {
            ObjectDatabase db = context.objectDatabase();
            RevObject obj = db.get(id);
            switch (obj.getType()) {
            case COMMIT:
                commit = (RevCommit) obj;
                break;
            case TAG:
                commit = db.getCommit(((RevTag) obj).getCommitId());
                break;
            default:
                throw new IllegalArgumentException(
                        String.format("'%s' resolves to a %s, not a comit", obj.getType()));
            }
        }
        return Optional.ofNullable(commit);
    }

    public ResolveCommit setCommitIsh(String refSpec) {
        this.commitIsh = refSpec;
        return this;
    }
}
