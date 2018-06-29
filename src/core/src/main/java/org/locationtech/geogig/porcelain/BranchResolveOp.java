/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.porcelain;

import java.util.Optional;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.NonNull;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.repository.AbstractGeoGigOp;

/**
 * Resolves the branch specified or the current branch one if no branch name was given
 *
 */
public class BranchResolveOp extends AbstractGeoGigOp<java.util.Optional<Ref>>
        implements Supplier<Optional<Ref>> {

    private String branchName = Ref.HEAD;

    protected @Override java.util.Optional<Ref> _call() {
        Ref head = command(RefParse.class).setName(branchName).call().orNull();
        if (head != null && Ref.HEAD.equals(branchName) && (head instanceof SymRef)) {
            head = head.peel();
        }
        return Optional.ofNullable(head);
    }

    public BranchResolveOp setName(@NonNull String name) {
        this.branchName = name;
        return this;
    }

    public @Override Optional<Ref> get() {
        return call();
    }
}
