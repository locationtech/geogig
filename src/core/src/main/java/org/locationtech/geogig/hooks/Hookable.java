/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.hooks;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.locationtech.geogig.repository.AbstractGeoGigOp;

/**
 * An annotation to indicate that a {@link AbstractGeoGigOp GeoGig operation} allows hooks
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Hookable {
    /**
     * the name used to identify the script files corresponding to the hooks for the class annotated
     * with this annotation
     */
    public String name();
}