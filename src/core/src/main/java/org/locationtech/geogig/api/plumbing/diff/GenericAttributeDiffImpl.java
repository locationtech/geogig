/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.api.plumbing.diff;

import javax.annotation.Nullable;

import org.locationtech.geogig.storage.text.TextValueSerializer;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * Generic implementation of a difference between two values for a given attribute
 * 
 */
public class GenericAttributeDiffImpl implements AttributeDiff {

    /**
     * The new value. Null if it does not exist (the attribute has been removed)
     */
    private Optional<?> newValue;

    /**
     * The old value. Null if it did not exist (the attribute has been added)
     */
    private Optional<?> oldValue;

    public GenericAttributeDiffImpl(@Nullable Optional<?> oldValue, @Nullable Optional<?> newValue) {
        if (oldValue == null) {
            this.oldValue = Optional.absent();
        } else {
            this.oldValue = oldValue;
        }

        if (newValue == null) {
            this.newValue = Optional.absent();
        } else {
            this.newValue = newValue;
        }
    }

    @Override
    public TYPE getType() {
        TYPE type;
        if (!oldValue.isPresent() && !newValue.isPresent()) {
            type = TYPE.NO_CHANGE;
        } else if (!newValue.isPresent()) {
            type = TYPE.REMOVED;
        } else if (!oldValue.isPresent()) {
            type = TYPE.ADDED;
        } else if (oldValue.equals(newValue)) {
            type = TYPE.NO_CHANGE;
        } else {
            type = TYPE.MODIFIED;
        }
        return type;
    }

    @Override
    public Optional<?> getOldValue() {
        return oldValue;
    }

    @Override
    public Optional<?> getNewValue() {
        return newValue;
    }

    public String toString() {
        if (getType().equals(TYPE.MODIFIED)) {
            return attributeValueAsString(oldValue) + " -> " + attributeValueAsString(newValue);
        } else if (getType().equals(TYPE.ADDED)) {
            return "[MISSING] -> " + attributeValueAsString(newValue);
        } else if (getType().equals(TYPE.REMOVED)) {
            return attributeValueAsString(oldValue) + " -> [MISSING]";
        } else {
            return "[NO CHANGE] -> " + attributeValueAsString(oldValue);
        }
    }

    private CharSequence attributeValueAsString(Optional<?> value) {
        if (value.isPresent()) {
            return TextValueSerializer.asString(Optional.fromNullable((Object) value.get()));
        } else {
            return "NULL";
        }
    }

    @Override
    public AttributeDiff reversed() {
        return new GenericAttributeDiffImpl(newValue, oldValue);
    }

    @Override
    public Optional<?> applyOn(Optional<?> obj) {
        Preconditions.checkState(canBeAppliedOn(obj));
        return newValue;
    }

    @Override
    public boolean canBeAppliedOn(Optional<?> obj) {
        if (obj == null) {
            obj = Optional.absent();
        }
        return obj.equals(oldValue) || obj.equals(newValue);
    }

    @Override
    public String asText() {
        if (getType().equals(TYPE.MODIFIED)) {
            return getType().name().toCharArray()[0] + "\t" + attributeValueAsString(oldValue)
                    + "\t" + attributeValueAsString(newValue);
        } else if (getType().equals(TYPE.ADDED)) {
            return getType().name().toCharArray()[0] + "\t" + attributeValueAsString(newValue);
        } else {
            return getType().name().toCharArray()[0] + "\t" + attributeValueAsString(oldValue);
        }
    }

    @Override
    public boolean equals(Object o) {
        // TODO: this is a temporary simple comparison. Should be more elaborate
        if (!(o instanceof GenericAttributeDiffImpl)) {
            return false;
        }
        GenericAttributeDiffImpl d = (GenericAttributeDiffImpl) o;
        return d.oldValue.equals(oldValue) && d.newValue.equals(newValue);
    }

    @Override
    public boolean conflicts(AttributeDiff ad) {
        if (ad instanceof GenericAttributeDiffImpl) {
            GenericAttributeDiffImpl gad = (GenericAttributeDiffImpl) ad;
            return !Objects.equal(gad.newValue, newValue);
        } else {
            return true;
        }
    }

}
