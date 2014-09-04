/* Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.mongo;

import com.google.common.base.Objects;

/**
 * A value object containing connection info for Mongo databases.
 * These are used as keys for the connection managers.
 * 
 * @see MongoObjectDatabase
 * @see MongoGraphDatabase
 * @see MongoStagingDatabase
 */
final class MongoAddress {
    private final String uri;

    public MongoAddress(String uri) {
        this.uri = uri;
    }

    public String getUri() {
        return uri;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(uri);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(MongoAddress.class).addValue(uri).toString();
    }
}
