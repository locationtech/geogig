package org.locationtech.geogig.plumbing.index;

import java.util.ArrayList;
import java.util.List;

import org.geotools.data.DataUtilities;
import org.geotools.feature.SchemaException;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.CanonicalTreeBuilder;
import org.locationtech.geogig.model.impl.RevFeatureBuilder;
import org.locationtech.geogig.repository.NodeRef;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.locationtech.geogig.storage.ObjectStore;
import org.opengis.feature.simple.SimpleFeatureType;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryFactory;

public class QuadTreeTestSupport {

    private static GeometryFactory gf = new GeometryFactory();

    public static Node createWorldPointsLayer(Repository repository) {
        String typeSpec = "geom:Point:srid=4326,x:Double,y:Double,xystr:String";
        SimpleFeatureType type;
        try {
            type = DataUtilities.createType("worldpoints", typeSpec);
        } catch (SchemaException e) {
            throw Throwables.propagate(e);
        }
        RevTree tree = createWorldPointsTree(repository);
        WorkingTree workingTree = repository.workingTree();
        NodeRef typeTreeRef = workingTree.createTypeTree(type.getTypeName(), type);

        ObjectStore store = repository.objectDatabase();
        CanonicalTreeBuilder newRootBuilder = CanonicalTreeBuilder.create(store,
                workingTree.getTree());

        Node newTypeTreeRef = typeTreeRef.getNode().update(tree.getId(), SpatialOps.boundsOf(tree));
        newRootBuilder.put(newTypeTreeRef);
        RevTree newWorkTree = newRootBuilder.build();
        workingTree.updateWorkHead(newWorkTree.getId());
        return newTypeTreeRef;
    }

    public static RevTree createWorldPointsTree(Repository repository) {

        ObjectStore store = repository.objectDatabase();
        CanonicalTreeBuilder builder = CanonicalTreeBuilder.create(store);
        for (int x = -180; x <= 180; x += 5) {
            for (int y = -90; y <= 90; y += 5) {

                String fid = String.format("%03d,%03d", x, y);

                RevFeature feature = createPointFeature(x, y, Double.valueOf(x), Double.valueOf(y),
                        fid);
                Envelope env = SpatialOps.boundsOf(feature);

                ObjectId oid = feature.getId();
                Node node = Node.create(fid, oid, ObjectId.NULL, TYPE.FEATURE, env);
                store.put(feature);
                builder.put(node);
            }
        }
        RevTree tree = builder.build();
        return tree;
    }

    public static List<RevFeature> createWorldPointFeatures() {
        List<RevFeature> features = new ArrayList<>();
        for (int x = -180; x <= 180; x++) {
            for (int y = -90; y <= 90; y++) {
                RevFeature feature = createPointFeature(x, y);
                features.add(feature);
            }
        }
        return features;
    }

    static RevFeature createPointFeature(double x, double y, Object... extraAttribues) {
        RevFeatureBuilder builder = RevFeatureBuilder.builder();
        builder.addValue(gf.createPoint(new Coordinate(x, y)));
        if (extraAttribues != null) {
            builder.addAll(Lists.newArrayList(extraAttribues));
        }
        RevFeature feature = builder.build();
        return feature;
    }
}
