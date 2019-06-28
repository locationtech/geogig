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
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;
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

    private ObjectId id;

    protected @Override java.util.Optional<RevCommit> _call() {
        Preconditions.checkArgument(commitIsh != null || id != null, "commit-ish not provided");

        final ObjectId id;
        if (this.id == null) {
            id = context.command(RevParse.class).setRefSpec(commitIsh).call().orElse(null);
        } else {
            id = this.id;
        }

        RevCommit commit = null;
        if (id != null) {
            ObjectDatabase db = objectDatabase();
            RevObject obj = db.getIfPresent(id);
            if (obj != null) {
                switch (obj.getType()) {
                case COMMIT:
                    commit = (RevCommit) obj;
                    break;
                case TAG:
                    commit = db.getCommit(((RevTag) obj).getCommitId());
                    break;
                default:
                    throw new IllegalArgumentException(
                            String.format("'%s' resolves to a %s, not a comit",
                                    (this.id == null ? this.commitIsh : this.id), obj.getType()));
                }
            }
        }
        return Optional.ofNullable(commit);
    }

    public ResolveCommit setCommitIsh(String refSpec) {
        this.commitIsh = refSpec;
        return this;
    }

    public ResolveCommit setCommitIsh(ObjectId id) {
        this.id = id;
        return this;
    }
}
