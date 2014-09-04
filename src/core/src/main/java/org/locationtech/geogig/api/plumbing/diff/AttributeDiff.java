/* Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.plumbing.diff;

import com.google.common.base.Optional;

/**
 * An interface to implement by all classes storing differences of an attribute value between 2
 * version of a feature
 * 
 * @param <T>
 */
public interface AttributeDiff {

    public enum TYPE {
        MODIFIED, REMOVED, ADDED, NO_CHANGE
    }

    /**
     * Returns the type of difference that this object represent
     * 
     * @return the type of difference that this object represent
     */
    public TYPE getType();

    /**
     * Returns a reversed version of the attribute difference
     * 
     * @return a reversed version of the attribute difference
     */
    public AttributeDiff reversed();

    /**
     * Returns true if the diff can be applied on the passed value. Return false if the passed value
     * does not represent the old state represented by this attribute difference
     * 
     * @param obj the object representing the original (old) state of the attribute. If the value is
     *        null, it represents that the attribute did not exist previously
     * @return true if the diff can be applied to the passed object
     */
    public boolean canBeAppliedOn(Optional<?> obj);

    /**
     * applies the difference on the passed object, if possible.
     * 
     * @param obj the object representing the original (old) state of the attribute. If the value is
     *        null, it represents that the attribute did not exist previously
     */
    public Optional<?> applyOn(Optional<?> obj);

    /**
     * serializes the difference as text
     */
    public String asText();

    /**
     * accessor for the old value
     */
    public Optional<?> getOldValue();

    /**
     * accessor for the new value
     */
    public Optional<?> getNewValue();

    /**
     * Return true if the changes represented by AttributeDiff are in conflict with changes
     * represented by the passed one
     * 
     * @param ad the AttributeDiff to check against
     * @return true if the changes represented by AttributeDiff are in conflict with changes
     *         represented by the passed one
     */
    public boolean conflicts(AttributeDiff otherAd);

}
