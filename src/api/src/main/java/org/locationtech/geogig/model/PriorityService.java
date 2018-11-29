/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.model;

import java.util.ServiceLoader;

/**
 * @since 1.4
 */
public interface PriorityService {

    /**
     * Defines a priority when loaded using {@link ServiceLoader}, the higher priority wins in case
     * there are several implementations, with the lowest priority being {@code 0}.
     */
    public int getPriority();
}
