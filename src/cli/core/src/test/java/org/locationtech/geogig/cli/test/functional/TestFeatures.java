/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.test.functional;

import org.locationtech.geogig.feature.Feature;
import org.locationtech.geogig.feature.FeatureType;
import org.locationtech.geogig.feature.FeatureTypes;
import org.locationtech.geogig.feature.Name;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

public final class TestFeatures {

    public static final String idL1 = "Lines.1";

    public static final String idL2 = "Lines.2";

    public static final String idL3 = "Lines.3";

    public static final String idP1 = "Points.1";

    public static final String idP2 = "Points.2";

    public static final String idP3 = "Points.3";

    public static final String pointsNs = "http://geogig.points";

    public static final String pointsName = "Points";

    public static final String pointsTypeSpec = "sp:String,ip:Integer,pp:Point:srid=4326";

    public static final String modifiedPointsTypeSpec = "sp:String,ip:Integer,pp:Point:srid=4326,extra:String";

    public static final Name pointsTypeName = Name.valueOf("http://geogig.points", pointsName);

    public static FeatureType pointsType;

    public static FeatureType modifiedPointsType;

    public static Feature points1;

    public static Feature points1_modified;

    public static Feature points2;

    public static Feature points3;

    public static Feature points1_FTmodified;

    protected static final String linesNs = "http://geogig.lines";

    protected static final String linesName = "Lines";

    protected static final String linesTypeSpec = "sp:String,ip:Integer,pp:LineString:srid=4326";

    protected static final Name linesTypeName = Name.valueOf("http://geogig.lines", linesName);

    public static FeatureType linesType;

    public static Feature lines1;

    public static Feature lines2;

    public static Feature lines3;

    private static boolean created;

    public synchronized static void setupFeatures() throws Exception {
        if (created) {
            return;
        }
        pointsType = FeatureTypes.createType(pointsTypeName, pointsTypeSpec.split(","));
        modifiedPointsType = FeatureTypes.createType(pointsTypeName,
                modifiedPointsTypeSpec.split(","));

        points1 = feature(pointsType, idP1, "StringProp1_1", new Integer(1000), "POINT(1 1)");
        points1_modified = feature(pointsType, idP1, "StringProp1_1a", new Integer(1001),
                "POINT(1 2)");
        points1_FTmodified = feature(modifiedPointsType, idP1, "StringProp1_1", new Integer(1000),
                "POINT(1 1)", "ExtraString");
        points2 = feature(pointsType, idP2, "StringProp1_2", new Integer(2000), "POINT(2 2)");
        points3 = feature(pointsType, idP3, "StringProp1_3", new Integer(3000), "POINT(3 3)");

        linesType = FeatureTypes.createType(linesTypeName, linesTypeSpec.split(","));

        lines1 = feature(linesType, idL1, "StringProp2_1", new Integer(1000),
                "LINESTRING (1 1, 2 2)");
        lines2 = feature(linesType, idL2, "StringProp2_2", new Integer(2000),
                "LINESTRING (3 3, 4 4)");
        lines3 = feature(linesType, idL3, "StringProp2_3", new Integer(3000),
                "LINESTRING (5 5, 6 6)");
        created = true;
    }

    public static Feature feature(FeatureType type, String id, Object... values)
            throws ParseException {
        Feature feature = Feature.build(id, type);
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            if (type.getDescriptor(i).isGeometryDescriptor()) {
                if (value instanceof String) {
                    value = new WKTReader().read((String) value);
                }
            }
            feature.setAttribute(i, value);
        }
        return feature;
    }

}
