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
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.referencing.CRS;
import org.geotools.referencing.CRS.AxisOrder;
import org.geotools.referencing.crs.DefaultEngineeringCRS;
import org.geotools.referencing.wkt.Formattable;
import org.locationtech.geogig.flatbuffers.generated.Commit;
import org.locationtech.geogig.flatbuffers.generated.Feature;
import org.locationtech.geogig.flatbuffers.generated.LeafTree;
import org.locationtech.geogig.flatbuffers.generated.NodeTree;
import org.locationtech.geogig.flatbuffers.generated.ObjectType;
import org.locationtech.geogig.flatbuffers.generated.Person;
import org.locationtech.geogig.flatbuffers.generated.RevisionObject;
import org.locationtech.geogig.flatbuffers.generated.SHA;
import org.locationtech.geogig.flatbuffers.generated.SimpleAttributeDescriptor;
import org.locationtech.geogig.flatbuffers.generated.SimpleFeatureType;
import org.locationtech.geogig.flatbuffers.generated.Tag;
import org.locationtech.geogig.flatbuffers.generated.values.Bounds;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.FieldType;
import org.locationtech.geogig.model.HashObjectFunnels;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.model.RevPerson;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.jts.geom.Envelope;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.flatbuffers.FlatBufferBuilder;

import lombok.NonNull;

final class FlatBuffers {

    private static final int[] EMPTY_OFFSETS_VECTOR = {};

    public byte[] encode(@NonNull RevObject o) {
        FlatBufferBuilder fbb = new FlatBufferBuilder();
        encode(o, fbb);
        ByteBuffer dataBuffer = fbb.dataBuffer();
        byte[] array = new byte[dataBuffer.remaining()];
        dataBuffer.get(array);
        return array;
    }

    public int encode(@NonNull RevObject o, @NonNull FlatBufferBuilder fbb) {

        final byte objectType;
        final int objOffset;

        switch (o.getType()) {
        case COMMIT:
            objectType = ObjectType.Commit;
            objOffset = write((RevCommit) o, fbb);
            break;
        case FEATURE:
            objectType = ObjectType.Feature;
            objOffset = write((RevFeature) o, fbb);
            break;
        case FEATURETYPE:
            objectType = ObjectType.SimpleFeatureType;
            objOffset = write((RevFeatureType) o, fbb);
            break;
        case TAG:
            objectType = ObjectType.Tag;
            objOffset = write((RevTag) o, fbb);
            break;
        case TREE:
            RevTree tree = (RevTree) o;
            if (tree.bucketsSize() == 0) {
                objectType = ObjectType.LeafTree;
                objOffset = writeLeafTree(tree, fbb);
            } else {
                objectType = ObjectType.NodeTree;
                objOffset = writeNodeTree(tree, fbb);
            }
            break;
        default:
            throw new IllegalArgumentException("Invalid RevObject type: " + o.getType());
        }

        return writeRevisionObject(objectType, objOffset, fbb);
    }

    final int writeRevisionObject(final byte objectType, final int objOffset,
            FlatBufferBuilder fbb) {
        RevisionObject.startRevisionObject(fbb);
        RevisionObject.addObjectType(fbb, objectType);
        switch (objectType) {
        case ObjectType.Commit:
            RevisionObject.addCommit(fbb, objOffset);
            break;
        case ObjectType.Feature:
            RevisionObject.addFeature(fbb, objOffset);
            break;
        case ObjectType.LeafTree:
            RevisionObject.addLeafTree(fbb, objOffset);
            break;
        case ObjectType.NodeTree:
            RevisionObject.addNodeTree(fbb, objOffset);
            break;
        case ObjectType.Tag:
            RevisionObject.addTag(fbb, objOffset);
            break;
        case ObjectType.SimpleFeatureType:
            RevisionObject.addSimpleFeatureType(fbb, objOffset);
            break;
        default:
            throw new IllegalArgumentException("Unknown ObjectType: " + objectType);
        }
        int offset = RevisionObject.endRevisionObject(fbb);
        RevisionObject.finishRevisionObjectBuffer(fbb, offset);
        return offset;
    }

    public RevObject decode(@Nullable ObjectId id, @Nonnull byte[] data, @Nonnegative int offset,
            @Nonnegative int length) {

        final ByteBuffer dataBuffer = ByteBuffer.wrap(data, offset, length);
        return decode(id, dataBuffer);
    }

    public RevObject decode(@Nullable ObjectId id, final @NonNull ByteBuffer dataBuffer) {
        final RevisionObject revObject = RevisionObject.getRootAsRevisionObject(dataBuffer);
        final byte objectType = revObject.objectType();
        final FBRevObject<?> ret;
        switch (objectType) {
        case ObjectType.Commit:
            Commit c = revObject.commit();
            ret = new FBCommit(c);
            break;
        case ObjectType.LeafTree:
            LeafTree leafTree = revObject.leafTree();
            ret = new FBLeafTree(leafTree);
            break;
        case ObjectType.NodeTree:
            NodeTree nodeTree = revObject.nodeTree();
            ret = new FBNodeTree(nodeTree);
            break;
        case ObjectType.Feature:
            Feature f = revObject.feature();
            ret = new FBFeature(f);
            break;
        case ObjectType.Tag:
            Tag tag = revObject.tag();
            ret = new FBTag(tag);
            break;
        case ObjectType.SimpleFeatureType:
            SimpleFeatureType type = revObject.simpleFeatureType();
            ret = new FBSimpleFeatureType(type);
            break;
        default:
            throw new IllegalArgumentException("Unknown object type: " + objectType);
        }

        if (id == null) {
            id = HashObjectFunnels.hashObject(ret);
        }
        ret.setId(id);
        return ret;
    }

    private int write(@NonNull ObjectId id, FlatBufferBuilder builder) {
        return SHA.createSHA(builder, RevObjects.h1(id), RevObjects.h2(id), RevObjects.h3(id));
    }

    private int write(RevPerson p, FlatBufferBuilder fbb) {
        int nameOffset = 0;
        int emailOffset = 0;

        Optional<String> name = p.getName();
        Optional<String> email = p.getEmail();

        if (name.isPresent()) {
            nameOffset = fbb.createString(name.get());
        }
        if (email.isPresent()) {
            emailOffset = fbb.createString(email.get());
        }
        Person.startPerson(fbb);
        if (name.isPresent()) {
            Person.addName(fbb, nameOffset);
        }
        if (email.isPresent()) {
            Person.addEmail(fbb, emailOffset);
        }
        Person.addTimestamp(fbb, p.getTimestamp());
        Person.addTimezoneOffset(fbb, p.getTimeZoneOffset());
        return Person.endPerson(fbb);
    }

    private int write(@NonNull RevCommit c, @NonNull FlatBufferBuilder builder) {
        return writeCommit(builder, c.getTreeId(), c.getParentIds(), c.getAuthor(),
                c.getCommitter(), c.getMessage());
    }

    public int writeCommit(FlatBufferBuilder builder, ObjectId treeId, List<ObjectId> parents,
            RevPerson author, RevPerson committer, String message) {

        int messageOffset = builder.createString(message);
        int authorOffset = write(author, builder);
        int committerOffset = write(committer, builder);

        // buffers are written "back to front", so write them in reverse order to preserve the
        // expected read order
        int parentIdsOffset = 0;
        if (!parents.isEmpty()) {
            Commit.startParentIdsVector(builder, parents.size());
            for (int i = parents.size() - 1; i >= 0; i--) {
                write(parents.get(i), builder);
            }
            parentIdsOffset = builder.endVector();
        }
        Commit.startCommit(builder);
        Commit.addParentIds(builder, parentIdsOffset);
        Commit.addTreeId(builder, write(treeId, builder));
        Commit.addMessage(builder, messageOffset);
        Commit.addAuthor(builder, authorOffset);
        Commit.addCommitter(builder, committerOffset);
        return Commit.endCommit(builder);
    }

    public int write(RevTree tree, FlatBufferBuilder builder) {
        if (tree.bucketsSize() == 0) {
            return writeLeafTree(tree, builder);
        }
        return writeNodeTree(tree, builder);
    }

    private int writeLeafTree(RevTree tree, FlatBufferBuilder builder) {
        return writeLeafTree(builder, tree.size(), tree.trees(), tree.features());
    }

    public int writeLeafTree(FlatBufferBuilder builder, final long size, final List<Node> trees,
            final List<Node> features) {

        final int numNodes = trees.size() + features.size();
        final int[] offsets = new int[numNodes];

        forEachNode(trees, features, offsets, n -> builder.createString(n.getName()));
        final int nodeNamesOffset = LeafTree.createNodesNamesVector(builder, offsets);

        LeafTree.startNodesBoundsVector(builder, numNodes);
        forEachNodeReverseOrder(trees, features, n -> write(n.getObjectId(), builder));
        final int nodeIdsOffset = builder.endVector();
        final int nodeBoundsOffset;
        final int nodesExtraDataOffset;
        final Envelope buff = new Envelope();
        final boolean[] hasBoundsOrExtraData = hasBoundsOrExtraData(trees, features, buff);
        if (hasBoundsOrExtraData[0]) {
            LeafTree.startNodesBoundsVector(builder, numNodes);
            forEachNodeReverseOrder(trees, features, n -> {
                buff.setToNull();
                n.expand(buff);
                write(buff, builder);
            });
            nodeBoundsOffset = builder.endVector();
        } else {
            nodeBoundsOffset = 0;
        }
        if (hasBoundsOrExtraData[1]) {
            forEachNodeReverseOrder(trees, features, offsets,
                    n -> ValueSerializer.writeDictionary(builder, n.getExtraData()));
            nodesExtraDataOffset = LeafTree.createNodesExtraDataVector(builder, offsets);
        } else {
            nodesExtraDataOffset = 0;
        }

        LeafTree.startLeafTree(builder);
        LeafTree.addSize(builder, size);
        LeafTree.addNumDirectTreeNodes(builder, trees.size());
        LeafTree.addNodesNames(builder, nodeNamesOffset);
        LeafTree.addNodesIds(builder, nodeIdsOffset);
        if (nodeBoundsOffset > 0) {
            LeafTree.addNodesBounds(builder, nodeBoundsOffset);
        }
        if (nodesExtraDataOffset > 0) {
            LeafTree.addNodesExtraData(builder, nodesExtraDataOffset);
        }
        return LeafTree.endLeafTree(builder);
    }

    private int writeNodeTree(RevTree tree, FlatBufferBuilder builder) {

        final int bucketsOffset = tree.bucketsSize() == 0 ? 0
                : NodeTree.createBucketsVector(builder, writeBuckets(tree, builder));

        NodeTree.startNodeTree(builder);
        NodeTree.addSize(builder, tree.size());
        NodeTree.addNumTreesRecursive(builder, tree.numTrees());
        if (bucketsOffset > 0) {
            NodeTree.addBuckets(builder, bucketsOffset);
        }
        return NodeTree.endNodeTree(builder);
    }

    public int writeNodeTree(@NonNull FlatBufferBuilder builder, final long size,
            final int childTreeCount, final @NonNull SortedSet<Bucket> buckets) {
        Preconditions.checkArgument(!buckets.isEmpty());

        final int bucketsOffset = NodeTree.createBucketsVector(builder,
                writeBuckets(buckets.size(), buckets, builder));

        NodeTree.startNodeTree(builder);
        NodeTree.addSize(builder, size);
        NodeTree.addNumTreesRecursive(builder, childTreeCount);
        NodeTree.addBuckets(builder, bucketsOffset);
        return NodeTree.endNodeTree(builder);
    }

    private boolean[] hasBoundsOrExtraData(List<Node> trees, List<Node> features, Envelope buff) {
        boolean[] flags = hasBoundsOrExtraData(trees, buff);
        if (!flags[0] || !flags[1]) {
            boolean[] featuresFlags = hasBoundsOrExtraData(features, buff);
            flags[0] = flags[0] || featuresFlags[0];
            flags[1] = flags[1] || featuresFlags[1];
        }
        return flags;
    }

    private boolean[] hasBoundsOrExtraData(List<Node> nodes, Envelope buff) {
        boolean hasBounds = false;
        boolean hasExtraData = false;
        final int features = nodes.size();
        for (int i = 0; i < features; i++) {
            buff.setToNull();
            Node node = nodes.get(i);
            node.expand(buff);
            if (!buff.isNull()) {
                hasBounds = true;
            }
            Map<String, Object> extraData = node.getExtraData();
            if (!extraData.isEmpty()) {
                hasExtraData = true;
            }
            if (hasBounds && hasExtraData) {
                return new boolean[] { true, true };
            }
        }
        return new boolean[] { hasBounds, hasExtraData };
    }

    private void forEachNodeReverseOrder(List<Node> treeNodes, List<Node> featureNodes,
            Consumer<Node> consumer) {
        final int trees = treeNodes.size();
        final int features = featureNodes.size();
        for (int i = features - 1; i >= 0; i--) {
            consumer.accept(featureNodes.get(i));
        }
        for (int i = trees - 1; i >= 0; i--) {
            consumer.accept(treeNodes.get(i));
        }
    }

    private void forEachNode(List<Node> treeNodes, List<Node> featureNodes, int[] offsets,
            ToIntFunction<Node> nodeToOffset) {
        final int trees = treeNodes.size();
        final int features = featureNodes.size();

        int j = 0;
        for (int i = 0; i < trees; i++, j++) {
            offsets[j] = nodeToOffset.applyAsInt(treeNodes.get(i));
        }
        for (int i = 0; i < features; i++, j++) {
            offsets[j] = nodeToOffset.applyAsInt(featureNodes.get(i));
        }
    }

    private void forEachNodeReverseOrder(List<Node> treeNodes, List<Node> featureNodes,
            int[] offsets, ToIntFunction<Node> nodeToOffset) {
        final int trees = treeNodes.size();
        final int features = featureNodes.size();
        int j = 0;
        for (int i = features - 1; i >= 0; i--, j++) {
            offsets[j] = nodeToOffset.applyAsInt(featureNodes.get(i));
        }
        for (int i = trees - 1; i >= 0; i--, j++) {
            offsets[j] = nodeToOffset.applyAsInt(treeNodes.get(i));
        }
    }

    private int write(@NonNull RevTag t, @NonNull FlatBufferBuilder builder) {
        return writeTag(builder, t.getName(), t.getCommitId(), t.getMessage(), t.getTagger());
    }

    public int writeTag(@NonNull FlatBufferBuilder builder, @NonNull String name,
            @NonNull ObjectId commitId, @NonNull String message, @NonNull RevPerson tagger) {

        int nameOffset = builder.createString(name);
        int messageOffset = builder.createString(message);
        int taggerOffset = write(tagger, builder);

        Tag.startTag(builder);
        Tag.addCommitId(builder, write(commitId, builder));
        Tag.addName(builder, nameOffset);
        Tag.addMessage(builder, messageOffset);
        Tag.addTagger(builder, taggerOffset);
        return Tag.endTag(builder);
    }

    private int write(@NonNull RevFeature f, @NonNull FlatBufferBuilder builder) {
        final int size = f.size();
        int[] valueOffsets = new int[size];
        final AtomicInteger index = new AtomicInteger();
        f.forEach(val -> valueOffsets[index.getAndIncrement()] = ValueSerializer.encode(val,
                builder));
        int valuesOffset = Feature.createValuesVector(builder, valueOffsets);

        return Feature.createFeature(builder, valuesOffset);
    }

    public int writeFeature(FlatBufferBuilder builder, List<Object> values) {
        final int size = values.size();
        int[] valueOffsets = new int[size];
        final AtomicInteger index = new AtomicInteger();
        values.forEach(val -> valueOffsets[index.getAndIncrement()] = ValueSerializer.encode(val,
                builder));
        int valuesOffset = Feature.createValuesVector(builder, valueOffsets);

        return Feature.createFeature(builder, valuesOffset);
    }

    private @Nullable int[] writeBuckets(RevTree tree, FlatBufferBuilder builder) {
        return writeBuckets(tree.bucketsSize(), tree.getBuckets(), builder);
    }

    private @Nullable int[] writeBuckets(int bucketsSize, @NonNull Iterable<Bucket> buckets,
            FlatBufferBuilder builder) {
        if (0 == bucketsSize) {
            return EMPTY_OFFSETS_VECTOR;
        }
        final int[] offsets = new int[bucketsSize];
        final Envelope envBuff = new Envelope();
        final AtomicInteger i = new AtomicInteger();
        buckets.forEach(bucket -> {
            int off = writeBucket(builder, bucket, envBuff);
            offsets[i.getAndIncrement()] = off;
        });
        return offsets;
    }

    public int writeBucket(@NonNull FlatBufferBuilder builder, @NonNull Bucket bucket,
            @NonNull Envelope envBuff) {

        envBuff.setToNull();
        bucket.expand(envBuff);
        return writeBucket(builder, bucket.getIndex(), bucket.getObjectId(), envBuff);
    }

    public int writeBucket(@NonNull FlatBufferBuilder builder, int bucketIndex,
            @NonNull ObjectId treeId, @NonNull Envelope bounds) {
        org.locationtech.geogig.flatbuffers.generated.Bucket.startBucket(builder);
        org.locationtech.geogig.flatbuffers.generated.Bucket.addIndex(builder, bucketIndex);
        org.locationtech.geogig.flatbuffers.generated.Bucket.addTreeId(builder,
                write(treeId, builder));

        // Bounds is a struct, must be serialized inline
        int boundsOffset = bounds.isNull() ? 0 : write(bounds, builder);
        org.locationtech.geogig.flatbuffers.generated.Bucket.addBounds(builder, boundsOffset);
        return org.locationtech.geogig.flatbuffers.generated.Bucket.endBucket(builder);
    }

    private int write(Envelope env, FlatBufferBuilder builder) {
        return Bounds.createBounds(builder, (float) env.getMinX(), (float) env.getMinY(),
                (float) env.getMaxX(), (float) env.getMaxY());
    }

    private int write(@NonNull RevFeatureType t, @NonNull FlatBufferBuilder builder) {
        org.opengis.feature.simple.SimpleFeatureType type = (org.opengis.feature.simple.SimpleFeatureType) t
                .type();
        return writeSimpleFeatureType(builder, type);
    }

    public int writeSimpleFeatureType(@NonNull FlatBufferBuilder builder,
            @NonNull org.opengis.feature.simple.SimpleFeatureType type) {

        GeometryDescriptor defgeom = type.getGeometryDescriptor();

        int nameOffset = builder.createString(type.getName().getLocalPart());
        int defaultGeometryNameOffset = defgeom == null ? 0
                : builder.createString(defgeom.getLocalName());

        int[] attributesOffsets = writeAttributeDescriptors(type.getAttributeDescriptors(),
                builder);
        int attributesOffset = SimpleFeatureType.createAttributesVector(builder, attributesOffsets);

        return SimpleFeatureType.createSimpleFeatureType(builder, nameOffset,
                defaultGeometryNameOffset, attributesOffset);
    }

    private int[] writeAttributeDescriptors(List<AttributeDescriptor> attributeDescriptors,
            FlatBufferBuilder builder) {

        int[] offsets = new int[attributeDescriptors.size()];
        for (int i = 0; i < offsets.length; i++) {
            offsets[i] = writeAttributeDescriptor(attributeDescriptors.get(i), builder);
        }
        return offsets;
    }

    private int writeAttributeDescriptor(AttributeDescriptor att, FlatBufferBuilder builder) {

        int nameOffset;
        byte bindingCode;
        String srsName = null;
        String wkt = null;

        nameOffset = builder.createString(att.getLocalName());
        Class<?> binding = att.getType().getBinding();
        FieldType fieldType = FieldType.forBinding(binding);
        Preconditions.checkArgument(fieldType != FieldType.UNKNOWN);
        bindingCode = fieldType.getTag();// just because they do match
        boolean geometric = att instanceof GeometryDescriptor;
        if (geometric) {
            GeometryDescriptor gd = (GeometryDescriptor) att;
            CoordinateReferenceSystem crs = gd.getCoordinateReferenceSystem();
            if (crs != null && !DefaultEngineeringCRS.CARTESIAN_2D.equals(crs)) {
                boolean codeOnly = true;
                final boolean longitudeFirst = CRS.getAxisOrder(crs, false) == AxisOrder.EAST_NORTH;
                final String crsCode = CRS.toSRS(crs, codeOnly);
                if (crsCode != null) {
                    srsName = (longitudeFirst ? "EPSG:" : "urn:ogc:def:crs:EPSG::") + crsCode;
                    // check that what we are writing is actually a valid EPSG code and we will
                    // be able to decode it later. If not, we will use WKT instead
                    try {
                        CRS.decode(srsName, longitudeFirst);
                    } catch (NoSuchAuthorityCodeException e) {
                        srsName = null;
                    } catch (FactoryException e) {
                        srsName = null;
                    }
                }
                if (srsName == null) {
                    if (crs instanceof Formattable) {
                        wkt = ((Formattable) crs).toWKT(Formattable.SINGLE_LINE);
                    } else {
                        wkt = crs.toWKT();
                    }
                }
            }
        }
        int authCodeOffset = srsName == null ? 0 : builder.createString(srsName);
        int wktOffset = wkt == null ? 0 : builder.createString(wkt);
        return SimpleAttributeDescriptor.createSimpleAttributeDescriptor(builder, nameOffset,
                bindingCode, geometric, authCodeOffset, wktOffset);
    }

}
