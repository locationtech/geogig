/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.fail;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;

import org.geotools.data.DataUtilities;
import org.geotools.feature.NameImpl;
import org.geotools.feature.SchemaException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.Name;
import org.opengis.feature.type.PropertyDescriptor;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Lists;
import com.google.common.hash.Funnel;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

@SuppressWarnings("deprecation")
public class HashObjectFunnelsTest {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    final ObjectId oid1 = ObjectId.valueOf("abc123000000000000001234567890abcdef0000");

    final ObjectId oid2 = ObjectId.valueOf("abc123000000000000001234567890abcdef0001");

    final ObjectId oid3 = ObjectId.valueOf("abc123000000000000001234567890abcdef0002");

    final ObjectId oid4 = ObjectId.valueOf("abc123000000000000001234567890abcdef0003");

    @Test
    public void testCommitFunnel() {
        ImmutableList<ObjectId> parents = ImmutableList.of(oid3, oid4);
        RevCommit testCommit = new RevCommit() {
            @Override
            public TYPE getType() {
                return RevObject.TYPE.COMMIT;
            }

            @Override
            public ObjectId getId() {
                return oid1;
            }

            @Override
            public ObjectId getTreeId() {
                return oid2;
            }

            @Override
            public ImmutableList<ObjectId> getParentIds() {
                return parents;
            }

            @Override
            public Optional<ObjectId> parentN(int parentIndex) {
                if (parentIndex >= parents.size()) {
                    return Optional.absent();
                }
                return Optional.fromNullable(parents.get(parentIndex));
            }

            @Override
            public RevPerson getAuthor() {
                return new RevPerson() {

                    @Override
                    public Optional<String> getName() {
                        return Optional.of("Test Author");
                    }

                    @Override
                    public Optional<String> getEmail() {
                        return Optional.of("test@author.com");
                    }

                    @Override
                    public long getTimestamp() {
                        return 142L;
                    }

                    @Override
                    public int getTimeZoneOffset() {
                        return 4;
                    }
                };
            }

            @Override
            public RevPerson getCommitter() {
                return new RevPerson() {

                    @Override
                    public Optional<String> getName() {
                        return Optional.of("Test Committer");
                    }

                    @Override
                    public Optional<String> getEmail() {
                        return Optional.of("test@committer.com");
                    }

                    @Override
                    public long getTimestamp() {
                        return 143L;
                    }

                    @Override
                    public int getTimeZoneOffset() {
                        return 5;
                    }
                };
            }

            @Override
            public String getMessage() {
                return "Commit Message";
            }

        };

        Funnel<RevCommit> commitFunnel = HashObjectFunnels.commitFunnel();
        Hasher hasher = Hashing.sha1().newHasher();
        commitFunnel.funnel(testCommit, hasher);

        final byte[] rawKey = hasher.hash().asBytes();
        assertEquals(ObjectId.NUM_BYTES, rawKey.length);
    }

    @Test
    public void testTreeFunnel() {
        SortedMap<Integer, Bucket> buckets = new TreeMap<Integer, Bucket>();
        List<Node> trees = new LinkedList<Node>();
        List<Node> features = new LinkedList<Node>();

        final Node testNode = RevObjectFactory.defaultInstance().createNode("Points",
                ObjectId.valueOf("abc123000000000000001234567890abcdef0001"), ObjectId.NULL,
                TYPE.TREE, null, null);
        final Bucket testBucket = RevObjectFactory.defaultInstance().createBucket(
                ObjectId.valueOf("abc123000000000000001234567890abcdef0002"), 0,
                new Envelope(0, 0, 1, 1));

        RevTree testTree = new RevTree() {

            @Override
            public TYPE getType() {
                return RevObject.TYPE.TREE;
            }

            @Override
            public ObjectId getId() {
                return oid1;
            }

            @Override
            public long size() {
                return 0;
            }

            @Override
            public int numTrees() {
                return trees.size();
            }

            @Override
            public ImmutableList<Node> trees() {
                return ImmutableList.copyOf(trees);
            }

            @Override
            public ImmutableList<Node> features() {
                return ImmutableList.copyOf(features);
            }

            @Override
            public ImmutableSortedMap<Integer, Bucket> buckets() {
                return ImmutableSortedMap.copyOf(buckets);
            }

            @Override
            public Iterable<Bucket> getBuckets() {
                return buckets.values();
            }
        };

        Funnel<RevTree> treeFunnel = HashObjectFunnels.treeFunnel();
        Hasher hasher = Hashing.sha1().newHasher();
        treeFunnel.funnel(testTree, hasher);

        byte[] rawKey = hasher.hash().asBytes();
        assertEquals(ObjectId.NUM_BYTES, rawKey.length);

        ObjectId id1 = ObjectId.create(rawKey);

        trees.add(testNode);
        features.add(testNode);
        buckets.put(0, testBucket);

        hasher = Hashing.sha1().newHasher();
        treeFunnel.funnel(testTree, hasher);

        rawKey = hasher.hash().asBytes();
        assertEquals(ObjectId.NUM_BYTES, rawKey.length);

        ObjectId id2 = ObjectId.create(rawKey);

        assertNotSame(id1, id2);

        ObjectId treeHash = HashObjectFunnels.hashTree(trees, features, buckets);

        assertEquals(treeHash, id2);

        ObjectId treeHash2 = HashObjectFunnels.hashTree(null, null, (Iterable<Bucket>) null);

        assertEquals(treeHash2, id1);

    }

    @Test
    public void testFeatureFunnel() throws ParseException {
        List<Optional<Object>> values = new LinkedList<Optional<Object>>();

        RevFeature testFeature = new RevFeature() {
            @Override
            public TYPE getType() {
                return RevObject.TYPE.FEATURE;
            }

            @Override
            public ObjectId getId() {
                return oid1;
            }

            @Override
            public ImmutableList<Optional<Object>> getValues() {
                return ImmutableList.copyOf(values);
            }

            @Override
            public int size() {
                return values.size();
            }

            @Override
            public Optional<Object> get(int index) {
                return values.get(index);
            }

            @Override
            public void forEach(Consumer<Object> consumer) {
                for (int i = 0; i < values.size(); i++) {
                    consumer.accept(values.get(i).orNull());
                }
            }

            @Override
            public Optional<Geometry> get(int index, GeometryFactory gf) {
                throw new UnsupportedOperationException();
            }
        };

        Funnel<RevFeature> treeFunnel = HashObjectFunnels.featureFunnel();
        Hasher hasher = Hashing.sha1().newHasher();
        treeFunnel.funnel(testFeature, hasher);

        byte[] rawKey = hasher.hash().asBytes();
        assertEquals(ObjectId.NUM_BYTES, rawKey.length);
        ObjectId emptyFeatureId1 = ObjectId.create(rawKey);

        hasher = Hashing.sha1().newHasher();
        HashObjectFunnels.feature(hasher, ImmutableList.of());
        rawKey = hasher.hash().asBytes();
        assertEquals(ObjectId.NUM_BYTES, rawKey.length);
        ObjectId emptyFeatureId2 = ObjectId.create(rawKey);

        assertEquals(emptyFeatureId1, emptyFeatureId2);

        WKTReader reader = new WKTReader();
        // Make sure properties of each type are hashed
        values.add(Optional.absent());
        values.add(Optional.of(new Boolean(false)));
        values.add(Optional.of((byte) 0x0));
        values.add(Optional.of((short) 0));
        values.add(Optional.of(0));
        values.add(Optional.of(0L));
        values.add(Optional.of(0.f));
        values.add(Optional.of(new Double(0)));
        values.add(Optional.of(""));
        values.add(Optional.of(new boolean[] { true, false }));
        values.add(Optional.of(new byte[] { 0x01, 0x02 }));
        values.add(Optional.of(new short[] { 0, 1 }));
        values.add(Optional.of(new int[] { 0, 1 }));
        values.add(Optional.of(new long[] { 0, 1 }));
        values.add(Optional.of(new float[] { 0.f, 1.f }));
        values.add(Optional.of(new double[] { 0.0, 1.0 }));
        values.add(Optional.of(new String[] { "test", "strings" }));
        values.add(Optional.of(reader.read("POINT(0 0)")));
        values.add(Optional.of(reader.read("LINESTRING(0 0, 1 1)")));
        values.add(Optional.of(reader.read("POLYGON((0 0, 1 1, 2 2, 0 0))")));
        values.add(Optional.of(reader.read("MULTIPOINT((0 0),(1 1))")));
        values.add(Optional.of(reader.read("MULTILINESTRING ((0 0, 1 1),(2 2, 3 3))")));
        values.add(Optional
                .of(reader.read("MULTIPOLYGON(((0 0, 1 1, 2 2, 0 0)),((3 3, 4 4, 5 5, 3 3)))")));
        values.add(Optional.of(reader.read("GEOMETRYCOLLECTION(POINT(4 6),LINESTRING(4 6,7 10))")));
        values.add(Optional.of(UUID.randomUUID()));
        values.add(Optional.of(new BigInteger("0")));
        values.add(Optional.of(new BigDecimal("0.0")));
        values.add(Optional.of(new java.sql.Date(0L)));
        values.add(Optional.of(new java.util.Date()));
        values.add(Optional.of(new java.sql.Time(0L)));
        values.add(Optional.of(new java.sql.Timestamp(0L)));
        values.add(Optional.of('a'));
        values.add(Optional.of(new char[] { 'a', 'b' }));
        values.add(Optional.of(new Envelope()));
        Map<String, String> testMap = new HashMap<String, String>();
        testMap.put("key", "value");
        values.add(Optional.of(testMap));
        SortedMap<String, String> testSortedMap = new TreeMap<String, String>();
        testSortedMap.put("key", "value");
        values.add(Optional.of(testSortedMap));

        hasher = Hashing.sha1().newHasher();
        treeFunnel.funnel(testFeature, hasher);

        rawKey = hasher.hash().asBytes();
        assertEquals(ObjectId.NUM_BYTES, rawKey.length);
        ObjectId featureId1 = ObjectId.create(rawKey);

        hasher = Hashing.sha1().newHasher();
        HashObjectFunnels.feature(hasher, Lists.transform(values, (value) -> value.orNull()));
        rawKey = hasher.hash().asBytes();
        assertEquals(ObjectId.NUM_BYTES, rawKey.length);
        ObjectId featureId2 = ObjectId.create(rawKey);

        assertEquals(featureId1, featureId2);

        values.clear();

        // Try hashing a feature with invalid value type
        values.add(Optional.of(new Object()));
        try {
            hasher = Hashing.sha1().newHasher();
            treeFunnel.funnel(testFeature, hasher);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }

        // If this fails it means a new type was added and this test needs to be updated with the
        // new type.
        exception.expect(ArrayIndexOutOfBoundsException.class);
        FieldType.valueOf(0x25);

    }

    @Test
    public void testTagFunnel() {
        RevTag testTag = new RevTag() {

            @Override
            public TYPE getType() {
                return RevObject.TYPE.TAG;
            }

            @Override
            public ObjectId getId() {
                return oid1;
            }

            @Override
            public String getName() {
                return "TagName";
            }

            @Override
            public String getMessage() {
                return "Tag Message";
            }

            @Override
            public RevPerson getTagger() {
                return new RevPerson() {

                    @Override
                    public Optional<String> getName() {
                        return Optional.of("Test Tagger");
                    }

                    @Override
                    public Optional<String> getEmail() {
                        return Optional.of("test@tagger.com");
                    }

                    @Override
                    public long getTimestamp() {
                        return 142L;
                    }

                    @Override
                    public int getTimeZoneOffset() {
                        return 4;
                    }
                };
            }

            @Override
            public ObjectId getCommitId() {
                return oid2;
            }

        };

        Funnel<RevTag> tagFunnel = HashObjectFunnels.tagFunnel();
        Hasher hasher = Hashing.sha1().newHasher();
        tagFunnel.funnel(testTag, hasher);

        byte[] rawKey = hasher.hash().asBytes();
        assertEquals(ObjectId.NUM_BYTES, rawKey.length);
    }

    @Test
    public void testFeatureTypeFunnel() throws SchemaException {
        FeatureType featureType = DataUtilities.createType("http://geogig.points", "Points",
                "sp:String,ip:Integer,pp:Point:0");
        RevFeatureType testFeatureType = new RevFeatureType() {
            @Override
            public TYPE getType() {
                return RevObject.TYPE.FEATURETYPE;
            }

            @Override
            public ObjectId getId() {
                return oid1;
            }

            @Override
            public FeatureType type() {
                return featureType;
            }

            @Override
            public ImmutableList<PropertyDescriptor> descriptors() {
                return ImmutableList.copyOf(featureType.getDescriptors());
            }

            @Override
            public Name getName() {
                return new NameImpl("http://geogig.points", "Points");
            }
        };

        Funnel<RevFeatureType> featureTypeFunnel = HashObjectFunnels.featureTypeFunnel();
        Hasher hasher = Hashing.sha1().newHasher();
        featureTypeFunnel.funnel(testFeatureType, hasher);

        byte[] rawKey = hasher.hash().asBytes();
        assertEquals(ObjectId.NUM_BYTES, rawKey.length);
    }

}
