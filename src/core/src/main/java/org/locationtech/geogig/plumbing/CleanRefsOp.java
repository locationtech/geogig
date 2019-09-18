/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.porcelain.MergeOp;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;
import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.RefChange;
import org.locationtech.geogig.storage.impl.Blobs;

import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Cleans refs and blobs left by conflict-generating operations.
 * <p>
 * The refs that are cleaned up:
 * <ul>
 * <li>MERGE_HEAD
 * <li>ORIG_HEAD
 * <li>CHERRY_PICK_HEAD
 * </ul>
 * <p>
 * The blobs that are cleaned up:
 * <ul>
 * <li>MERGE_MSG
 * </ul>
 */
@Accessors(fluent = true)
public class CleanRefsOp extends AbstractGeoGigOp<List<String>> {

    private @Setter String reason;

    protected @Override List<String> _call() {
        UpdateRefs cmd = command(UpdateRefs.class).setReason(reason == null ? "" : reason);
        cmd.remove(Ref.MERGE_HEAD);
        cmd.remove(Ref.ORIG_HEAD);
        cmd.remove(Ref.CHERRY_PICK_HEAD);

        List<String> cleaned = cmd.call().stream()//
                .map(RefChange::oldValue)//
                .filter(Optional::isPresent)//
                .map(Optional::get)//
                .map(Ref::getName)//
                .collect(Collectors.toList());

        BlobStore blobStore = context.blobStore();
        Optional<byte[]> blob = Blobs.getBlob(blobStore, MergeOp.MERGE_MSG);
        if (blob.isPresent()) {
            cleaned.add(MergeOp.MERGE_MSG);
            blobStore.removeBlob(MergeOp.MERGE_MSG);
        }
        return cleaned;
    }

}
