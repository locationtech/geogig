/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.geotools.plumbing;

import static org.geotools.data.DataUtilities.attributeNames;
import static org.locationtech.geogig.repository.impl.SpatialOps.findIdentifier;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.data.DataStore;
import org.geotools.data.DataUtilities;
import org.geotools.data.FeatureSource;
import org.geotools.data.Query;
import org.geotools.data.store.FeatureIteratorIterator;
import org.geotools.factory.Hints;
import org.geotools.feature.DecoratingFeature;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.NameImpl;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.feature.type.AttributeDescriptorImpl;
import org.geotools.filter.identity.FeatureIdImpl;
import org.geotools.jdbc.JDBCFeatureSource;
import org.locationtech.geogig.data.FeatureBuilder;
import org.locationtech.geogig.data.ForwardingFeatureCollection;
import org.locationtech.geogig.data.ForwardingFeatureIterator;
import org.locationtech.geogig.data.ForwardingFeatureSource;
import org.locationtech.geogig.geotools.plumbing.GeoToolsOpException.StatusCode;
import org.locationtech.geogig.hooks.Hookable;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureBuilder;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.LsTreeOp;
import org.locationtech.geogig.plumbing.LsTreeOp.Strategy;
import org.locationtech.geogig.plumbing.ResolveFeatureType;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.porcelain.CRSException;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.FeatureInfo;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.jts.geom.CoordinateSequenceFactory;
import org.locationtech.jts.geom.impl.PackedCoordinateSequenceFactory;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.filter.Filter;
import org.opengis.filter.identity.FeatureId;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

import lombok.NonNull;

/**
 * Internal operation for importing tables from a GeoTools {@link DataStore}.
 * 
 * @see DataStore
 */
@Hookable(name = "import")
public class ImportOp extends AbstractGeoGigOp<RevTree> {

    private static final Logger LOG = LoggerFactory.getLogger(ImportOp.class);

    private boolean all = false;

    private String table = null;

    /**
     * The path to import the data into
     */
    private String destPath;

    /**
     * The name to use for the geometry descriptor, replacing the default one
     */
    private String geomName;

    /**
     * The name of the attribute to use for defining feature id's
     */
    private String fidAttribute;

    private DataStore dataStore;

    /**
     * Whether to remove previous objects in the destination path, in case they exist
     * 
     */
    private boolean overwrite = true;

    /**
     * If set to true, only create the schema on the destination path, do not actually insert
     * features.
     */
    private boolean createSchemaOnly = false;

    /**
     * If true, it does not overwrite, and modifies the existing features to have the same feature
     * type as the imported table
     */
    private boolean alter;

    /**
     * If false, features will be added as they are, with their original feature type. If true, the
     * import operation will try to adapt them to the current default feature type, and if that is
     * not possible it will throw an exception
     */
    private boolean adaptToDefaultFeatureType = true;

    private boolean usePaging = true;

    private ForwardingFeatureIteratorProvider forwardingFeatureIteratorProvider = null;

    private Filter filter = Filter.INCLUDE;

    /**
     * Executes the import operation using the parameters that have been specified. Features will be
     * added to the working tree, and a new working tree will be constructed. Either {@code all} or
     * {@code table}, but not both, must be set prior to the import process.
     * 
     * @return RevTree the new working tree
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    protected RevTree _call() {

        // check preconditions and get the actual list of type names to import
        final String[] typeNames = checkPreconditions();

        for (int i = 0; i < typeNames.length; i++) {
            try {
                typeNames[i] = URLDecoder.decode(typeNames[i], Charsets.UTF_8.displayName());
            } catch (UnsupportedEncodingException e) {
                // shouldn't reach here.
            }
        }

        ProgressListener progressListener = getProgressListener();
        progressListener.started();

        // use a local variable not to alter the command's state
        boolean overwrite = this.overwrite;
        if (alter) {
            overwrite = false;
        }

        final WorkingTree workTree = workingTree();

        RevFeatureType destPathFeatureType = null;
        final boolean destPathProvided = destPath != null;
        if (destPathProvided) {
            destPathFeatureType = this.command(ResolveFeatureType.class).setRefSpec(destPath).call()
                    .orNull();
            // we delete the previous tree to honor the overwrite setting, but then turn it
            // to false. Otherwise, each table imported will overwrite the previous ones and
            // only the last one will be imported.
            if (overwrite) {
                try {
                    workTree.delete(destPath);
                } catch (Exception e) {
                    throw new GeoToolsOpException(e, StatusCode.UNABLE_TO_INSERT);
                }
                overwrite = false;
            }
        }

        int tableCount = 0;

        for (String typeName : typeNames) {
            {
                tableCount++;
                String tableName = String.format("%-16s", typeName);
                if (typeName.length() > 16) {
                    tableName = tableName.substring(0, 13) + "...";
                }
                progressListener.setDescription("Importing " + tableName + " (" + tableCount + "/"
                        + typeNames.length + ")... ");
            }

            FeatureSource featureSource = getFeatureSource(typeName);
            SimpleFeatureType featureType = (SimpleFeatureType) featureSource.getSchema();

            if (!Filter.INCLUDE.equals(filter)) {
                Set<String> filterAtts = Sets.newHashSet(attributeNames(filter, featureType));
                SetView<String> missing = Sets.difference(filterAtts,
                        featureType.getAttributeDescriptors().stream()
                                .map(att -> att.getLocalName()).collect(Collectors.toSet()));
                if (!missing.isEmpty()) {
                    throw new IllegalArgumentException(String.format(
                            "The following attributes required by the CQL filter do not exist in %s: %s",
                            typeName, missing));
                }
            }

            final String fidPrefix = featureType.getTypeName() + ".";

            String path;
            if (destPath == null) {
                path = featureType.getTypeName();
            } else {
                NodeRef.checkValidPath(destPath);
                path = destPath;
                featureType = forceFeatureTypeName(featureType, path);
            }

            featureType = overrideGeometryName(featureType);
            featureType = tryForceKnownCRS(featureType);

            featureSource = new ForceTypeAndFidFeatureSource<FeatureType, Feature>(featureSource,
                    featureType, fidPrefix);

            boolean hasPrimaryKey = hasPrimaryKey(typeName);
            boolean forbidSorting = !usePaging || !hasPrimaryKey;
            ((ForceTypeAndFidFeatureSource) featureSource).setForbidSorting(forbidSorting);

            if (destPathFeatureType != null && adaptToDefaultFeatureType && !alter) {
                featureSource = new FeatureTypeAdapterFeatureSource<FeatureType, Feature>(
                        featureSource, destPathFeatureType.type());
            }

            ProgressListener taskProgress = subProgress(100.f / typeNames.length);
            if (overwrite) {
                try {
                    workTree.delete(path);
                    workTree.createTypeTree(path, featureType);
                } catch (Exception e) {
                    throw new GeoToolsOpException(e, StatusCode.UNABLE_TO_INSERT);
                }
            }

            if (alter) {
                // first we modify the feature type and the existing features, if needed
                workTree.updateTypeTree(path, featureType);
                Iterator<Feature> transformedIterator = transformFeatures(featureType, path);
                RevFeatureType type = RevFeatureType.builder().type(featureType).build();
                objectDatabase().put(type);
                insert(workTree, path, transformedIterator, taskProgress, type.getId());
            }
            if (!createSchemaOnly) {
                insert(workTree, path, featureSource, taskProgress);
            }
        }

        progressListener.setProgress(100.f);
        progressListener.complete();
        return workTree.getTree();
    }

    private boolean hasPrimaryKey(String typeName) {
        FeatureSource featureSource;
        try {
            featureSource = dataStore.getFeatureSource(typeName);
        } catch (Exception e) {
            throw new GeoToolsOpException(StatusCode.UNABLE_TO_GET_FEATURES);
        }
        if (featureSource instanceof JDBCFeatureSource) {
            return ((JDBCFeatureSource) featureSource).getPrimaryKey().getColumns().size() != 0;
        }
        return false;
    }

    private SimpleFeatureType tryForceKnownCRS(SimpleFeatureType orig) {
        GeometryDescriptor geometryDescriptor = orig.getGeometryDescriptor();
        if (geometryDescriptor == null) {
            return orig;
        }
        CoordinateReferenceSystem crs = geometryDescriptor.getCoordinateReferenceSystem();
        if (crs == null) {
            return orig;
        }
        try {
            crs = findIdentifier(geometryDescriptor);
            return DataUtilities.createSubType(orig, null, crs);
        } catch (CRSException e) {
            LOG.warn(e.getMessage());
            return orig;
        } catch (FactoryException | SchemaException e) {
            LOG.warn("Error looking for known identifier for CRS " + crs, e);
            return orig;
        }
    }

    private SimpleFeatureType overrideGeometryName(SimpleFeatureType featureType) {

        if (geomName == null) {
            return featureType;
        }

        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        List<AttributeDescriptor> newAttributes = Lists.newArrayList();

        String oldGeomName = featureType.getGeometryDescriptor().getName().getLocalPart();
        Collection<AttributeDescriptor> descriptors = featureType.getAttributeDescriptors();

        for (AttributeDescriptor descriptor : descriptors) {
            String name = descriptor.getName().getLocalPart();
            Preconditions.checkArgument(!name.equals(geomName),
                    "The provided geom name is already in use by another attribute");
            if (name.equals(oldGeomName)) {
                AttributeDescriptorImpl newDescriptor = new AttributeDescriptorImpl(
                        descriptor.getType(), new NameImpl(geomName), descriptor.getMinOccurs(),
                        descriptor.getMaxOccurs(), descriptor.isNillable(),
                        descriptor.getDefaultValue());
                newAttributes.add(newDescriptor);
            } else {
                newAttributes.add(descriptor);
            }
        }

        builder.setAttributes(newAttributes);
        builder.setName(featureType.getName());
        builder.setCRS(featureType.getCoordinateReferenceSystem());
        featureType = builder.buildFeatureType();
        return featureType;

    }

    private SimpleFeatureType forceFeatureTypeName(SimpleFeatureType featureType, String path) {
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setAttributes(featureType.getAttributeDescriptors());
        builder.setName(new NameImpl(featureType.getName().getNamespaceURI(), path));
        builder.setCRS(featureType.getCoordinateReferenceSystem());

        featureType = builder.buildFeatureType();
        return featureType;
    }

    private Iterator<Feature> transformFeatures(SimpleFeatureType featureType, String path) {
        String refspec = Ref.WORK_HEAD + ":" + path;
        Iterator<NodeRef> oldFeatures = command(LsTreeOp.class).setReference(refspec)
                .setStrategy(Strategy.FEATURES_ONLY).call();

        RevFeatureType revFeatureType = RevFeatureType.builder().type(featureType).build();
        Iterator<Feature> transformedIterator = transformIterator(oldFeatures, revFeatureType);
        return transformedIterator;
    }

    private Integer collectionSize(@SuppressWarnings("rawtypes") FeatureSource featureSource) {
        final Integer collectionSize;
        {
            int fastCount;
            try {
                fastCount = featureSource.getCount(Query.ALL);
            } catch (IOException e) {
                throw new GeoToolsOpException(e, StatusCode.UNABLE_TO_GET_FEATURES);
            }
            collectionSize = -1 == fastCount ? null : Integer.valueOf(fastCount);
        }
        return collectionSize;
    }

    private String[] checkPreconditions() {
        if (dataStore == null) {
            throw new GeoToolsOpException(StatusCode.DATASTORE_NOT_DEFINED);
        }

        if ((table == null || table.isEmpty()) && !(all)) {
            throw new GeoToolsOpException(StatusCode.TABLE_NOT_DEFINED);
        }

        if (table != null && !table.isEmpty() && all) {
            throw new GeoToolsOpException(StatusCode.ALL_AND_TABLE_DEFINED);
        }
        String[] typeNames;
        if (all) {
            try {
                typeNames = dataStore.getTypeNames();
            } catch (Exception e) {
                throw new GeoToolsOpException(StatusCode.UNABLE_TO_GET_NAMES);
            }
            if (typeNames.length == 0) {
                throw new GeoToolsOpException(StatusCode.NO_FEATURES_FOUND);
            }
        } else {
            SimpleFeatureType schema;
            try {
                schema = dataStore.getSchema(table);
            } catch (IOException e) {
                throw new GeoToolsOpException(StatusCode.TABLE_NOT_FOUND);
            }
            Preconditions.checkNotNull(schema);
            typeNames = new String[] { table };
        }

        if (typeNames.length > 1 && alter && all) {
            throw new GeoToolsOpException(StatusCode.ALTER_AND_ALL_DEFINED);
        }
        // sort the schemas in reverse alphabetic order so that any FIDs that exist in multiple
        // schemas will look like the Feature in the first schema alphabetically, since it will be
        // the last one processed in _call() above.
        Arrays.sort(typeNames, Collections.reverseOrder());
        return typeNames;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private FeatureSource getFeatureSource(String typeName) {

        FeatureSource featureSource;
        try {
            featureSource = dataStore.getFeatureSource(typeName);
        } catch (Exception e) {
            throw new GeoToolsOpException(StatusCode.UNABLE_TO_GET_FEATURES);
        }

        return new ForwardingFeatureSource(featureSource) {

            @Override
            public FeatureCollection getFeatures(Query query) throws IOException {

                final FeatureCollection features = super.getFeatures(query);
                return new ForwardingFeatureCollection(features) {

                    @Override
                    public FeatureIterator features() {

                        final FeatureType featureType = getSchema();
                        final String fidPrefix = featureType.getName().getLocalPart() + ".";

                        FeatureIterator iterator = delegate.features();

                        FeatureIterator fidAndFtReplaced = new FidAndFtReplacerIterator(iterator,
                                fidAttribute, fidPrefix, (SimpleFeatureType) featureType);

                        FeatureIterator finalIterator;

                        if (forwardingFeatureIteratorProvider != null) {
                            finalIterator = forwardingFeatureIteratorProvider.forwardIterator(
                                    fidAndFtReplaced, (SimpleFeatureType) featureType);
                        } else {
                            finalIterator = fidAndFtReplaced;
                        }

                        return finalIterator;
                    }
                };
            }
        };
    }

    /**
     * Replaces the default geotools fid with the string representation of the value of an
     * attribute.
     * 
     * If the specified attribute is null, does not exist or the value is null, an fid is created by
     * taking the default fid and removing the specified fidPrefix prefix from it.
     * 
     * It also replaces the feature type. This is used to avoid identical feature types (in terms of
     * attributes) coming from different data sources (such as to shapefiles with different names)
     * being considered different for having a different name. It is used in this importer class to
     * decorate the name of the feature type when importing into a given tree, using the name of the
     * tree.
     * 
     * The passed feature type should have the same attribute descriptions as the one to replace,
     * but no checking is performed to ensure that
     * 
     */
    private static class FidAndFtReplacerIterator extends ForwardingFeatureIterator<SimpleFeature> {

        private final String fidPrefix;

        private String attributeName;

        private SimpleFeatureType featureType;

        @SuppressWarnings("unchecked")
        public FidAndFtReplacerIterator(@SuppressWarnings("rawtypes") FeatureIterator iterator,
                final String attributeName, String fidPrefix, SimpleFeatureType featureType) {
            super(iterator);
            this.attributeName = attributeName;
            this.fidPrefix = fidPrefix;
            this.featureType = featureType;

        }

        @Override
        public SimpleFeature next() {
            SimpleFeature next = super.next();
            if (attributeName == null) {
                String fid = next.getID();
                if (fid.startsWith(fidPrefix)) {
                    fid = fid.substring(fidPrefix.length());
                }
                return new FidAndFtOverrideFeature(next, fid, featureType);
            } else {
                Object value = next.getAttribute(attributeName);
                Preconditions.checkNotNull(value);
                return new FidAndFtOverrideFeature(next, value.toString(), featureType);
            }
        }
    }

    private void insert(final WorkingTree workTree, final String treePath,
            @SuppressWarnings("rawtypes") final FeatureSource featureSource,
            final ProgressListener taskProgress) {

        final Query query = new Query();
        query.setFilter(filter);
        CoordinateSequenceFactory coordSeq = new PackedCoordinateSequenceFactory();
        query.getHints().add(new Hints(Hints.JTS_COORDINATE_SEQUENCE_FACTORY, coordSeq));

        try (FeatureIterator fit = featureSource.getFeatures(query).features()) {
            FeatureType schema = featureSource.getSchema();
            RevFeatureType featureType = RevFeatureType.builder().type(schema).build();
            objectDatabase().put(featureType);
            ObjectId featureTypeId = featureType.getId();

            if (fit.hasNext()) {
                Iterator<Feature> features = new FeatureIteratorIterator<>(fit);
                insert(workTree, treePath, features, taskProgress, featureTypeId);
            }
        } catch (IOException e) {
            LOG.warn("Unable to insert into " + treePath, e);
            throw new GeoToolsOpException(e, StatusCode.UNABLE_TO_INSERT);
        }
    }

    private void insert(final WorkingTree workTree, final String treePath,
            Iterator<Feature> features, final ProgressListener taskProgress,
            ObjectId featureTypeId) {
        try {

            // RevFeature rf = RevFeature.builder().build(f);
            // String path = NodeRef.appendChild(treePath, f.getIdentifier().getID());
            // return FeatureInfo.insert(rf, featureTypeId, path);
            Function<Feature, FeatureInfo> fn = new Function<Feature, FeatureInfo>() {
                @Override
                public FeatureInfo apply(Feature f) {
                    RevFeature rf = RevFeature.builder().build(f);
                    String path = NodeRef.appendChild(treePath, f.getIdentifier().getID());
                    return FeatureInfo.insert(rf, featureTypeId, path);
                }
            };

            Iterator<FeatureInfo> infos = Iterators.transform(features, fn);
            workTree.insert(infos, taskProgress);
        } catch (Exception e) {
            LOG.warn("Unable to insert into " + treePath, e);
            throw new GeoToolsOpException(e, StatusCode.UNABLE_TO_INSERT);
        }
    }

    private Iterator<Feature> transformIterator(Iterator<NodeRef> nodeIterator,
            final RevFeatureType newFeatureType) {

        // (node) -> alter(node, newFeatureType)
        Function<NodeRef, Feature> fn = new Function<NodeRef, Feature>() {
            @Override
            public Feature apply(NodeRef node) {
                return alter(node, newFeatureType);
            }
        };

        Iterator<Feature> iterator = Iterators.transform(nodeIterator, fn);

        return iterator;

    }

    /**
     * Translates a feature pointed by a node from its original feature type to a given one, using
     * values from those attributes that exist in both original and destination feature type. New
     * attributes are populated with null values
     * 
     * @param node The node that points to the feature. No checking is performed to ensure the node
     *        points to a feature instead of other type
     * @param featureType the destination feature type
     * @return a feature with the passed feature type and data taken from the input feature
     */
    private Feature alter(NodeRef node, RevFeatureType featureType) {
        RevFeature oldFeature = command(RevObjectParse.class).setObjectId(node.getObjectId())
                .call(RevFeature.class).get();
        RevFeatureType oldFeatureType;
        oldFeatureType = command(RevObjectParse.class).setObjectId(node.getMetadataId())
                .call(RevFeatureType.class).get();
        ImmutableList<PropertyDescriptor> oldAttributes = oldFeatureType.descriptors();
        ImmutableList<PropertyDescriptor> newAttributes = featureType.descriptors();
        RevFeatureBuilder builder = RevFeature.builder();
        for (int i = 0; i < newAttributes.size(); i++) {
            int idx = oldAttributes.indexOf(newAttributes.get(i));
            if (idx != -1) {
                Optional<Object> oldValue = oldFeature.get(idx);
                builder.addValue(oldValue.orNull());
            } else {
                builder.addValue(null);
            }
        }
        RevFeature newFeature = builder.build();
        FeatureBuilder featureBuilder = new FeatureBuilder(featureType);
        Feature feature = featureBuilder.build(node.name(), newFeature);
        return feature;
    }

    /**
     * @param all if this is set, all tables from the data store will be imported
     * @return {@code this}
     */
    public ImportOp setAll(boolean all) {
        this.all = all;
        return this;
    }

    /**
     * @param table if this is set, only the specified table will be imported from the data store
     * @return {@code this}
     */
    public ImportOp setTable(String table) {
        this.table = table;
        return this;
    }

    /**
     * @param overwrite If this is true, the features being imported will fully overrite the
     *        destination feature tree (i.e. the feature tree will be truncated and finally contain
     *        only the features being imported). If {@code false}, the features being imported are
     *        added to the existing feature tree.
     * @return {@code this}
     */
    public ImportOp setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
        return this;
    }

    public ImportOp setCreateSchemaOnly(boolean createSchema) {
        this.createSchemaOnly = createSchema;
        return this;
    }

    /**
     * 
     * @param the attribute to use to create the feature id, if the default.
     */
    public ImportOp setFidAttribute(String attribute) {
        this.fidAttribute = attribute;
        return this;
    }

    /**
     * @param force if true, it will change the default feature type of the tree we are importing
     *        into and change all features under that tree to have that same feature type
     * @return {@code this}
     */
    public ImportOp setAlter(boolean alter) {
        this.alter = alter;
        return this;
    }

    /**
     * 
     * @param destPath the path to import to to. If not provided, it will be taken from the feature
     *        type of the table to import.
     * @return {@code this}
     */
    public ImportOp setDestinationPath(@Nullable String destPath) {
        Preconditions.checkArgument(destPath == null || !destPath.isEmpty());
        this.destPath = destPath;
        return this;
    }

    /**
     * @param dataStore the data store to use for the import process
     * @return {@code this}
     */
    public ImportOp setDataStore(DataStore dataStore) {
        this.dataStore = dataStore;
        return this;
    }

    /**
     * @param provider the forwarding feature iterator provider to use to transform incoming
     *        features during the import
     * @return {@code this}
     */
    public ImportOp setForwardingFeatureIteratorProvider(
            ForwardingFeatureIteratorProvider provider) {
        this.forwardingFeatureIteratorProvider = provider;
        return this;
    }

    private static final class FidAndFtOverrideFeature extends DecoratingFeature {

        private String fid;

        private SimpleFeatureType featureType;

        public FidAndFtOverrideFeature(SimpleFeature delegate, String fid,
                SimpleFeatureType featureType) {
            super(delegate);
            this.fid = fid;
            this.featureType = featureType;
        }

        @Override
        public SimpleFeatureType getType() {
            return featureType;
        }

        @Override
        public String getID() {
            return fid;
        }

        @Override
        public FeatureId getIdentifier() {
            return new FeatureIdImpl(fid);
        }
    }

    /**
     * Sets the name to use for the geometry descriptor. If not provided, the geometry name from the
     * source schema will be used.
     * 
     * @param geomName
     */
    public ImportOp setGeometryNameOverride(String geomName) {
        this.geomName = geomName;
        return this;

    }

    public ImportOp setUsePaging(boolean usePaging) {
        this.usePaging = usePaging;
        return this;
    }

    /**
     * Sets whether features will be added as they are, with their original feature type, or adapted
     * to the preexisting feature type of the destination tree. If true, the import operation will
     * try to adapt them to the current default feature type, and if that is not possible it will
     * throw an exception. Setting this parameter to true prevents the destination tree to have
     * mixed feature types. If importing onto a tree that doesn't exist, this has no effect at all,
     * since there is not previous feature type for that tree with which the features to import can
     * be compared
     * 
     * @param forceFeatureType
     * @return {@code this}
     */
    public ImportOp setAdaptToDefaultFeatureType(boolean adaptToDefaultFeatureType) {
        this.adaptToDefaultFeatureType = adaptToDefaultFeatureType;
        return this;
    }

    public ImportOp setFilter(@NonNull Filter filter) {
        this.filter = filter;
        return this;
    }
}
