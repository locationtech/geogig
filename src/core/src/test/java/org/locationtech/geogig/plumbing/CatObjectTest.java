/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import org.junit.Test;
import org.locationtech.geogig.model.CanonicalNodeNameOrder;
import org.locationtech.geogig.model.FieldType;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

import com.google.common.base.Suppliers;

public class CatObjectTest extends RepositoryTestCase {

    private ObjectDatabase odb;

    private static final ObjectId FAKE_ID = RevObjectTestSupport.hashString("fake");

    private static final String FEATURE_PREFIX = "Feature.";

    @Override
    protected void setUpInternal() throws Exception {
        odb = repo.objectDatabase();
    }

    @Test
    public void TestCatTreeWithoutBucketsObject() throws Exception {
        int numChildren = CanonicalNodeNameOrder.normalizedSizeLimit(0) / 2;
        RevTree tree = createTree(numChildren);
        CharSequence desc = geogig.command(CatObject.class).setObject(Suppliers.ofInstance(tree))
                .call();
        String[] lines = desc.toString().split("\n");
        assertEquals(numChildren + 4, lines.length);
        for (int i = 4; i < lines.length; i++) {
            String[] tokens = lines[i].split("\t");
            assertEquals(FAKE_ID.toString(), tokens[3].trim());
        }

    }

    @Test
    public void TestCatTreeWithBucketsObject() throws Exception {
        int numChildren = CanonicalNodeNameOrder.normalizedSizeLimit(0) * 2;
        RevTree tree = createTree(numChildren);
        CharSequence desc = geogig.command(CatObject.class).setObject(Suppliers.ofInstance(tree))
                .call();
        String[] lines = desc.toString().split("\n");
        assertEquals(tree.bucketsSize() + 4, lines.length);
        for (int i = 4; i < lines.length; i++) {
            String[] tokens = lines[i].split("\t");
            assertEquals(tokens[0].trim(), "BUCKET");
        }
    }

    private RevTree createTree(int numChildren) {
        RevTreeBuilder rtb = RevTreeBuilder.builder(odb);
        for (int i = 0; i < numChildren; i++) {
            String key = FEATURE_PREFIX + i;
            Node ref = RevObjectFactory.defaultInstance().createNode(key, FAKE_ID, FAKE_ID,
                    TYPE.FEATURE, null, null);
            rtb.put(ref);
        }
        return rtb.build();
    }

    @Test
    public void TestCatFeatureObject() {
        RevFeature feature = RevFeature.builder().build(points1);
        CharSequence desc = geogig.command(CatObject.class).setObject(Suppliers.ofInstance(feature))
                .call();
        String[] lines = desc.toString().split("\n");

        assertEquals(points1.getProperties().size() + 2, lines.length);
        assertEquals(FieldType.STRING.name() + "\tStringProp1_1", lines[2]);
        assertEquals(FieldType.INTEGER.name() + "\t1000", lines[3]);
        assertEquals(FieldType.POINT.name() + "\tPOINT (1 1)", lines[4]);
    }

}
