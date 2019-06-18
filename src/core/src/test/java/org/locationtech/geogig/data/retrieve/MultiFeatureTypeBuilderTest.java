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

import org.junit.Test;
import org.locationtech.geogig.feature.Feature;
import org.locationtech.geogig.feature.FeatureType;
import org.locationtech.geogig.feature.FeatureTypes;
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
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.util.Assert;

public class MultiFeatureTypeBuilderTest {

    @Test
    public void testGet() throws Exception {
        ObjectId meta1 = getOID(1);
        ObjectId meta2 = getOID(2);

        FeatureType fType1 = FeatureTypes.createType("location", "the_geom:Point:srid=4326",
                "name:String", "name2:String");
        RevFeatureType revft1 = RevFeatureType.builder().id(getOID(1)).type(fType1).build();

        FeatureType fType2 = FeatureTypes.createType("location", "the_geom:Point:srid=4326",
                "name3:String", "name4:String");
        RevFeatureType revft2 = RevFeatureType.builder().id(getOID(1)).type(fType2).build();

        ObjectDatabase odb = mock(ObjectDatabase.class);
        when(odb.getFeatureType(meta1)).thenReturn(revft1);
        when(odb.getFeatureType(meta2)).thenReturn(revft2);

        MultiFeatureTypeBuilder builder = new MultiFeatureTypeBuilder(odb);
        FeatureType ft1 = builder.get(meta1);
        FeatureType ft2 = builder.get(meta2);

        Assert.isTrue(ft1 == builder.get(meta1)); // not rebuilt
        Assert.isTrue(ft2 == builder.get(meta2)); // not rebuilt

        Assert.isTrue(ft1.equals(revft1.type()));
        Assert.isTrue(ft2.equals(revft2.type()));
    }

    @Test
    public void testBuild() throws Exception {

        ObjectId meta1 = getOID(1);

        org.locationtech.geogig.feature.FeatureType fType1 = FeatureTypes.createType("location",
                "the_geom:Point:srid=4326", "name:String", "name2:String");
        RevFeatureType revft1 = RevFeatureType.builder().id(getOID(1)).type(fType1).build();

        ObjectDatabase odb = mock(ObjectDatabase.class);
        when(odb.getFeatureType(meta1)).thenReturn(revft1);

        WKTReader wkt = new WKTReader();
        RevFeature feat = RevObjectTestSupport.feature(wkt.read("POINT(0 0)"), "abc", "def");

        Node n1 = RevObjectFactory.defaultInstance().createNode("name1", getOID(2), meta1,
                TYPE.FEATURE, new Envelope(), null);
        NodeRef nr1 = new NodeRef(n1, "testcase", meta1);

        ObjectInfo<RevFeature> fi = ObjectInfo.of(nr1, feat);

        MultiFeatureTypeBuilder builder = new MultiFeatureTypeBuilder(odb);
        Feature sf = builder.apply(fi);

        Assert.isTrue(sf.getAttribute("name").equals("abc"));
        Assert.isTrue(sf.getAttribute("name2").equals("def"));
    }

    public ObjectId getOID(int b) {
        byte n = (byte) b;
        return new ObjectId(
                new byte[] { n, n, n, n, n, n, n, n, n, n, n, n, n, n, n, n, n, n, n, n });
    }
}
