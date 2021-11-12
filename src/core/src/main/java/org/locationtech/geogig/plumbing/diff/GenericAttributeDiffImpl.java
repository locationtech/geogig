/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing.diff;

import static org.locationtech.geogig.base.Preconditions.checkArgument;

import java.util.Optional;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.base.Preconditions;
import org.locationtech.geogig.storage.text.TextValueSerializer;

import com.google.common.base.Objects;

/**
 * Generic implementation of a difference between two values for a given attribute
 * 
 */
public class GenericAttributeDiffImpl implements AttributeDiff {

    /**
     * The new value. Null if it does not exist (the attribute has been removed)
     */
    private @Nullable Object newValue;

    /**
     * The old value. Null if it did not exist (the attribute has been added)
     */
    private @Nullable Object oldValue;

    /**
     * @param oldValue The new value. {@code null} if it does not exist (the attribute has been
     *        removed)
     * @param newValue The old value. {@code null} if it did not exist (the attribute has been
     *        added)
     */
    public GenericAttributeDiffImpl(@Nullable Object oldValue, @Nullable Object newValue) {
        // checkArgument(!(oldValue == null && newValue == null),
        // "both sides of the attribute diff can't be null");
        checkArgument(!(oldValue instanceof Optional));
        checkArgument(!(newValue instanceof Optional));
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public @Override TYPE getType() {
        TYPE type;
        if (java.util.Objects.equals(oldValue, newValue)) {
            type = TYPE.NO_CHANGE;
        } else if (null == newValue) {
            type = TYPE.REMOVED;
        } else if (null == oldValue) {
            type = TYPE.ADDED;
        } else {
            type = TYPE.MODIFIED;
        }
        return type;
    }

    public @Override Object getOldValue() {
        return oldValue;
    }

    public @Override Object getNewValue() {
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

    private CharSequence attributeValueAsString(@Nullable Object value) {
        if (null == value) {
            return "NULL";
        }
        return TextValueSerializer.asString(value);
    }

    public @Override AttributeDiff reversed() {
        return new GenericAttributeDiffImpl(newValue, oldValue);
    }

    public @Override Object applyOn(@Nullable Object obj) {
        Preconditions.checkState(canBeAppliedOn(obj));
        return newValue;
    }

    public @Override boolean canBeAppliedOn(@Nullable Object obj) {
        checkArgument(!(obj instanceof Optional));
        return Objects.equal(obj, oldValue) || Objects.equal(obj, newValue);
    }

    public @Override String asText() {
        if (getType().equals(TYPE.MODIFIED)) {
            return getType().name().toCharArray()[0] + "\t" + attributeValueAsString(oldValue)
                    + "\t" + attributeValueAsString(newValue);
        } else if (getType().equals(TYPE.ADDED)) {
            return getType().name().toCharArray()[0] + "\t" + attributeValueAsString(newValue);
        } else {
            return getType().name().toCharArray()[0] + "\t" + attributeValueAsString(oldValue);
        }
    }

    public @Override boolean equals(Object o) {
        // TODO: this is a temporary simple comparison. Should be more elaborate
        if (!(o instanceof GenericAttributeDiffImpl)) {
            return false;
        }
        GenericAttributeDiffImpl d = (GenericAttributeDiffImpl) o;
        return d.oldValue.equals(oldValue) && d.newValue.equals(newValue);
    }

    public @Override boolean conflicts(AttributeDiff ad) {
        if (ad instanceof GenericAttributeDiffImpl) {
            GenericAttributeDiffImpl gad = (GenericAttributeDiffImpl) ad;
            return !Objects.equal(gad.newValue, newValue);
        } else {
            return true;
        }
    }

}
