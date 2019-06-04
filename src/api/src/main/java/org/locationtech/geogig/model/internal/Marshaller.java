/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.internal;

import lombok.NonNull;

/**
 * A simple value marshalling/unmarshalling interface
 */
public interface Marshaller {

    public @NonNull String marshall(@NonNull Object value);

    public @NonNull Object unmarshall(@NonNull String source, @NonNull Class<?> target);
}
