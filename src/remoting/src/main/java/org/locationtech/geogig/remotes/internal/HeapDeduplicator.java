/* Copyright (c) 2013-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.remotes.internal;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.locationtech.geogig.model.ObjectId;

import com.google.common.collect.Sets;

public class HeapDeduplicator implements Deduplicator {

    private static class IdTuple {
        private final ObjectId left;

        private final ObjectId right;

        private IdTuple(ObjectId left, ObjectId right) {
            this.left = left;
            this.right = right;
        }

        static IdTuple of(ObjectId left, ObjectId right) {
            return new IdTuple(left, right);
        }

        public @Override boolean equals(Object o) {
            IdTuple t = (IdTuple) o;
            return left.equals(t.left) && right.equals(t.right);
        }

        public @Override int hashCode() {
            return 31 * left.hashCode() + right.hashCode();
        }
    }

    private Set<IdTuple> seen = Sets.newConcurrentHashSet();

    public @Override boolean visit(ObjectId right) {
        return visit(ObjectId.NULL, right);
    }

    public @Override boolean visit(ObjectId left, ObjectId right) {
        return seen.add(IdTuple.of(left, right));
    }

    public @Override boolean isDuplicate(ObjectId id) {
        return isDuplicate(ObjectId.NULL, id);
    }

    public @Override boolean isDuplicate(ObjectId left, ObjectId right) {
        return seen.contains(IdTuple.of(left, right));
    }

    public @Override void removeDuplicates(List<ObjectId> ids) {
        Iterator<ObjectId> iterator = ids.iterator();
        while (iterator.hasNext()) {
            ObjectId id = iterator.next();
            if (seen.contains(IdTuple.of(ObjectId.NULL, id))) {
                iterator.remove();
            }
        }
    }

    public @Override void reset() {
        seen.clear();
    }

    public @Override void release() {
        seen = null;
    }
}
