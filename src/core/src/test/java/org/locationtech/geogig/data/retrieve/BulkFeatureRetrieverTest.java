/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Blasby (Boundless) - initial implementation
 */
package org.locationtech.geogig.data.retrieve;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;
import org.locationtech.geogig.feature.Feature;
import org.locationtech.geogig.feature.FeatureType;
import org.locationtech.geogig.feature.FeatureTypes;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectInfo;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.io.WKTReader;

import com.google.common.collect.Streams;

public class BulkFeatureRetrieverTest {

    @Test
    public void testGet() throws Exception {
        ObjectId meta1 = getOID(1);

        FeatureType fType1 = FeatureTypes.createType("location", "the_geom:Point:srid=4326",
                "name:String", "name2:String");
        RevFeatureType revft1 = RevFeatureType.builder().id(meta1).type(fType1).build();

        WKTReader wkt = new WKTReader();
        RevFeature f1 = RevObjectTestSupport.featureForceId(getOID(2), wkt.read("POINT(0 0)"),
                "abc", "def");
        RevFeature f2 = RevObjectTestSupport.featureForceId(getOID(3), wkt.read("POINT(0 0)"),
                "rrr", "ddd");

        ObjectDatabase odb = mock(ObjectDatabase.class);
        when(odb.getFeatureType(meta1)).thenReturn(revft1);

        Iterator<RevObject> iterator = (Arrays.asList((RevObject) f1, (RevObject) f2)).iterator();
        when(odb.getAll(any(), any(), any())).thenReturn(iterator);

        Node n1 = RevObjectFactory.defaultInstance().createNode("name1", getOID(2), meta1,
                TYPE.FEATURE, new Envelope(), null);
        NodeRef nr1 = new NodeRef(n1, "testcase", meta1);

        Node n2 = RevObjectFactory.defaultInstance().createNode("name1", getOID(3), meta1,
                TYPE.FEATURE, new Envelope(), null);
        NodeRef nr2 = new NodeRef(n2, "testcase", meta1);

        List<ObjectInfo<RevObject>> objs = List.of(ObjectInfo.of(nr1, f1), ObjectInfo.of(nr2, f2));

        AutoCloseableIterator<ObjectInfo<RevObject>> objects = AutoCloseableIterator
                .fromIterator(objs.iterator());

        when(odb.getObjects(any(), any(), any())).thenReturn(objects);

        Iterator<NodeRef> input = Arrays.asList(nr1, nr2).iterator();

        BulkFeatureRetriever getter = new BulkFeatureRetriever(odb);

        Iterator<Feature> results = getter.getGeoToolsFeatures(input);

        List<Feature> feats = Streams.stream(results).collect(Collectors.toList());

        assertEquals(2, feats.size());

        Feature feat1 = "abc".equals(feats.get(0).getAttribute("name")) ? feats.get(0)
                : feats.get(1);
        Feature feat2 = "rrr".equals(feats.get(0).getAttribute("name")) ? feats.get(0)
                : feats.get(1);

        assertEquals("abc", feat1.getAttribute("name"));
        assertEquals("rrr", feat2.getAttribute("name"));
    }

    public ObjectId getOID(int b) {
        byte n = (byte) b;
        return new ObjectId(
                new byte[] { n, n, n, n, n, n, n, n, n, n, n, n, n, n, n, n, n, n, n, n });
    }

}
