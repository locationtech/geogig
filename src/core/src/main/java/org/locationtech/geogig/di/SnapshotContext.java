/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - pulled off from GuiceContext inner class
 */
package org.locationtech.geogig.di;

import org.locationtech.geogig.repository.Context;

class SnapshotContext extends DelegatingContext {

    private RefDatabaseSnapshot refsSnapshot;

    public SnapshotContext(Context context) {
        super(context);
        this.refsSnapshot = new RefDatabaseSnapshot(context.refDatabase());
        this.refsSnapshot.create();
    }

    public @Override Context snapshot() {
        return this;
    }
}