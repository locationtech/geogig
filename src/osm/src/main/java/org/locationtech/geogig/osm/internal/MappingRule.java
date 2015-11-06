/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.osm.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.locationtech.geogig.storage.FieldType;
import org.locationtech.geogig.storage.text.TextValueSerializer;
import org.opengis.feature.Feature;
import org.opengis.feature.GeometryAttribute;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.gson.annotations.Expose;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

/**
 * A rule used to convert an OSM entity into a feature with a custom feature type. Attributes values
 * for the attributes in the feature type are taken from the tags and geometry of the feature to
 * convert
 * 
 */
public class MappingRule {

    public enum GeomRestriction {
        ONLY_OPEN_LINES("open"), ONLY_CLOSED_LINES("closed"), ALL_LINESTRINGS("all");

        private String s;

        GeomRestriction(String s) {
            this.s = s;
        }

        public String getModifierString() {
            return s;
        }

        public static GeomRestriction valueFromText(String s) {
            for (GeomRestriction gr : values()) {
                if (gr.getModifierString().equals(s)) {
                    return gr;
                }
            }
            throw new NoSuchElementException("Unknown modifier: " + s);
        }
    }

    public enum DefaultField {
        visible(Boolean.class), timestamp(Long.class), tags(Map.class), changeset(Long.class), version(
                Integer.class), user(String.class);

        private Class<?> clazz;

        private static ArrayList<Object> names = Lists.newArrayList();
        static {
            for (DefaultField v : DefaultField.values()) {
                names.add(v.name());
            }
        }

        DefaultField(Class<?> clazz) {
            this.clazz = clazz;
        }

        public Class<?> getFieldClass() {
            return clazz;
        }

        public static boolean isDefaultField(String s) {
            return names.contains(s);
        }

    }

    /**
     * The name of the rule
     */
    @Expose
    private String name;

    /**
     * A map of key, list_of_accepted_values, to be used to filter features. If a feature has any of
     * the keys in this map with any of the accepted values, it will be transformed by this rule
     */
    @Expose
    private Map<String, List<String>> filter;

    /**
     * A map of key, list_of_values_to_exclude, to be used to filter features. If a feature has any
     * of the keys in this map with any of the values in the list, it will not be transformed by
     * this rule, even if it meets the conditions set by the 'filter' object
     */
    @Expose
    @Nullable
    private Map<String, List<String>> exclude;

    /**
     * The fields to use for the custom feature type of the transformed feature
     */
    @Expose
    private Map<String, AttributeDefinition> fields;

    /**
     * The default fields to include in the destination feature type without transforming them
     */
    @Expose
    @Nullable
    private List<DefaultField> defaultFields;

    private SimpleFeatureType featureType;

    private SimpleFeatureBuilder featureBuilder;

    private Class<?> geometryType;

    private GeomRestriction geomRestriction;

    private ArrayList<String> _mandatoryTags = null;

    private static GeometryFactory gf = new GeometryFactory();

    public MappingRule(final String name, final Map<String, List<String>> filter,
            @Nullable final Map<String, List<String>> filterExclude,
            final Map<String, AttributeDefinition> fields,
            @Nullable final List<DefaultField> defaultFields) {

        Preconditions.checkNotNull(name);
        Preconditions.checkNotNull(filter);
        Preconditions.checkNotNull(fields);
        this.name = name;
        this.filter = filter;
        this.exclude = filterExclude;
        this.fields = fields;
        this.defaultFields = defaultFields;
        ArrayList<String> names = Lists.newArrayList();
        for (AttributeDefinition ad : fields.values()) {
            Preconditions.checkState(!names.contains(ad.getName()),
                    "Duplicated alias in mapping rule: " + ad.getName());
            names.add(ad.getName());
        }
    }

    /**
     * Returns the feature type defined by this rule. This is the feature type that features
     * transformed by this rule will have
     * 
     * @return
     */
    public SimpleFeatureType getFeatureType() {
        if (featureType == null) {
            SimpleFeatureTypeBuilder fb = new SimpleFeatureTypeBuilder();
            fb.setName(name);
            fb.add("id", Long.class);
            if (defaultFields != null) {
                for (DefaultField df : defaultFields) {
                    fb.add(df.name().toLowerCase(), df.getFieldClass());
                }
            }
            Set<String> keys = this.fields.keySet();
            for (String key : keys) {
                AttributeDefinition field = fields.get(key);
                Class<?> clazz = field.getType().getBinding();
                if (Geometry.class.isAssignableFrom(clazz)) {
                    Preconditions.checkArgument(geometryType == null,
                            "The mapping has more than one geometry attribute");
                    CoordinateReferenceSystem epsg4326;
                    try {
                        epsg4326 = CRS.decode("EPSG:4326", true);
                        fb.add(field.getName(), clazz, epsg4326);
                    } catch (NoSuchAuthorityCodeException e) {
                    } catch (FactoryException e) {
                    }
                    geometryType = clazz;
                } else {
                    fb.add(field.getName(), clazz);
                }
            }
            Preconditions.checkNotNull(geometryType,
                    "The mapping rule does not define a geometry field");
            if (!geometryType.equals(Point.class)) {
                fb.add("nodes", long[].class);
            }
            featureType = fb.buildFeatureType();

            featureBuilder = new SimpleFeatureBuilder(featureType);

        }
        return featureType;

    }

    private GeomRestriction getGeomRestriction() {
        if (geomRestriction == null) {
            if (filter.containsKey("geom")) {
                geomRestriction = GeomRestriction.valueFromText(filter.get("geom").get(0));
            } else {
                geomRestriction = GeomRestriction.ALL_LINESTRINGS;
            }
        }
        return geomRestriction;
    }

    /**
     * Returns the feature resulting from transforming a given feature using this rule
     * 
     * @param feature
     * @return
     */
    public Optional<Feature> apply(Feature feature) {
        @SuppressWarnings("unchecked")
        Map<String, String> tagsMap = (Map<String, String>) ((SimpleFeature) feature)
                .getAttribute("tags");
        Collection<Tag> tags = OSMUtils.buildTagsCollection(tagsMap);
        return apply(feature, tags);
    }

    /**
     * Returns the feature resulting from transforming a given feature using this rule. This method
     * takes a collection of tags, so there is no need to compute them from the 'tags' attribute.
     * This is meant as a faster alternative to the apply(Feature) method, in case the mapping
     * object calling this has already computed the tags, to avoid recomputing them
     * 
     * @param feature
     * @param tags
     * @return
     */
    public Optional<Feature> apply(Feature feature, Collection<Tag> tags) {
        if (!canBeApplied(feature, tags)) {
            return Optional.absent();
        }
        for (AttributeDescriptor attribute : getFeatureType().getAttributeDescriptors()) {
            String attrName = attribute.getName().toString();
            Class<?> clazz = attribute.getType().getBinding();
            if (Geometry.class.isAssignableFrom(clazz)) {
                Geometry geom = prepareGeometry((Geometry) feature.getDefaultGeometryProperty()
                        .getValue());
                if (geom == null) {
                    return Optional.absent();
                }
                featureBuilder.set(attrName, geom);
            } else {
                Object value = null;
                for (Tag tag : tags) {
                    if (fields.containsKey(tag.getKey())) {
                        if (fields.get(tag.getKey()).getName().equals(attrName)) {
                            FieldType type = FieldType.forBinding(clazz);
                            value = getAttributeValue(tag.getValue(), type);
                            break;
                        }
                    }
                }
                featureBuilder.set(attribute.getName(), value);
            }
        }

        String id = feature.getIdentifier().getID();
        featureBuilder.set("id", id);
        if (defaultFields != null) {
            for (DefaultField df : defaultFields) {
                featureBuilder.set(df.name(), feature.getProperty(df.name()).getValue());
            }
        }
        if (!featureType.getGeometryDescriptor().getType().getBinding().equals(Point.class)) {
            long[] nodeIds = (long[]) feature.getProperty("nodes").getValue();
            featureBuilder.set("nodes", nodeIds);
        }
        return Optional.of((Feature) featureBuilder.buildFeature(id));

    }

    private Geometry prepareGeometry(Geometry geom) {
        if (geometryType.equals(Polygon.class)) {
            Coordinate[] coords = geom.getCoordinates();
            if (!coords[0].equals(coords[coords.length - 1])) {
                Coordinate[] newCoords = new Coordinate[coords.length + 1];
                System.arraycopy(coords, 0, newCoords, 0, coords.length);
                newCoords[coords.length] = coords[0];
                coords = newCoords;
            }
            if (coords.length < 4) {
                return null;
            }
            return gf.createPolygon(coords);
        }

        return geom;
    }

    private Object getAttributeValue(String value, FieldType type) {
        return TextValueSerializer.fromString(type, value);
    }

    public boolean canBeApplied(Feature feature, Collection<Tag> tags) {
        return hasCorrectTags(feature, tags) && hasCompatibleGeometryType(feature);
    }

    private boolean hasCompatibleGeometryType(Feature feature) {
        getFeatureType();
        GeomRestriction restriction = getGeomRestriction();
        GeometryAttribute property = feature.getDefaultGeometryProperty();
        Geometry geom = (Geometry) property.getValue();
        if (geom.getClass().equals(Point.class)) {
            return geometryType == Point.class;
        } else {
            if (geometryType.equals(Point.class)) {
                return false;
            }
            Coordinate[] coords = geom.getCoordinates();
            if (geometryType.equals(Polygon.class) && coords.length < 3) {
                return false;
            }
            boolean isClosed = coords[0].equals(coords[coords.length - 1]);
            if (isClosed && restriction.equals(GeomRestriction.ONLY_OPEN_LINES)) {
                return false;
            }
            if (!isClosed && restriction.equals(GeomRestriction.ONLY_CLOSED_LINES)) {
                return false;
            }
            return true;
        }
    }

    private boolean hasCorrectTags(Feature feature, Collection<Tag> tags) {
        if (filter.isEmpty() || (filter.size() == 1 && filter.containsKey("geom"))
                && (exclude == null || exclude.isEmpty())) {
            return true;
        }
        boolean ret = false;
        ArrayList<String> tagNames = Lists.newArrayList();
        for (Tag tag : tags) {
            tagNames.add(tag.getKey());
            if (exclude != null && exclude.keySet().contains(tag.getKey())) {
                List<String> values = exclude.get(tag.getKey());
                if (values != null) {
                    if (values.isEmpty() || values.contains(tag.getValue())) {
                        return false;
                    }
                }
            }
            if (filter.keySet().contains(tag.getKey())) {
                List<String> values = filter.get(tag.getKey());
                if (values.isEmpty() || values.contains(tag.getValue())) {
                    ret = true;
                }
            }

        }
        if (ret) {
            for (String mandatory : getMandatoryTags()) {
                if (!tagNames.contains(mandatory)) {
                    return false;
                }
            }
        }
        return ret;
    }

    public String getName() {
        return name;
    }

    /**
     * Returns true if this rule generates feature with a line or polygon geometry, or it doesn't
     * have a geometry attribute, so it can take ways as inputs
     * 
     * @return
     */
    public boolean canUseWays() {
        getFeatureType();
        return !geometryType.equals(Point.class);
    }

    /**
     * Returns true if this rule generates feature with a point geometry, or it doesn't have a
     * geometry attribute, so it can take nodes as inputs
     * 
     * @return
     */
    public boolean canUseNodes() {
        getFeatureType();
        return geometryType.equals(Point.class);
    }

    /**
     * Resolves the original tag name based on the name of a field created by this mapping rule (an
     * alias for a tag name) *
     * 
     * @param field the name of the field
     * @return the name of the tag from which the passed field was created in the specified mapped
     *         tree. If the alias cannot be resolved, that passed alias itself is returned
     */
    public String getTagNameFromAlias(String alias) {
        Set<String> keys = this.fields.keySet();
        for (String key : keys) {
            AttributeDefinition field = fields.get(key);
            if (field.getName().equals(alias)) {
                return key;
            }
        }
        return alias;
    }

    public boolean equals(Object o) {
        if (o instanceof MappingRule) {
            MappingRule m = (MappingRule) o;
            return name.equals(m.name) && m.fields.equals(fields) && m.filter.equals(filter)
                    && m.exclude.equals(exclude) && m.defaultFields.equals(defaultFields);
        } else {
            return false;
        }
    }

    private ArrayList<String> getMandatoryTags() {
        if (_mandatoryTags == null) {
            _mandatoryTags = Lists.newArrayList();
            if (exclude != null) {
                for (String key : this.exclude.keySet()) {
                    if (exclude.get(key) == null) {
                        _mandatoryTags.add(key);
                    }
                }
            }
        }
        return _mandatoryTags;

    }
}
