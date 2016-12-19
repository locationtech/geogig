/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import java.util.Date;
import java.util.Iterator;

import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.repository.AbstractGeoGigOp;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * Returns the last commit in the current branch at a given date
 */
public class CommitFromDateOp extends AbstractGeoGigOp<Optional<RevCommit>> {

    private Date date;

    public CommitFromDateOp setDate(Date date) {
        this.date = date;
        return this;
    }

    @Override
    protected Optional<RevCommit> _call() {
        Preconditions.checkState(date != null);
        long time = date.getTime();
        Iterator<RevCommit> iter = command(LogOp.class).setFirstParentOnly(true).call();
        while (iter.hasNext()) {
            RevCommit commit = iter.next();
            if (commit.getCommitter().getTimestamp() < time) {
                return Optional.of(commit);
            }
        }
        return Optional.absent();
    }

}
