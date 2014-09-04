/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

public interface RevFeature extends RevObject {

    /**
     * @return a list of values, with {@link Optional#absent()} representing a null value
     */
    public ImmutableList<Optional<Object>> getValues();

}