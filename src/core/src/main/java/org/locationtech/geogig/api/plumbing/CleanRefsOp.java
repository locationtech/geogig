/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.api.plumbing;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.porcelain.MergeOp;
import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.Blobs;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

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
public class CleanRefsOp extends AbstractGeoGigOp<ImmutableList<String>> {

    @Override
    protected ImmutableList<String> _call() {
        Builder<String> cleaned = new ImmutableList.Builder<String>();
        Optional<Ref> ref = command(RefParse.class).setName(Ref.MERGE_HEAD).call();
        if (ref.isPresent()) {
            cleaned.add(Ref.MERGE_HEAD);
            command(UpdateRef.class).setDelete(true).setName(Ref.MERGE_HEAD).call();
        }
        ref = command(RefParse.class).setName(Ref.ORIG_HEAD).call();
        if (ref.isPresent()) {
            cleaned.add(Ref.ORIG_HEAD);
            command(UpdateRef.class).setDelete(true).setName(Ref.ORIG_HEAD).call();
        }
        ref = command(RefParse.class).setName(Ref.CHERRY_PICK_HEAD).call();
        if (ref.isPresent()) {
            cleaned.add(Ref.CHERRY_PICK_HEAD);
            command(UpdateRef.class).setDelete(true).setName(Ref.CHERRY_PICK_HEAD).call();
        }
        BlobStore blobStore = context.blobStore();
        Optional<String> blob = Blobs.getBlobAsString(blobStore, MergeOp.MERGE_MSG);
        if (blob.isPresent()) {
            cleaned.add(MergeOp.MERGE_MSG);
            blobStore.removeBlob(MergeOp.MERGE_MSG);
        }
        return cleaned.build();
    }

}
