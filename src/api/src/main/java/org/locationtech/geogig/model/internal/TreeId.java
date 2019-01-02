/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.internal;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.primitives.UnsignedBytes;

/**
 * A tree identifier defined by the array of bucket indices it belongs to up to a given depth, as
 * opposed to the final SHA-1 identifier of a built {@code RevTree}.
 *
 */
public final class TreeId implements Comparable<TreeId>, Serializable {

    private static final long serialVersionUID = 1L;

    private static final Comparator<byte[]> COMPARATOR = UnsignedBytes.lexicographicalComparator();

    public final byte[] bucketIndicesByDepth;

    /**
     * @param bucketIndicesByDepth array of bucket indexes that define this tree id
     * @implNote: this instance takes ownership of the argument array
     */
    public TreeId(byte[] bucketIndicesByDepth) {
        this.bucketIndicesByDepth = bucketIndicesByDepth;
    }

    /**
     * @return the length of this tree id (i.e. how many levels of depth are represented)
     */
    public int depthLength() {
        return bucketIndicesByDepth.length;
    }

    public int depthIndex() {
        return depthLength() - 1;
    }

    /**
     * @param depthIndex zero based index of the depth at which to return the bucket index for
     * @return the bucket index this tree id belongs to at the specified depth index
     */
    public Integer bucketIndex(final int depthIndex) {
        Preconditions.checkArgument(depthIndex < bucketIndicesByDepth.length,
                "depth (%s) is outside range [0 - %s]", depthIndex,
                bucketIndicesByDepth.length - 1);
        return Integer.valueOf(bucketIndicesByDepth[depthIndex] & 0xFF);
    }

    @Override
    public boolean equals(Object o) {
        // don't bother checking for instanceof TreeId, this is private and will never be
        // compared to something else
        return (o instanceof TreeId)
                && Arrays.equals(bucketIndicesByDepth, ((TreeId) o).bucketIndicesByDepth);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(bucketIndicesByDepth);
    }

    @Override
    public int compareTo(TreeId o) {
        return COMPARATOR.compare(bucketIndicesByDepth, o.bucketIndicesByDepth);
    }

    @Override
    public String toString() {
        return Arrays.toString(bucketIndicesByDepth);
    }

    /**
     * Factory method to create a child tree id of this one at with the given bucket id
     */
    public TreeId newChild(int bucketId) {
        int depthLength = depthLength();
        byte[] childIndices = new byte[depthLength + 1];
        System.arraycopy(this.bucketIndicesByDepth, 0, childIndices, 0, depthLength);
        childIndices[depthLength] = (byte) bucketId;
        return new TreeId(childIndices);
    }

    public TreeId sibling(int bucketId) {
        Preconditions.checkState(bucketIndicesByDepth.length > 0, "root can't have siblings");
        byte[] bucketIndices = this.bucketIndicesByDepth.clone();
        bucketIndices[bucketIndices.length - 1] = (byte) bucketId;
        return new TreeId(bucketIndices);
    }

    public TreeId parent() {
        Preconditions.checkState(depthLength() > 0, "already at root tree id");
        int parentDepthLength = depthIndex();
        byte[] parentIndices = new byte[parentDepthLength];
        System.arraycopy(this.bucketIndicesByDepth, 0, parentIndices, 0, parentDepthLength);
        return new TreeId(parentIndices);
    }

    public int leafBucket() {
        return depthLength() == 0 ? -1 : bucketIndex(depthIndex()).intValue();
    }

    @VisibleForTesting
    static TreeId fromString(String stringRepresentation) {
        stringRepresentation = stringRepresentation.trim();
        Preconditions.checkArgument(
                stringRepresentation.startsWith("[") && stringRepresentation.endsWith("]"));
        stringRepresentation = stringRepresentation.substring(1);
        stringRepresentation = stringRepresentation.substring(0, stringRepresentation.length() - 1);

        List<String> stringBucketList = Splitter.on(',').trimResults().omitEmptyStrings()
                .splitToList(stringRepresentation);
        List<Integer> bucketList = stringBucketList.stream().map(Integer::parseInt)
                .collect(Collectors.toList());
        byte[] id = new byte[bucketList.size()];
        for (int i = 0; i < bucketList.size(); i++) {
            id[i] = bucketList.get(i).byteValue();
            Preconditions.checkArgument(id[i] >= 0);
        }
        return new TreeId(id);
    }

    public static TreeId valueOf(List<Integer> quadrantsByDepth) {
        byte[] quadpath = new byte[quadrantsByDepth.size()];
        for (int i = 0; i < quadrantsByDepth.size(); i++) {
            quadpath[i] = quadrantsByDepth.get(i).byteValue();
        }
        return new TreeId(quadpath);
    }

    public List<TreeId> deglose() {
        LinkedList<TreeId> path = new LinkedList<>();
        TreeId child = this;
        while (child.depthLength() > 0) {
            path.addFirst(child);
            child = child.parent();
        }
        return path;
    }

    public boolean contains(final int bucketIndex) {
        for (int i = 0; i < depthLength(); i++) {
            if (bucketIndex == bucketIndex(i)) {
                return true;
            }
        }
        return false;
    }

}