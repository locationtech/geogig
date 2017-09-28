/* Copyright (c) 2013-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.remotes.internal;

public class HeapDeduplicationService implements DeduplicationService {
    @Override
    public Deduplicator createDeduplicator() {
        return new HeapDeduplicator();
    }
}
