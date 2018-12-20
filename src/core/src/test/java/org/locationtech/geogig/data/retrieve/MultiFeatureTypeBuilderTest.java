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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.geotools.data.DataUtilities;
import org.geotools.geometry.jts.WKTReader2;
import org.junit.Test;
import org.locationtech.geogig.data.FeatureBuilder;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectInfo;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.util.Assert;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

public class MultiFeatureTypeBuilderTest {

    @Test
    public void testGet() throws Exception {
        ObjectId meta1 = getOID(1);
        ObjectId meta2 = getOID(2);

        SimpleFeatureType fType1 = DataUtilities.createType("location",
                "the_geom:Point:srid=4326,name:String,name2:String");
        RevFeatureType revft1 = RevFeatureType.builder().id(getOID(1)).type(fType1).build();

        SimpleFeatureType fType2 = DataUtilities.createType("location",
                "the_geom:Point:srid=4326,name3:String,name4:String");
        RevFeatureType revft2 = RevFeatureType.builder().id(getOID(1)).type(fType2).build();

        ObjectDatabase odb = mock(ObjectDatabase.class);
        when(odb.getFeatureType(meta1)).thenReturn(revft1);
        when(odb.getFeatureType(meta2)).thenReturn(revft2);

        MultiFeatureTypeBuilder builder = new MultiFeatureTypeBuilder(odb);
        FeatureBuilder fb1 = builder.get(meta1);
        FeatureBuilder fb2 = builder.get(meta2);

        Assert.isTrue(fb1 == builder.get(meta1)); // not rebuilt
        Assert.isTrue(fb2 == builder.get(meta2)); // not rebuilt

        Assert.isTrue(fb1.getType().equals(revft1));
        Assert.isTrue(fb2.getType().equals(revft2));
    }

    @Test
    public void testBuild() throws Exception {

        ObjectId meta1 = getOID(1);

        SimpleFeatureType fType1 = DataUtilities.createType("location",
                "the_geom:Point:srid=4326,name:String,name2:String");
        RevFeatureType revft1 = RevFeatureType.builder().id(getOID(1)).type(fType1).build();

        ObjectDatabase odb = mock(ObjectDatabase.class);
        when(odb.getFeatureType(meta1)).thenReturn(revft1);

        WKTReader2 wkt = new WKTReader2();
        RevFeature feat = RevObjectTestSupport.feature(wkt.read("POINT(0 0)"), "abc", "def");

        Node n1 = RevObjectFactory.defaultInstance().createNode("name1", getOID(2), meta1,
                TYPE.FEATURE, new Envelope(), null);
        NodeRef nr1 = new NodeRef(n1, "testcase", meta1);

        ObjectInfo<RevFeature> fi = ObjectInfo.of(nr1, feat);

        MultiFeatureTypeBuilder builder = new MultiFeatureTypeBuilder(odb);
        SimpleFeature sf = builder.apply(fi);

        Assert.isTrue(sf.getAttribute("name").equals("abc"));
        Assert.isTrue(sf.getAttribute("name2").equals("def"));
    }

    public ObjectId getOID(int b) {
        byte n = (byte) b;
        return new ObjectId(
                new byte[] { n, n, n, n, n, n, n, n, n, n, n, n, n, n, n, n, n, n, n, n });
    }
}
