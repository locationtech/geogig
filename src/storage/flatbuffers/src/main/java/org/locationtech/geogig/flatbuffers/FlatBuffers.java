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
import org.locationtech.geogig.flatbuffers.generated.v1.Commit;
import org.locationtech.geogig.flatbuffers.generated.v1.Feature;
import org.locationtech.geogig.flatbuffers.generated.v1.LeafTree;
import org.locationtech.geogig.flatbuffers.generated.v1.NodeTree;
import org.locationtech.geogig.flatbuffers.generated.v1.ObjectType;
import org.locationtech.geogig.flatbuffers.generated.v1.Person;
import org.locationtech.geogig.flatbuffers.generated.v1.QualifiedName;
import org.locationtech.geogig.flatbuffers.generated.v1.RevisionObject;
import org.locationtech.geogig.flatbuffers.generated.v1.SHA;
import org.locationtech.geogig.flatbuffers.generated.v1.SimpleAttributeDescriptor;
import org.locationtech.geogig.flatbuffers.generated.v1.SimpleFeatureType;
import org.locationtech.geogig.flatbuffers.generated.v1.Tag;
import org.locationtech.geogig.flatbuffers.generated.v1.values.Bounds;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.FieldType;
import org.locationtech.geogig.model.HashObjectFunnels;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.model.RevPerson;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.ValueArray;
import org.locationtech.jts.geom.Envelope;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.AttributeType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.GeometryType;
import org.opengis.feature.type.Name;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
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

    public ValueArray decodeValueArray(ByteBuffer buffer) {
        Feature feature = Feature.getRootAsFeature(buffer);
        return new FBValueArray(feature);
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

    public int writeLeafTree(@NonNull FlatBufferBuilder builder, final long size,
            final @NonNull List<Node> trees, final @NonNull List<Node> features) {

        final int numNodes = trees.size() + features.size();
        final int[] offsets = new int[numNodes];

        forEachNode(trees, features, offsets, n -> builder.createString(n.getName()));
        final int nodeNamesOffset = LeafTree.createNodesNamesVector(builder, offsets);

        LeafTree.startNodesIdsVector(builder, numNodes);
        forEachNodeReverseOrder(trees, features, n -> write(n.getObjectId(), builder));
        final int nodeIdsOffset = builder.endVector();
        final int nodesMetadataIdsOffset;
        final int nodeBoundsOffset;
        final int nodesExtraDataOffset;
        final Envelope buff = new Envelope();
        final boolean[] hasBoundsOrExtraData = hasBoundsOrExtraData(trees, features, buff);
        final boolean hasBounds = hasBoundsOrExtraData[0];
        final boolean hasExtraDataMap = hasBoundsOrExtraData[1];
        final boolean hasMetadataIds = hasBoundsOrExtraData[2];

        if (hasMetadataIds) {
            LeafTree.startNodesMetadataIdsVector(builder, numNodes);
            forEachNodeReverseOrder(trees, features,
                    n -> write(n.getMetadataId().or(ObjectId.NULL), builder));
            nodesMetadataIdsOffset = builder.endVector();
        } else {
            nodesMetadataIdsOffset = 0;
        }

        if (hasBounds) {
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
        if (hasExtraDataMap) {
            forEachNode(trees, features, offsets,
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
        LeafTree.addNodesMetadataIds(builder, nodesMetadataIdsOffset);
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

    /**
     * @return a 3-elements boolean array where the first one indicates if at least one node has
     *         bounds, the seconds if it has extradata map, and the third if it has a metadataId
     */
    private boolean[] hasBoundsOrExtraData(List<Node> trees, List<Node> features, Envelope buff) {
        boolean[] flags = hasBoundsOrExtraData(trees, buff);
        if (!flags[0] || !flags[1] || flags[2]) {
            boolean[] featuresFlags = hasBoundsOrExtraData(features, buff);
            flags[0] = flags[0] || featuresFlags[0];
            flags[1] = flags[1] || featuresFlags[1];
            flags[2] = flags[2] || featuresFlags[2];
        }
        return flags;
    }

    /**
     * @return a 3-elements boolean array where the first one indicates if at least one node has
     *         bounds, the seconds if it has extradata map, and the third if it has a metadataId
     */
    private boolean[] hasBoundsOrExtraData(List<Node> nodes, Envelope buff) {
        boolean hasBounds = false;
        boolean hasExtraData = false;
        boolean hasMetadataId = false;
        final int size = nodes.size();
        for (int i = 0; i < size; i++) {
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
            if (node.getMetadataId().isPresent()) {
                hasMetadataId = true;
            }
            if (hasBounds && hasExtraData && hasMetadataId) {
                return new boolean[] { true, true, true };
            }
        }
        return new boolean[] { hasBounds, hasExtraData, hasMetadataId };
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
            Node node = treeNodes.get(i);
            checkNode(node, TYPE.TREE, i);
            offsets[j] = nodeToOffset.applyAsInt(node);
        }
        for (int i = 0; i < features; i++, j++) {
            Node node = featureNodes.get(i);
            checkNode(node, TYPE.FEATURE, i);
            offsets[j] = nodeToOffset.applyAsInt(node);
        }
    }

    private void checkNode(Node node, TYPE type, int index) {
        if (node == null) {
            throw new NullPointerException(
                    "null node in " + type.toString().toLowerCase() + "s at index " + index);
        }
        if (node.getType() != type) {
            throw new IllegalArgumentException(type.toString().toLowerCase() + "s contains "
                    + node.getType() + " node at index " + index);
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

    public int writeValueArray(FlatBufferBuilder builder, List<Object> values) {
        int foffset = writeFeature(builder, values);
        builder.finish(foffset);
        return foffset;
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
        for (Bucket bucket : buckets) {
            if (bucket == null) {
                throw new NullPointerException("There's a null bucket in the buckets set");
            }
            int off = writeBucket(builder, bucket, envBuff);
            offsets[i.getAndIncrement()] = off;
        }
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
        org.locationtech.geogig.flatbuffers.generated.v1.Bucket.startBucket(builder);
        org.locationtech.geogig.flatbuffers.generated.v1.Bucket.addIndex(builder, bucketIndex);
        org.locationtech.geogig.flatbuffers.generated.v1.Bucket.addTreeId(builder,
                write(treeId, builder));

        // Bounds is a struct, must be serialized inline
        int boundsOffset = bounds.isNull() ? 0 : write(bounds, builder);
        org.locationtech.geogig.flatbuffers.generated.v1.Bucket.addBounds(builder, boundsOffset);
        return org.locationtech.geogig.flatbuffers.generated.v1.Bucket.endBucket(builder);
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

        int nameOffset = writeQualifiedName(type.getName(), builder);
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

    private int writeAttributeDescriptor(AttributeDescriptor attDescriptor,
            FlatBufferBuilder builder) {

        final int nameOffset = writeQualifiedName(attDescriptor.getName(), builder);
        final int typeOffset = writeAttributeType(attDescriptor.getType(), builder);
        final int minOccurs = attDescriptor.getMinOccurs();
        final int maxOccurs = attDescriptor.getMaxOccurs();
        final boolean nillable = attDescriptor.isNillable();
        return SimpleAttributeDescriptor.createSimpleAttributeDescriptor(builder, nameOffset,
                typeOffset, minOccurs, maxOccurs, nillable);
    }

    private int writeAttributeType(AttributeType att, FlatBufferBuilder builder) {

        final int nameOffset = writeQualifiedName(att.getName(), builder);
        final byte bindingCode;
        {
            Class<?> binding = att.getBinding();
            FieldType fieldType = FieldType.forBinding(binding);
            Preconditions.checkArgument(fieldType != FieldType.UNKNOWN);
            bindingCode = fieldType.getTag();// just because they do match
        }
        final boolean identifiable = att.isIdentified();
        final boolean geometric = att instanceof GeometryType;
        final int crs_authority_codeOffset;
        final int crs_wktOffset;
        if (geometric) {
            String srsName = null;
            String wkt = null;
            GeometryType gd = (GeometryType) att;
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
            crs_authority_codeOffset = srsName == null ? 0 : builder.createString(srsName);
            crs_wktOffset = wkt == null ? 0 : builder.createString(wkt);
        } else {
            crs_authority_codeOffset = 0;
            crs_wktOffset = 0;
        }

        return org.locationtech.geogig.flatbuffers.generated.v1.AttributeType.createAttributeType(
                builder, nameOffset, bindingCode, identifiable, geometric, crs_authority_codeOffset,
                crs_wktOffset);
    }

    private int writeQualifiedName(Name name, FlatBufferBuilder builder) {
        String namespaceURI = name.getNamespaceURI();
        String localPart = name.getLocalPart();
        int namespaceUriOffset = Strings.isNullOrEmpty(namespaceURI) ? 0
                : builder.createString(namespaceURI);
        int localNameOffset = Strings.isNullOrEmpty(localPart) ? 0
                : builder.createString(localPart);
        return QualifiedName.createQualifiedName(builder, namespaceUriOffset, localNameOffset);
    }

}
