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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.junit.Test;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.repository.NodeRef;
import org.locationtech.geogig.storage.ObjectDatabase;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.util.Assert;

import static org.mockito.Mockito.*;

public class BulkGeoGigFeatureRetrieverTest {

    /**
     * this is a simple test that puts 2 features in the objectDatabase then retrieves them. It
     * checks the correlation is correct.
     */
    @Test
    public void testBulkGet() {
        ObjectDatabase odb = mock(ObjectDatabase.class);

        RevFeature f1 = mock(RevFeature.class);
        when(f1.getId()).thenReturn(getOID(1));

        RevFeature f2 = mock(RevFeature.class);
        when(f2.getId()).thenReturn(getOID(2));

        when(odb.getAll(anyObject(), anyObject(), anyObject()))
                .thenReturn((Arrays.asList((RevObject) f1, (RevObject) f2)).iterator());

        BulkGeoGigFeatureRetriever bulk = new BulkGeoGigFeatureRetriever(odb);

        ObjectId metadataid = getOID(4);

        Node n1 = Node.create("name1", getOID(1), metadataid, TYPE.FEATURE, new Envelope());
        NodeRef nr1 = new NodeRef(n1, "testcase", metadataid);
        Node n2 = Node.create("name2", getOID(2), metadataid, TYPE.FEATURE, new Envelope());
        NodeRef nr2 = new NodeRef(n2, "testcase", metadataid);

        Iterator<FeatureInfo> it = bulk.apply(Arrays.asList(nr1, nr2));
        List<FeatureInfo> feats = Arrays.asList(it.next(), it.next());

        Assert.isTrue(feats.get(0).getMetaDataID() == metadataid);
        Assert.isTrue(feats.get(0).getName() == "name1");
        Assert.equals(feats.get(0).getFeature().getId(), getOID(1));

        Assert.isTrue(feats.get(1).getMetaDataID() == metadataid);
        Assert.isTrue(feats.get(1).getName() == "name2");
        Assert.equals(feats.get(1).getFeature().getId(), getOID(2));

        int ttt = 00;
    }

    public ObjectId getOID(int b) {
        byte n = (byte) b;
        return new ObjectId(
                new byte[] { n, n, n, n, n, n, n, n, n, n, n, n, n, n, n, n, n, n, n, n });
    }
}
