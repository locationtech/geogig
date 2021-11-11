/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.geogig.commands.pr;

import org.locationtech.geogig.base.Preconditions;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.UpdateRefs;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.transaction.GeogigTransaction;

import lombok.NonNull;

public class PRCloseOp extends PRCommand<PRStatus> {

    private @NonNull Integer id;

    public PRCloseOp setId(int id) {
        this.id = id;
        return this;
    }

    protected @Override PRStatus _call() {
        PRStatus status = command(PRHealthCheckOp.class).setId(id).call();
        Preconditions.checkState(!status.isMerged(),
                "Pull request %s is already merged, can't be closed", id);
        if (status.isClosed()) {
            return status;
        }

        PR pr = status.getRequest();
        Context liveContext = context();
        GeogigTransaction tx = pr.getTransaction(liveContext);
        Ref headRef = pr.resolveHeadRef(tx);
        Ref origRef = pr.resolveOriginRef(tx);
        tx.abort();
        liveContext.command(UpdateRefs.class)//
                .setReason(String.format("pr-close: close pull request %d wihtout merging it", id))//
                .add(headRef)//
                .add(origRef)//
                .call();
        return command(PRHealthCheckOp.class).setRequest(pr).call();
    }
}
