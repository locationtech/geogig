/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.flatbuffers;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.flatbuffers.generated.v1.ObjectType;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.FieldType;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevPerson;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.ValueArray;
import org.locationtech.geogig.model.impl.RevObjectFactoryImpl;
import org.locationtech.jts.geom.Envelope;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.PropertyDescriptor;

import com.google.flatbuffers.FlatBufferBuilder;

import lombok.NonNull;

public class FlatBuffersRevObjectFactory implements RevObjectFactory {

    private static RevObjectFactory DEFAULT_IMPL = new RevObjectFactoryImpl();

    private FlatBuffers encoder = new FlatBuffers();

    private FlatBufferBuilder newBuilder() {
        FlatBufferBuilder fbb = FlatBuffersRevObjectSerializer.WRITE_BUFFERS.get();
        fbb.clear();
        return fbb;
    }

    private ByteBuffer copy(ByteBuffer dataBuffer) {
        ByteBuffer bb = ByteBuffer.allocate(dataBuffer.remaining());
        bb.put(dataBuffer);
        bb.rewind();
        return bb;
    }

    public @Override int getPriority() {
        return 1;
    }

    public @Override RevPerson createPerson(String name, String email, long timeStamp,
            int timeZoneOffset) {
        return DEFAULT_IMPL.createPerson(name, email, timeStamp, timeZoneOffset);
    }

    public @Override Node createNode(String name, ObjectId objectId, ObjectId metadataId, TYPE type,
            @Nullable Envelope bounds, @Nullable Map<String, Object> extraData) {

        return DEFAULT_IMPL.createNode(name, objectId, metadataId, type, bounds, extraData);
    }

    public @Override @NonNull RevCommit createCommit(@NonNull ObjectId id, @NonNull ObjectId treeId,
            @NonNull List<ObjectId> parents, @NonNull RevPerson author,
            @NonNull RevPerson committer, @NonNull String message) {
        if (parents.indexOf(null) > -1) {
            throw new NullPointerException("null parent at index " + parents.indexOf(null));
        }

        FlatBufferBuilder fbb = newBuilder();
        int objOffset = encoder.writeCommit(fbb, treeId, parents, author, committer, message);
        encoder.writeRevisionObject(ObjectType.Commit, objOffset, fbb);
        return (RevCommit) encoder.decode(id, copy(fbb.dataBuffer()));
    }

    public @Override @NonNull RevTree createTree(final @NonNull ObjectId id, final long size,
            @NonNull List<Node> trees, @NonNull List<Node> features) {

        FlatBufferBuilder fbb = newBuilder();
        int objOffset = encoder.writeLeafTree(fbb, size, trees, features);
        encoder.writeRevisionObject(ObjectType.LeafTree, objOffset, fbb);
        return (RevTree) encoder.decode(id, copy(fbb.dataBuffer()));
    }

    public @Override @NonNull RevTree createTree(final @NonNull ObjectId id, final long size,
            final int childTreeCount, @NonNull SortedSet<Bucket> buckets) {
        if (size < 0L) {
            throw new IllegalArgumentException("Cannot create a tree with negative size: " + size);
        }
        if (childTreeCount < 0L) {
            throw new IllegalArgumentException(
                    "Cannot create a tree with negative child tree count: " + childTreeCount);
        }

        FlatBufferBuilder fbb = newBuilder();
        int objOffset = encoder.writeNodeTree(fbb, size, childTreeCount, buckets);
        encoder.writeRevisionObject(ObjectType.NodeTree, objOffset, fbb);
        return (RevTree) encoder.decode(id, copy(fbb.dataBuffer()));
    }

    public @Override Bucket createBucket(@NonNull ObjectId bucketTree, int bucketIndex,
            @Nullable Envelope bounds) {
        if (bucketIndex < 0) {
            throw new IllegalArgumentException(
                    "Bucket cannot have a negative index: " + bucketIndex);
        }
        return DEFAULT_IMPL.createBucket(bucketTree, bucketIndex, bounds);
    }

    public @Override @NonNull RevTag createTag(@NonNull ObjectId id, @NonNull String name,
            @NonNull ObjectId commitId, @NonNull String message, @NonNull RevPerson tagger) {

        FlatBufferBuilder fbb = newBuilder();
        int objOffset = encoder.writeTag(fbb, name, commitId, message, tagger);
        encoder.writeRevisionObject(ObjectType.Tag, objOffset, fbb);
        return (RevTag) encoder.decode(id, copy(fbb.dataBuffer()));
    }

    public @Override @NonNull RevFeatureType createFeatureType(@NonNull ObjectId id,
            @NonNull FeatureType ftype) {
        Collection<PropertyDescriptor> descriptors = ftype.getDescriptors();
        descriptors.forEach(d -> {
            Class<?> binding = d.getType().getBinding();
            Objects.requireNonNull(binding,
                    "got null binding for attribute " + d.getName().getLocalPart());
            FieldType fieldType = FieldType.forBinding(binding);
            if (FieldType.NULL == fieldType || FieldType.UNKNOWN == fieldType) {
                String msg = String.format(
                        "Attribute %s of FeatureType %s is of an unsupported type: %s",
                        d.getName().getLocalPart(), ftype.getName().getLocalPart(),
                        binding.getName());
                throw new IllegalArgumentException(msg);
            }
        });

        FlatBufferBuilder fbb = newBuilder();
        int objOffset = encoder.writeSimpleFeatureType(fbb, (SimpleFeatureType) ftype);
        encoder.writeRevisionObject(ObjectType.SimpleFeatureType, objOffset, fbb);
        return (RevFeatureType) encoder.decode(id, copy(fbb.dataBuffer()));
    }

    public @Override @NonNull RevFeature createFeature(@NonNull ObjectId id,
            @NonNull List<Object> values) {

        FlatBufferBuilder fbb = newBuilder();
        int objOffset = encoder.writeFeature(fbb, values);
        encoder.writeRevisionObject(ObjectType.Feature, objOffset, fbb);
        return (RevFeature) encoder.decode(id, copy(fbb.dataBuffer()));
    }

    public @Override @NonNull RevFeature createFeature(@NonNull ObjectId id,
            @NonNull Object... values) {
        return createFeature(id, Arrays.asList(values));
    }

    public @Override @NonNull ValueArray createValueArray(@NonNull List<Object> values) {
        FlatBufferBuilder fbb = newBuilder();
        encoder.writeValueArray(fbb, values);
        return encoder.decodeValueArray(copy(fbb.dataBuffer()));
    }

    public @Override @NonNull ValueArray createValueArray(@NonNull Object... values) {
        return createValueArray(Arrays.asList(values));
    }

}
