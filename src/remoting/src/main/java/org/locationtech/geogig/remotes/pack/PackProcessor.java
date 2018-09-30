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

import java.util.Iterator;

import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.remotes.internal.Deduplicator;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.IndexDatabase;

/**
 * Applies changes of a pack to a repository.
 * <p>
 * The abstraction serves to separate the concern of whether the pack is being directly applied to a
 * repository's object database or being transferred over the wire to be applied to a remote
 * repository's object database.
 *
 */
public interface PackProcessor {

    public void putAll(Iterator<? extends RevObject> iterator, BulkOpListener listener);

    public void putIndex(Pack.IndexDef index, IndexDatabase sourceStore,
            ObjectReporter objectReport, Deduplicator deduplicator);

}
