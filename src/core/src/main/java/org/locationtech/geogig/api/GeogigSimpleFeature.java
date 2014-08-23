/*******************************************************************************
 * Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.api;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.geotools.feature.GeometryAttributeImpl;
import org.geotools.feature.type.AttributeDescriptorImpl;
import org.geotools.feature.type.Types;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.util.Converters;
import org.geotools.util.Utilities;
import org.opengis.feature.GeometryAttribute;
import org.opengis.feature.IllegalAttributeException;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.AttributeType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.GeometryType;
import org.opengis.feature.type.Name;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.filter.identity.FeatureId;
import org.opengis.filter.identity.Identifier;
import org.opengis.geometry.BoundingBox;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

/**
 */
public class GeogigSimpleFeature implements SimpleFeature {

    private static final GeometryFactory DEFAULT_GEOM_FACTORY = new GeometryFactory();

    private final FeatureId id;

    private @Nullable Node node;

    private SimpleFeatureType featureType;

    /**
     * The actual values held by this feature
     */
    private Supplier<? extends List<Optional<Object>>> revFeatureValues;

    // WARN! not to be accessed but by #getValues()
    private List<Optional<Object>> resolvedValues;

    /**
     * The attribute name -> position index
     */
    private Map<String, Integer> nameToRevTypeIndex;

    /**
     * The set of user data attached to the feature (lazily created)
     */
    private Map<Object, Object> userData;

    /**
     * The set of user data attached to each attribute (lazily created)
     */
    private Map<Object, Object>[] attributeUserData;

    private final int defaultGeomIndex;

    private final boolean defaultGeomIsPoint;

    /**
     * Fast construction of a new feature.
     * <p>
     * The object takes ownership of the provided value array, do not modify after calling the
     * constructor
     * </p>
     * 
     * @param values
     * @param featureType
     * @param id
     * @param validating
     * @param nameToRevTypeInded - attribute name to value index mapping
     */
    public GeogigSimpleFeature(ImmutableList<Optional<Object>> values,
            SimpleFeatureType featureType, FeatureId id, Map<String, Integer> nameToRevTypeInded) {

        this(Suppliers.ofInstance(values), featureType, id, nameToRevTypeInded, null);
    }

    public GeogigSimpleFeature(Supplier<? extends List<Optional<Object>>> values,
            SimpleFeatureType featureType, FeatureId id, Map<String, Integer> nameToRevTypeInded,
            @Nullable Node node) {
        this.id = id;
        this.featureType = featureType;
        this.revFeatureValues = values;
        this.nameToRevTypeIndex = nameToRevTypeInded;
        this.node = node;
        Integer defaultGeomIndex = nameToRevTypeInded.get(null);
        if (defaultGeomIndex == null) {
            this.defaultGeomIndex = -1;
            defaultGeomIsPoint = false;
        } else {
            this.defaultGeomIndex = defaultGeomIndex.intValue();
            Class<?> binding = featureType.getGeometryDescriptor().getType().getBinding();
            defaultGeomIsPoint = Point.class.isAssignableFrom(binding);
        }
    }

    private List<Optional<Object>> mutableValues() {
        List<Optional<Object>> values = getValues();
        if (values instanceof ImmutableList) {
            values = new ArrayList<>(getValues());
            resolvedValues = null;
            revFeatureValues = Suppliers.ofInstance(values);
            return getValues();
        }
        return values;
    }

    private List<Optional<Object>> getValues() {
        if (resolvedValues == null) {
            resolvedValues = revFeatureValues.get();
        }
        return resolvedValues;
    }

    @Override
    public FeatureId getIdentifier() {
        return id;
    }

    @Override
    public String getID() {
        return id.getID();
    }

    @Override
    public Object getAttribute(int index) throws IndexOutOfBoundsException {
        if (node != null && index == defaultGeomIndex && defaultGeomIsPoint
                && (resolvedValues == null || resolvedValues instanceof ImmutableList)) {
            Envelope e = new Envelope();
            node.expand(e);
            if (e.isNull()) {
                return null;
            }
            return DEFAULT_GEOM_FACTORY.createPoint(new Coordinate(e.getMinX(), e.getMinY()));
        }
        return getValues().get(index).orNull();
    }

    @Override
    public Object getAttribute(String name) {
        Integer index = nameToRevTypeIndex.get(name);
        if (index == null) {
            return null;
        }
        return getAttribute(index.intValue());
    }

    @Override
    public Object getAttribute(Name name) {
        return getAttribute(name.getLocalPart());
    }

    @Override
    public int getAttributeCount() {
        return featureType.getAttributeCount();
    }

    @Override
    public List<Object> getAttributes() {
        final int attributeCount = getAttributeCount();
        List<Object> atts = new ArrayList<Object>(attributeCount);
        for (int i = 0; i < attributeCount; i++) {
            atts.add(getAttribute(i));
        }
        return atts;
    }

    @Override
    public Object getDefaultGeometry() {
        // should be specified in the index as the default key (null)
        Integer idx = nameToRevTypeIndex.get(null);
        List<Optional<Object>> values = getValues();
        Object defaultGeometry = idx != null ? values.get(idx).orNull() : null;

        // not found? do we have a default geometry at all?
        if (defaultGeometry == null) {
            GeometryDescriptor geometryDescriptor = featureType.getGeometryDescriptor();
            if (geometryDescriptor != null) {
                Integer defaultGeomIndex = nameToRevTypeIndex.get(geometryDescriptor.getName()
                        .getLocalPart());
                defaultGeometry = values.get(defaultGeomIndex.intValue()).get();
            }
        }

        return defaultGeometry;
    }

    @Override
    public SimpleFeatureType getFeatureType() {
        return featureType;
    }

    @Override
    public SimpleFeatureType getType() {
        return featureType;
    }

    @Override
    public void setAttribute(int index, Object value) throws IndexOutOfBoundsException {
        // first do conversion
        Class<?> binding = getFeatureType().getDescriptor(index).getType().getBinding();
        Object converted = Converters.convert(value, binding);

        // finally set the value into the feature
        mutableValues().set(index, Optional.fromNullable(converted));
    }

    @Override
    public void setAttribute(String name, Object value) {
        final Integer revTypeIndex = nameToRevTypeIndex.get(name);
        if (revTypeIndex == null) {
            throw new IllegalAttributeException(null, "Unknown attribute " + name);
        }
        setAttribute(revTypeIndex.intValue(), value);
    }

    @Override
    public void setAttribute(Name name, Object value) {
        setAttribute(name.getLocalPart(), value);
    }

    @Override
    public void setAttributes(List<Object> values) {
        List<Optional<Object>> revFeatureValues = mutableValues();
        for (int i = 0; i < revFeatureValues.size(); i++) {
            setAttribute(i, values.get(i));
        }
    }

    @Override
    public void setAttributes(Object[] values) {
        setAttributes(Arrays.asList(values));
    }

    @Override
    public void setDefaultGeometry(Object geometry) {
        Integer geometryIndex = nameToRevTypeIndex.get(null);
        if (geometryIndex != null) {
            mutableValues().set(geometryIndex.intValue(), Optional.fromNullable(geometry));
        }
    }

    @Override
    public BoundingBox getBounds() {
        CoordinateReferenceSystem crs = featureType.getCoordinateReferenceSystem();
        Envelope bounds = ReferencedEnvelope.create(crs);
        if (node == null) {
            Optional<Object> o;
            List<Optional<Object>> values = getValues();
            for (int i = 0; i < values.size(); i++) {
                o = values.get(i);
                if (o.isPresent() && o.get() instanceof Geometry) {
                    Geometry g = (Geometry) o.get();
                    // TODO: check userData for crs... and ensure its of the same
                    // crs as the feature type
                    if (bounds.isNull()) {
                        bounds.init(JTS.bounds(g, crs));
                    } else {
                        bounds.expandToInclude(JTS.bounds(g, crs));
                    }
                }
            }
        } else {
            node.expand(bounds);
        }
        return (BoundingBox) bounds;
    }

    @Override
    public GeometryAttribute getDefaultGeometryProperty() {
        GeometryDescriptor geometryDescriptor = featureType.getGeometryDescriptor();
        GeometryAttribute geometryAttribute = null;
        if (geometryDescriptor != null) {
            Object defaultGeometry = getDefaultGeometry();
            geometryAttribute = new GeometryAttributeImpl(defaultGeometry, geometryDescriptor, null);
        }
        return geometryAttribute;
    }

    @Override
    public void setDefaultGeometryProperty(GeometryAttribute geometryAttribute) {
        if (geometryAttribute == null) {
            setDefaultGeometry(null);
        } else {
            setDefaultGeometry(geometryAttribute.getValue());
        }
    }

    @Override
    public Collection<Property> getProperties() {
        return new AttributeList();
    }

    @Override
    public Collection<Property> getProperties(Name name) {
        return getProperties(name.getLocalPart());
    }

    @Override
    public Collection<Property> getProperties(String name) {
        final Integer idx = nameToRevTypeIndex.get(name);
        if (idx != null) {
            // cast temporarily to a plain collection to avoid type problems with generics
            Collection<Property> c = Collections.singleton((Property) new Attribute(idx));
            return c;
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public Property getProperty(Name name) {
        return getProperty(name.getLocalPart());
    }

    @Override
    public Property getProperty(String name) {
        AttributeDescriptor descriptor = featureType.getDescriptor(name);
        if (descriptor == null) {
            return null;
        } else {
            Integer index = nameToRevTypeIndex.get(name).intValue();
            if (descriptor instanceof GeometryDescriptor) {
                Object value = getAttribute(index);
                return new GeometryAttributeImpl(value, (GeometryDescriptor) descriptor, null);
            } else {
                return new Attribute(index);
            }
        }
    }

    @Override
    public Collection<? extends Property> getValue() {
        return getProperties();
    }

    @Override
    public void setValue(Collection<Property> values) {
        int index = 0;
        for (Property p : values) {
            mutableValues().set(index, Optional.fromNullable(p.getValue()));
            index++;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setValue(Object newValue) {
        setValue((Collection<Property>) newValue);
    }

    /**
     * @see org.opengis.feature.Attribute#getDescriptor()
     */
    @Override
    public AttributeDescriptor getDescriptor() {
        return new AttributeDescriptorImpl(featureType, featureType.getName(), 0,
                Integer.MAX_VALUE, true, null);
    }

    /**
     * @return same name than this feature's {@link SimpleFeatureType}
     * @see org.opengis.feature.Property#getName()
     */
    @Override
    public Name getName() {
        return featureType.getName();
    }

    @Override
    public boolean isNillable() {
        return true;
    }

    @Override
    public Map<Object, Object> getUserData() {
        if (userData == null) {
            userData = Maps.newHashMap();
        }
        return userData;
    }

    /**
     * returns a unique code for this feature
     * 
     * @return A unique int
     */
    public int hashCode() {
        return id.hashCode() * featureType.hashCode();
    }

    /**
     * override of equals. Returns if the passed in object is equal to this.
     * 
     * @param obj the Object to test for equality.
     * 
     * @return <code>true</code> if the object is equal, <code>false</code> otherwise.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (obj == this) {
            return true;
        }

        if (!(obj instanceof GeogigSimpleFeature)) {
            return false;
        }

        GeogigSimpleFeature feat = (GeogigSimpleFeature) obj;

        if (!id.equals(feat.getIdentifier())) {
            return false;
        }

        if (!feat.getFeatureType().equals(featureType)) {
            return false;
        }

        for (int i = 0, ii = getAttributeCount(); i < ii; i++) {
            Object otherAtt = feat.getAttribute(i);

            if (!Objects.equal(otherAtt, getAttribute(i))) {
                return false;
            }
        }

        return true;
    }

    /**
     * Live collection backed directly on the value array
     */
    class AttributeList extends AbstractList<Property> {

        @Override
        public Property get(int index) {
            AttributeDescriptor descriptor = featureType.getDescriptor(index);
            if (descriptor instanceof GeometryDescriptor) {
                return new SimpleGeometryAttribute(index);
            }
            return new Attribute(index);
        }

        @Override
        public Attribute set(int index, Property element) {
            mutableValues().set(index, Optional.fromNullable(element.getValue()));
            return null;
        }

        @Override
        public int size() {
            return getAttributeCount();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        sb.append(getType().getName().getLocalPart());
        sb.append('=');
        sb.append(getValue());
        return sb.toString();
    }

    /**
     * Attribute that delegates directly to the value array
     */
    class Attribute implements org.opengis.feature.Attribute {
        int index;

        Attribute(int index) {
            this.index = index;
        }

        @Override
        public Identifier getIdentifier() {
            return null;
        }

        @Override
        public AttributeDescriptor getDescriptor() {
            return featureType.getDescriptor(index);
        }

        @Override
        public AttributeType getType() {
            return featureType.getType(index);
        }

        @Override
        public Name getName() {
            return getDescriptor().getName();
        }

        @SuppressWarnings("unchecked")
        @Override
        public Map<Object, Object> getUserData() {
            // lazily create the user data holder
            if (attributeUserData == null)
                attributeUserData = new HashMap[getAttributeCount()];
            // lazily create the attribute user data
            if (attributeUserData[index] == null)
                attributeUserData[index] = new HashMap<Object, Object>();
            return attributeUserData[index];
        }

        @Override
        public Object getValue() {
            return getValues().get(index).orNull();
        }

        @Override
        public boolean isNillable() {
            return getDescriptor().isNillable();
        }

        @Override
        public void setValue(Object newValue) {
            mutableValues().set(index, Optional.fromNullable(newValue));
        }

        /**
         * Override of hashCode; uses descriptor name to agree with AttributeImpl
         * 
         * @return hashCode for this object.
         */
        @Override
        public int hashCode() {
            Object value = getValue();
            return 37 * getDescriptor().hashCode() + (37 * (value == null ? 0 : value.hashCode()));
        }

        /**
         * Override of equals.
         * 
         * @param other the object to be tested for equality.
         * 
         * @return whether other is equal to this attribute Type.
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (!(obj instanceof Attribute)) {
                return false;
            }
            Attribute other = (Attribute) obj;
            if (!Utilities.equals(getDescriptor(), other.getDescriptor())) {
                return false;
            }
            if (!Utilities.deepEquals(getValue(), other.getValue())) {
                return false;
            }
            return Utilities.equals(getIdentifier(), other.getIdentifier());
        }

        @Override
        public void validate() {
            Types.validate(getDescriptor(), getValue());
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(getClass().getSimpleName()).append(": ");
            sb.append(getDescriptor().getName().getLocalPart());
            if (!getDescriptor().getName().getLocalPart()
                    .equals(getDescriptor().getType().getName().getLocalPart())
                    || id != null) {
                sb.append('<');
                sb.append(getDescriptor().getType().getName().getLocalPart());
                if (id != null) {
                    sb.append(" id=");
                    sb.append(id);
                }
                sb.append('>');
            }
            sb.append('=');
            sb.append(getValue());
            return sb.toString();
        }
    }

    class SimpleGeometryAttribute extends Attribute implements GeometryAttribute {

        SimpleGeometryAttribute(int index) {
            super(index);
        }

        @Override
        public GeometryType getType() {
            return (GeometryType) super.getType();
        }

        @Override
        public GeometryDescriptor getDescriptor() {
            return (GeometryDescriptor) super.getDescriptor();
        }

        @Override
        public BoundingBox getBounds() {
            ReferencedEnvelope bounds = new ReferencedEnvelope(
                    featureType.getCoordinateReferenceSystem());
            Object value = getAttribute(index);
            if (value instanceof Geometry) {
                bounds.init(((Geometry) value).getEnvelopeInternal());
            }
            return bounds;
        }

        @Override
        public void setBounds(BoundingBox bounds) {
            // do nothing, this property is strictly derived. Shall throw unsupported operation
            // exception?
        }

        @Override
        public int hashCode() {
            return 17 * super.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (!(obj instanceof SimpleGeometryAttribute)) {
                return false;
            }
            return super.equals(obj);
        }
    }

    public static Map<String, Integer> buildAttNameToRevTypeIndex(RevFeatureType revType) {

        List<PropertyDescriptor> sortedDescriptors = revType.sortedDescriptors();

        Map<String, Integer> typeAttNameToRevTypeIndex = Maps.newHashMap();

        final GeometryDescriptor defaultGeometry = ((SimpleFeatureType) revType.type())
                .getGeometryDescriptor();
        for (int revFeatureIndex = 0; revFeatureIndex < sortedDescriptors.size(); revFeatureIndex++) {
            PropertyDescriptor prop = sortedDescriptors.get(revFeatureIndex);
            typeAttNameToRevTypeIndex.put(prop.getName().getLocalPart(),
                    Integer.valueOf(revFeatureIndex));

            if (prop.equals(defaultGeometry)) {
                typeAttNameToRevTypeIndex.put(null, Integer.valueOf(revFeatureIndex));
            }

        }

        return typeAttNameToRevTypeIndex;
    }

    @Override
    public void validate() throws IllegalAttributeException {
        for (int i = 0; i < getAttributeCount(); i++) {
            AttributeDescriptor descriptor = getType().getDescriptor(i);
            Types.validate(descriptor, getAttribute(i));
        }
    }
}
