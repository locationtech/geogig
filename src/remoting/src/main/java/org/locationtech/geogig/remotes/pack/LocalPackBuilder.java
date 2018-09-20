/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.remotes.pack;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import org.locationtech.geogig.repository.Repository;

public class LocalPackBuilder extends AbstractPackBuilder {

    private final Repository localRepo;

    public LocalPackBuilder(Repository localRepo) {
        checkNotNull(localRepo);
        checkState(localRepo.isOpen());
        this.localRepo = localRepo;
    }

    @Override
    public Pack build() {
        PackImpl pack = new PackImpl(localRepo, tags, missingCommits, missingIndexes);
        return pack;
    }

}
