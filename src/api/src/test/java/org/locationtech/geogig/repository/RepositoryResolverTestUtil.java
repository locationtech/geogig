/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository;

import java.util.List;

/**
 * Test Utility to disable RepositoryResolvers.
 */
public class RepositoryResolverTestUtil {

    /**
     * Sets a list of disabled RepositoryResolvers.
     * @param disabledResolvers List of class names of RepositoryResolver implementations that
     * should be disabled. Example: "org.locationtech.geogig.repository.impl.FileRepositoryResolver"
     * to disable the File/Directory resolver for URI scheme "file".
     */
    public static void setDisabledResolvers(List<String> disabledResolvers) {
        RepositoryResolver.setDisabledResolvers(disabledResolvers);
    }

    /**
     * Clears the list of disabled RepositoryResolvers.
     */
    public static void clearDisabledResolverList() {
        RepositoryResolver.clearDisabledResolvers();
    }
}
