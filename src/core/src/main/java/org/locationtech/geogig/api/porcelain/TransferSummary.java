/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.porcelain;

import static com.google.common.base.Preconditions.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.locationtech.geogig.api.Ref;

import com.google.common.base.Objects;
import com.google.common.collect.ArrayListMultimap;

/**
 *
 */
public class TransferSummary {

    private ArrayListMultimap<String, ChangedRef> changedRefs = ArrayListMultimap.create();

    public Map<String, Collection<ChangedRef>> getChangedRefs() {
        return changedRefs.asMap();
    }

    static public class ChangedRef {
        public enum ChangeTypes {
            ADDED_REF, REMOVED_REF, CHANGED_REF, DEEPENED_REF
        }

        private Ref oldRef;

        private Ref newRef;

        private ChangeTypes type;

        public ChangedRef(Ref oldRef, Ref newRef, ChangeTypes type) {
            this.oldRef = oldRef;
            this.newRef = newRef;
            this.type = type;
        }

        public Ref getOldRef() {
            return oldRef;
        }

        public void setOldRef(Ref oldRef) {
            this.oldRef = oldRef;
        }

        public Ref getNewRef() {
            return newRef;
        }

        public void setNewRef(Ref newRef) {
            this.newRef = newRef;
        }

        public ChangeTypes getType() {
            return type;
        }

        public void setType(ChangeTypes type) {
            this.type = type;
        }

        @Override
        public String toString() {
            return Objects.toStringHelper(ChangedRef.class) //
                    .addValue(type) //
                    .addValue(oldRef) //
                    .addValue(newRef) //
                    .toString();
        }
    }

    public void add(final String remoteURL, final ChangedRef changeResult) {
        checkNotNull(remoteURL);
        checkNotNull(changeResult);
        changedRefs.put(remoteURL, changeResult);
    }

    public void addAll(final String remoteURL, final List<ChangedRef> changes) {
        checkNotNull(remoteURL);
        checkNotNull(changes);
        for (ChangedRef cr : changes) {
            checkNotNull(cr);
        }
        changedRefs.putAll(remoteURL, changes);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(TransferSummary.class) //
                .addValue(changedRefs) //
                .toString();
    }

    public boolean isEmpty() {
        return changedRefs.isEmpty();
    }

}
