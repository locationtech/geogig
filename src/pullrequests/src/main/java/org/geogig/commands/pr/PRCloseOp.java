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

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.impl.GeogigTransaction;

import com.google.common.base.Preconditions;

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
        setRef(liveContext, headRef);
        setRef(liveContext, origRef);
        return command(PRHealthCheckOp.class).setRequest(pr).call();
    }

    private void setRef(Context liveContext, Ref ref) {
        String name = ref.getName();
        ObjectId objectId = ref.getObjectId();
        liveContext.command(UpdateRef.class).setName(name).setNewValue(objectId).call();
    }

}
