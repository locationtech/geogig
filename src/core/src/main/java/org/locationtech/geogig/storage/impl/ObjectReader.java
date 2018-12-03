/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.impl;

import java.io.InputStream;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.jts.geom.GeometryFactory;

/**
 * Provides a base interface for reading GeoGig objects from an {@link InputStream}.
 * 
 * @param <T> the type of the object to read
 */
public interface ObjectReader<T extends RevObject> {

    /**
     * Hint of type {@link GeometryFactory}
     */
    public static final String JTS_GEOMETRY_FACTORY = "JTS_GEOMETRY_FACTORY";

    /**
     * Hint of type Boolean
     */
    public static final String USE_PROVIDED_FID = "USE_PROVIDED_FID";

    /**
     * Reads an object from the given input stream and assigns it the provided {@link ObjectId id}.
     * 
     * @param id the id for the object to create
     * @param rawData the input stream of the object
     * @return the final object
     * @throws IllegalArgumentException if the provided stream does not represents an object of the
     *         required type
     */
    public T read(ObjectId id, InputStream rawData) throws IllegalArgumentException;

}
