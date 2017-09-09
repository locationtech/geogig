/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.remotes;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import org.locationtech.geogig.hooks.Hookable;
import org.locationtech.geogig.repository.AbstractGeoGigOp;

@Hookable(name = "receive-pack")
public class ReceivePack extends AbstractGeoGigOp<TransferSummary> {

    public static class Pack {

    }

    private Pack pack;

    public ReceivePack setPack(Pack pack) {
        checkNotNull(pack);
        this.pack = pack;
        return this;
    }

    public Pack getPack() {
        return pack;
    }

    @Override
    protected TransferSummary _call() {
        checkState(pack != null, "No pack supplied");
        return null;
    }

}
