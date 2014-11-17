/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.api.porcelain;

import java.util.List;
import java.util.Map;

import org.locationtech.geogig.api.Ref;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;

/**
 *
 */
public class FetchResult {

    private Map<String, List<ChangedRef>> changedRefs = Maps.newHashMap();

    public Map<String, List<ChangedRef>> getChangedRefs() {
        return changedRefs;
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

    @Override
    public String toString() {
        return Objects.toStringHelper(FetchResult.class) //
            .addValue(getChangedRefs()) //
            .toString();
    }
}
