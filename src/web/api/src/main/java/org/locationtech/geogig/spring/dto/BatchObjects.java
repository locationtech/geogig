/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.spring.dto;

import java.io.OutputStream;
import java.io.Writer;
import java.util.List;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.remote.http.BinaryPackedObjects;
import org.locationtech.geogig.remotes.internal.Deduplicator;
import org.locationtech.geogig.remotes.internal.ObjectFunnel;
import org.locationtech.geogig.remotes.internal.ObjectFunnels;
import org.locationtech.geogig.storage.datastream.DataStreamRevObjectSerializerV1;
import org.springframework.http.MediaType;

import com.google.common.io.CountingOutputStream;

/**
 *
 */
public class BatchObjects extends LegacyRepoResponse {

    private BinaryPackedObjects packer;

    private List<ObjectId> want;

    private List<ObjectId> have;

    private Deduplicator deduplicator;

    public BinaryPackedObjects getPacker() {
        return packer;
    }

    public BatchObjects setPacker(BinaryPackedObjects packer) {
        this.packer = packer;
        return this;
    }

    public List<ObjectId> getWant() {
        return want;
    }

    public BatchObjects setWant(List<ObjectId> want) {
        this.want = want;
        return this;
    }

    public List<ObjectId> getHave() {
        return have;
    }

    public BatchObjects setHave(List<ObjectId> have) {
        this.have = have;
        return this;
    }

    public Deduplicator getDeduplicator() {
        return deduplicator;
    }

    public BatchObjects setDeduplicator(Deduplicator deduplicator) {
        this.deduplicator = deduplicator;
        return this;
    }

    @Override
    protected void encode(Writer out) {
        throw new UnsupportedOperationException(
                "BatchObjects does not support java.io.Writer. Use java.io.OutputStream instead");
    }

    @Override
    protected void encode(OutputStream out) {

        if (packer != null && deduplicator != null) {
            CountingOutputStream counting = new CountingOutputStream(out);
            OutputStream output = counting;
            try {
                ObjectFunnel funnel;
                funnel = ObjectFunnels.newFunnel(output, DataStreamRevObjectSerializerV1.INSTANCE);
                packer.write(funnel, want, have, false, deduplicator);
                counting.flush();
                funnel.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                deduplicator.release();
            }
        }
    }

    @Override
    public MediaType resolveMediaType(MediaType defaultMediaType) {
        return MediaType.APPLICATION_OCTET_STREAM;
    }


}
