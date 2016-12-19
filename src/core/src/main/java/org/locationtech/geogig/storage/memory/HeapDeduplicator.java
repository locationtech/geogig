/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.memory;

import java.util.List;
import java.util.Set;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.repository.impl.Deduplicator;

import com.google.common.collect.Sets;

public class HeapDeduplicator implements Deduplicator {
    private Set<ObjectId> seen = Sets.newConcurrentHashSet();

    @Override
    public boolean visit(ObjectId id) {
        return !seen.add(id);
    }

    @Override
    public boolean isDuplicate(ObjectId id) {
        return seen.contains(id);
    }

    @Override
    public void removeDuplicates(List<ObjectId> ids) {
        ids.removeAll(seen);
    }

    @Override
    public void reset() {
        seen.clear();
    }

    @Override
    public void release() {
        seen = null;
    }
}
