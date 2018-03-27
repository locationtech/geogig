/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.datastream.v2_3;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.SortedMap;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.HashObject;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.io.ByteStreams;

/**
 * Defines the serialized form of a {@link RevTree}
 * <p>
 * <h2>Sections</h2>
 * 
 * <pre>
 * {@code
 *  tree =      <HEADER>,
 *              [NODESET], (* tree nodes*)
 *              [NODESET], (* feature nodes *)
 *              [BUCKETS], (* bucket tree pointers *)
 *              [EXTRA_DATA],
 *              <STRING_TABLE> ;
 *  
 *  HEADER =    <ushort>, (* size of header *)
 *              <uvarlong>, (*total size*)
 *              <uvarint>, (* recursive number of tree nodes *)
 *              <uvarint>, (*offset of contained tree nodes nodeset*)
 *              <uvarint>, (*offset of contained feature nodes nodeset*)
 *              <uvarint>, (*offset of contained bucketset*)
 *              <uvarint>; (*byte offset of string table, zero being the first byte of the header*)
 *  
 *  objectid =  <byte[20]>;
 *  
 *  NODESET =   <ushort>, (* number of nodes *)
 *              <byte>, (* flag bits: 0 = metadataId is present, 
 *                                    1 = bounds is present, 
 *                                    2 = bounds is point,  
 *                                    3 = extra data is present, 
 *                                    4-7 unused *)
 *              {ushort}, (* indexes of node names in string table *)
 *              {objectid}, (* node ids *)
 *              TBD: [coordinate sequence], (* bounds of nodes, present if at least one node has bounds *)
 *              TBD: [{extra data}]
 *              
 *  STRING_TABLE =      <unsigned varint>, (* size *)
 *                      {utf8 string}; (* sequence of utf8 strings *)
 * }
 * </pre>
 * <p>
 * {@link Node}
 */
@Beta
class RevTreeFormat {

    static long size(DataBuffer data) {
        return data.header().size();
    }

    static int numChildTrees(DataBuffer data) {
        return data.header().numTrees();
    }

    static ImmutableList<Node> trees(DataBuffer data) {
        final int offset = data.tail().getOffsetOfTreesNodeset();
        if (0 == offset) {
            return NodeSet.EMPTY_TREES.build();
        }
        NodeSet nodeSet = NodeSet.decode(data, offset, TYPE.TREE);
        ImmutableList<Node> nodes = nodeSet.build();
        return nodes;
    }

    static ImmutableList<Node> features(DataBuffer data) {
        final int offsetOfFeatures = data.tail().getOffsetOfFeaturesNodeset();
        if (0 == offsetOfFeatures) {
            return NodeSet.EMPTY_FEATURES.build();
        }
        NodeSet nodeSet = NodeSet.decode(data, offsetOfFeatures, TYPE.FEATURE);
        ImmutableList<Node> nodes = nodeSet.build();
        return nodes;
    }

    static ImmutableSortedMap<Integer, Bucket> buckets(DataBuffer data) {
        final int offset = data.tail().getOffsetOfBuckets();
        if (0 == offset) {
            return BucketSet.EMPTY.build();
        }
        BucketSet bucketSet = BucketSet.decode(data, offset);
        ImmutableSortedMap<Integer, Bucket> buckets = bucketSet.build();
        return buckets;
    }

    public static void encode(RevTree tree, DataOutput out) throws IOException {
        if (tree instanceof org.locationtech.geogig.storage.datastream.v2_3.RevTreeImpl) {
            org.locationtech.geogig.storage.datastream.v2_3.RevTreeImpl t = (RevTreeImpl) tree;
            DataBuffer buff = t.data;
            buff.writeTo(out);
        } else {
            byte[] encoded = encode(tree);
            out.write(encoded);
        }
    }

    private static byte[] encode(RevTree tree) {
        try (ByteArrayOutputStream buff = new ByteArrayOutputStream()) {
            encode(tree, buff);
            return buff.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void encode(final RevTree tree, ByteArrayOutputStream buff) throws IOException {
        DataOutput out = ByteStreams.newDataOutput(buff);
        final int offsetOfTreesNodeset;
        final int offsetOfFeaturesNodeset;
        final int offsetOfBuckets;
        final int offsetOfStringTable;
        final int offsetOfTail;

        StringTable stringTable = StringTable.unique();
        Header.encode(out, tree);

        offsetOfTreesNodeset = tree.treesSize() == 0 ? 0 : buff.size();
        if (tree.treesSize() > 0) {
            NodeSet.encode(out, tree.trees(), stringTable);
        }

        offsetOfFeaturesNodeset = tree.featuresSize() == 0 ? 0 : buff.size();
        if (tree.featuresSize() > 0) {
            NodeSet.encode(out, tree.features(), stringTable);
        }

        offsetOfBuckets = tree.bucketsSize() == 0 ? 0 : buff.size();
        BucketSet.encode(out, tree, stringTable);

        offsetOfStringTable = buff.size();
        stringTable.encode(out);
        offsetOfTail = buff.size();
        Tail.encode(out, //
                offsetOfTreesNodeset, //
                offsetOfFeaturesNodeset, //
                offsetOfBuckets, //
                offsetOfStringTable, //
                offsetOfTail);
    }

    public static RevTree decode(@Nullable ObjectId id, byte[] data) {
        return decode(id, data, 0, data.length);
    }

    public static RevTree decode(@Nullable ObjectId id, byte[] data, int offset, int length) {

        ByteBuffer buffer = ByteBuffer.wrap(data, offset, length);
        if (offset != 0 || length != 0) {
            buffer = buffer.slice();
        }
        return decode(id, buffer);
    }

    public static RevTree decode(@Nullable ObjectId id, ByteBuffer data) {
        final DataBuffer dataBuffer = DataBuffer.of(data);
        final long totalSize = dataBuffer.header().size();
        final int numTrees = dataBuffer.header().numTrees();
        if (totalSize == 0L && numTrees == 0) {
            return RevTree.EMPTY;
        }
        if (null == id) {
            List<Node> trees = RevTreeFormat.trees(dataBuffer);
            List<Node> features = RevTreeFormat.features(dataBuffer);
            SortedMap<Integer, Bucket> buckets = RevTreeFormat.buckets(dataBuffer);
            id = HashObject.hashTree(trees, features, buckets);
        }
        return new RevTreeImpl(id, dataBuffer);
    }

    static DataInput asDataInput(ByteBuffer data) {
        return asDataInput(data, data.position(), data.limit());
    }

    static DataInput asDataInput(ByteBuffer data, final int offset, final int limit) {
        return new ByteBufferDataInput(data, offset, limit);
    }

}
