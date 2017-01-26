package org.locationtech.geogig.geotools.data.reader;

import static com.google.common.base.Preconditions.checkNotNull;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.data.FeatureReader;
import org.geotools.data.Query;
import org.geotools.renderer.ScreenMap;
import org.locationtech.geogig.geotools.data.GeoGigDataStore.ChangeType;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.plumbing.ResolveFeatureType;
import org.locationtech.geogig.repository.Context;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.sort.SortBy;

import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.impl.PackedCoordinateSequenceFactory;

public class FeatureReaderBuilder {

    private static final GeometryFactory DEFAULT_GEOMETRY_FACTORY = new GeometryFactory(
            new PackedCoordinateSequenceFactory());

    private final Context repo;

    private final String typeName;

    private String headRef = Ref.HEAD;

    private String[] propertyNames = Query.ALL_NAMES;

    private boolean ignoreIndex;

    private Filter filter;

    private ScreenMap screenMap;

    private GeometryFactory geometryFactory = DEFAULT_GEOMETRY_FACTORY;

    private SortBy[] sortBy;

    private Integer limit;

    private Integer offset;

    private String oldHeadRef = ObjectId.NULL.toString();

    private ChangeType changeType = ChangeType.ADDED;

    public FeatureReaderBuilder(Context repo, String typeName) {
        this.repo = repo;
        this.typeName = typeName;
    }

    public static FeatureReaderBuilder builder(Context repo, String typeName) {
        return new FeatureReaderBuilder(repo, typeName);
    }

    public FeatureReaderBuilder oldHeadRef(String oldHeadRef) {
        checkNotNull(oldHeadRef);
        this.oldHeadRef = oldHeadRef;
        return this;
    }

    public FeatureReaderBuilder changeType(ChangeType changeType) {
        checkNotNull(changeType);
        this.changeType = changeType;
        return this;
    }

    public FeatureReaderBuilder headRef(String headRef) {
        checkNotNull(headRef);
        this.headRef = headRef;
        return this;
    }

    public FeatureReaderBuilder propertyNames(@Nullable String... propertyNames) {
        this.propertyNames = propertyNames;
        return this;
    }

    public FeatureReaderBuilder ignoreIndex() {
        this.ignoreIndex = true;
        return this;
    }

    public FeatureReaderBuilder filter(Filter filter) {
        checkNotNull(filter);
        this.filter = filter;
        return this;
    }

    public FeatureReaderBuilder screenMap(@Nullable ScreenMap screenMap) {
        this.screenMap = screenMap;
        return this;
    }

    public FeatureReaderBuilder geometryFactory(@Nullable GeometryFactory geometryFactory) {
        this.geometryFactory = geometryFactory == null ? DEFAULT_GEOMETRY_FACTORY : geometryFactory;
        return this;
    }

    public FeatureReaderBuilder sortBy(@Nullable SortBy... sortBy) {
        this.sortBy = sortBy;
        return this;
    }

    public FeatureReaderBuilder offset(@Nullable Integer offset) {
        this.offset = offset;
        return this;
    }

    public FeatureReaderBuilder limit(@Nullable Integer limit) {
        this.limit = limit;
        return this;
    }

    public FeatureReader<SimpleFeatureType, SimpleFeature> build() {

        RevFeatureType type = repo.command(ResolveFeatureType.class).setRefSpec(typeName).call()
                .get();
        FeatureType fullSchema = type.type();

        Context context = repo;
        SimpleFeatureType schema = (SimpleFeatureType) fullSchema;
        Filter origFilter = filter;
        String typeTreePath = typeName;
        String oldHead = oldHeadRef;
        ChangeType changeType = this.changeType;
        Integer maxFeatures = limit;
        boolean ignoreAttributes = false;
        GeometryFactory geomFac = geometryFactory;
        return new GeogigFeatureReader<SimpleFeatureType, SimpleFeature>(context, schema,
                origFilter, typeTreePath, headRef, oldHead, changeType, offset, maxFeatures,
                screenMap, ignoreAttributes, geomFac);
    }

}
