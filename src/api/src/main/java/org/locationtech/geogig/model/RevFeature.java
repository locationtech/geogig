/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model;

import java.util.function.Consumer;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

public interface RevFeature extends RevObject {

    /**
     * @return a list of values, with {@link Optional#absent()} representing a null value
     */
    public ImmutableList<Optional<Object>> getValues();

    /**
     * @return the number of attribute values in the feature
     */
    public int size();

    /**
     * @return the feature attribute value at the provided {@code index}, or
     *         {@link Optional#absent() absent} if the object at that index is {@code null} (not to
     *         be misinterpreted as absent if the index is out of bounds, in which case an exception
     *         is thrown)
     */
    public Optional<Object> get(final int index);

    /**
     * Performs the given action for each attribute in the feature, in it's natural order, until all
     * elements have been processed or the action throws an exception.
     * 
     * @param consumer the action to perform on each attribute value
     */
    public void forEach(final Consumer<Object> consumer);

}