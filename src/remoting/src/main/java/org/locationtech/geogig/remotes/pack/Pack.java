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

import java.util.List;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.remotes.RefDiff;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.ProgressListener;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

/**
 * @author groldan
 *
 */
public interface Pack {

    public static @Value @Builder class IndexDef {
        private final @NonNull IndexInfo index;

        private final @NonNull ObjectId canonical;

        private final @NonNull ObjectId parentIndexTreeId, indexTreeId;
    }

    /**
     * @param target
     * @param progress
     * @return
     */
    public List<RefDiff> applyTo(PackProcessor target, ProgressListener progress);
}
