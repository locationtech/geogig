/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing.merge;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import org.locationtech.geogig.porcelain.MergeOp;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.storage.BlobStore;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.io.CharStreams;

public class ReadMergeCommitMessageOp extends AbstractGeoGigOp<String> {

    @Override
    protected String _call() {
        BlobStore blobStore = context.blobStore();

        Optional<InputStream> blobAsStream = blobStore.getBlobAsStream(MergeOp.MERGE_MSG);
        if (!blobAsStream.isPresent()) {
            return "";
        }
        try (InputStream in = blobAsStream.get()) {
            List<String> lines = CharStreams.readLines(new InputStreamReader(in, Charsets.UTF_8));
            return Joiner.on("\n").join(lines);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}