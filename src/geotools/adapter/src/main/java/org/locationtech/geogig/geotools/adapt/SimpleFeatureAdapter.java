package org.locationtech.geogig.geotools.adapt;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.geotools.feature.GeometryAttributeImpl;
import org.geotools.feature.type.AttributeDescriptorImpl;
import org.geotools.feature.type.Types;
import org.geotools.filter.identity.FeatureIdImpl;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.util.Converters;
import org.geotools.util.Utilities;
import org.locationtech.geogig.feature.Feature;
import org.locationtech.geogig.feature.PropertyDescriptor;
import org.locationtech.jts.geom.Geometry;
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
import org.opengis.filter.identity.FeatureId;
import org.opengis.filter.identity.Identifier;
import org.opengis.geometry.BoundingBox;

import com.google.common.base.Objects;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@ToString(of = { "feature" })
public class SimpleFeatureAdapter implements SimpleFeature {

    private final SimpleFeatureType type;

    private final Feature feature;

    private Map<Object, Object> userData;

    /** The set of user data attached to each attribute (lazily created) */
    private Map<Object, Object>[] attributeUserData;

    public SimpleFeatureAdapter(Feature feature) {
        this(GT.adapt(feature.getType()), feature);
    }

    public SimpleFeatureAdapter(@NonNull SimpleFeatureType type, @NonNull Feature feature) {
        this.type = type;
        this.feature = feature;
    }

    public @Override FeatureId getIdentifier() {
        return new FeatureIdImpl(feature.getId());
    }

    public @Override BoundingBox getBounds() {
        return GT.adapt(type.getCoordinateReferenceSystem(), feature.getDefaultGeometryBounds());
    }

    public @Override GeometryAttribute getDefaultGeometryProperty() {
        GeometryDescriptor geometryDescriptor = type.getGeometryDescriptor();
        if (geometryDescriptor == null) {
            return null;
        }
        Object defaultGeometry = getDefaultGeometry();
        return new GeometryAttributeImpl(defaultGeometry, geometryDescriptor, null);
    }

    public @Override void setDefaultGeometryProperty(GeometryAttribute geometryAttribute) {
        setDefaultGeometry(geometryAttribute == null ? null : geometryAttribute.getValue());
    }

    @SuppressWarnings("unchecked")
    public @Override void setValue(Object newValue) {
        setValue((Collection<Property>) newValue);
    }

    public @Override void setValue(Collection<Property> values) {
        for (Property p : values) {
            setAttribute(p.getName(), p.getValue());
        }
    }

    public @Override Collection<? extends Property> getValue() {
        return getProperties();
    }

    public @Override Collection<Property> getProperties() {
        return new AttributeList();
    }

    public @Override Collection<Property> getProperties(Name name) {
        return Collections.singletonList(getProperty(name));
    }

    public @Override Collection<Property> getProperties(String name) {
        return Collections.singletonList(getProperty(name));
    }

    public @Override Property getProperty(Name name) {
        int index = feature.getType().getAttributeIndex(GT.adapt(name));
        return getProperty(index);
    }

    public @Override Property getProperty(String name) {
        int index = feature.getType().getAttributeIndex(name);
        return getProperty(index);
    }

    private Property getProperty(int index) {
        PropertyDescriptor descriptor = feature.getType().getDescriptor(index);
        if (descriptor.isGeometryDescriptor()) {
            return new SimpleGeometryAttribute(index);
        }
        return new Attribute(index);
    }

    public @Override void validate() throws IllegalAttributeException {
        for (int i = 0; i < getAttributeCount(); i++) {
            AttributeDescriptor descriptor = getType().getDescriptor(i);
            Types.validate(descriptor, getAttribute(i));
        }
    }

    public @Override AttributeDescriptor getDescriptor() {
        return new AttributeDescriptorImpl(type, type.getName(), 0, Integer.MAX_VALUE, true, null);
    }

    public @Override Name getName() {
        return type.getName();
    }

    public @Override boolean isNillable() {
        return true;
    }

    public @Override Map<Object, Object> getUserData() {
        if (userData == null) {
            userData = new HashMap<>();
        }
        return userData;
    }

    public @Override String getID() {
        return feature.getId();
    }

    public @Override SimpleFeatureType getType() {
        return type;
    }

    public @Override SimpleFeatureType getFeatureType() {
        return type;
    }

    public @Override List<Object> getAttributes() {
        return feature.getAttributes();
    }

    public @Override void setAttributes(List<Object> values) {
        for (int i = 0; i < getAttributeCount(); i++) {
            setAttribute(i, values.get(i));
        }
    }

    public @Override void setAttributes(Object[] values) {
        setAttributes(Arrays.asList(values));
    }

    public @Override Object getAttribute(String name) {
        return feature.getAttribute(name);
    }

    public @Override void setAttribute(String name, Object value) {
        setAttribute(feature.getType().getAttributeIndex(name), value);
    }

    public @Override Object getAttribute(Name name) {
        return feature.getAttribute(GT.adapt(name));
    }

    public @Override void setAttribute(Name name, Object value) {
        setAttribute(feature.getType().getAttributeIndex(GT.adapt(name)), value);
    }

    public @Override Object getAttribute(int index) throws IndexOutOfBoundsException {
        return feature.getAttribute(index);
    }

    public @Override void setAttribute(int index, Object value) throws IndexOutOfBoundsException {
        value = validate(index, value);
        try {
            feature.setAttribute(index, value);
        } catch (IllegalArgumentException iae) {
            IllegalAttributeException exception = new IllegalAttributeException(iae.getMessage());
            exception.initCause(iae);
            throw exception;
        }
    }

    protected Object validate(int index, final Object value) {
        Object result = value;
        final AttributeDescriptor descriptor = getFeatureType().getDescriptor(index);
        final Class<?> binding = descriptor.getType().getBinding();
        if (value == null) {
            if (!descriptor.isNillable()) {
                throw new IllegalAttributeException(
                        String.format("Property %s is not nullable", descriptor.getLocalName()));
            }
        } else if (!binding.isAssignableFrom(value.getClass())) {
            result = Converters.convert(value, binding);
            if (result == null) {
                throw new IllegalAttributeException(String.format(
                        "Unable to convert value for attribute %s from %s to %s",
                        descriptor.getLocalName(), value.getClass().getName(), binding.getName()));
            }
        }

        return result;
    }

    public @Override int getAttributeCount() {
        return feature.getAttributeCount();
    }

    public @Override Object getDefaultGeometry() {
        return feature.getDefaultGeometry();
    }

    public @Override void setDefaultGeometry(Object geometry) {
        setAttribute(feature.getType().getGeometryDescriptorIndex(), geometry);
    }

    /**
     * override of equals. Returns if the passed in object is equal to this.
     * 
     * @param obj the Object to test for equality.
     * 
     * @return <code>true</code> if the object is equal, <code>false</code> otherwise.
     */
    public @Override boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof SimpleFeature)) {
            return false;
        }

        SimpleFeature feat = (SimpleFeature) obj;

        if (!getID().equals(feat.getID())) {
            return false;
        }

        if (!getFeatureType().equals(feat.getFeatureType())) {
            return false;
        }

        for (int i = 0, ii = getAttributeCount(); i < ii; i++) {
            Object attribute = getAttribute(i);
            Object otherAtt = feat.getAttribute(i);
            if (!Objects.equal(attribute, otherAtt)) {
                return false;
            }
        }

        return true;
    }

    public @Override int hashCode() {
        return getID().hashCode() * getFeatureType().hashCode();
    }

    /** Live collection backed directly on the value array */
    class AttributeList extends AbstractList<Property> {

        public @Override Property get(int index) {
            AttributeDescriptor descriptor = type.getDescriptor(index);
            if (descriptor instanceof GeometryDescriptor) {
                return new SimpleGeometryAttribute(index);
            }
            return new Attribute(index);
        }

        public @Override Attribute set(int index, Property element) {
            SimpleFeatureAdapter.this.setAttribute(index, element.getValue());
            return null;
        }

        public @Override int size() {
            return SimpleFeatureAdapter.this.getAttributeCount();
        }
    }

    /** Attribute that delegates directly to the value array */
    private @RequiredArgsConstructor class Attribute implements org.opengis.feature.Attribute {

        final int index;

        public @Override Identifier getIdentifier() {
            return null;
        }

        public @Override AttributeDescriptor getDescriptor() {
            return type.getDescriptor(index);
        }

        public @Override AttributeType getType() {
            return type.getType(index);
        }

        public @Override Name getName() {
            return getDescriptor().getName();
        }

        @SuppressWarnings("unchecked")
        public @Override Map<Object, Object> getUserData() {
            // lazily create the user data holder
            if (attributeUserData == null)
                attributeUserData = new HashMap[getAttributeCount()];
            // lazily create the attribute user data
            if (attributeUserData[index] == null)
                attributeUserData[index] = new HashMap<Object, Object>();
            return attributeUserData[index];
        }

        public @Override Object getValue() {
            return getAttribute(index);
        }

        public @Override boolean isNillable() {
            return getDescriptor().isNillable();
        }

        public @Override void setValue(Object newValue) {
            setAttribute(index, newValue);
        }

        /**
         * Override of hashCode; uses descriptor name to agree with AttributeImpl
         *
         * @return hashCode for this object.
         */
        public int hashCode() {
            return 37 * getDescriptor().hashCode()
                    + (37 * (getValue() == null ? 0 : getValue().hashCode()));
        }

        /**
         * Override of equals.
         *
         * @param obj the object to be tested for equality.
         * @return whether other is equal to this attribute Type.
         */
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

        public @Override void validate() {
            Types.validate(getDescriptor(), getValue());
        }

        public String toString() {
            StringBuffer sb = new StringBuffer("SimpleFeatureAdapter.Attribute: ");
            sb.append(getDescriptor().getName().getLocalPart());
            if (!getDescriptor().getName().getLocalPart()
                    .equals(getDescriptor().getType().getName().getLocalPart())) {
                sb.append("<");
                sb.append(getDescriptor().getType().getName().getLocalPart());
                sb.append(">");
            }
            sb.append("=");
            sb.append(getValue());
            return sb.toString();
        }
    }

    private class SimpleGeometryAttribute extends Attribute implements GeometryAttribute {

        SimpleGeometryAttribute(int index) {
            super(index);
        }

        public @Override GeometryType getType() {
            return (GeometryType) super.getType();
        }

        public @Override GeometryDescriptor getDescriptor() {
            return (GeometryDescriptor) super.getDescriptor();
        }

        public @Override BoundingBox getBounds() {
            ReferencedEnvelope bounds = new ReferencedEnvelope(type.getCoordinateReferenceSystem());
            Object value = getAttribute(index);
            if (value instanceof Geometry) {
                bounds.init(((Geometry) value).getEnvelopeInternal());
            }
            return bounds;
        }

        public @Override void setBounds(BoundingBox bounds) {
            // do nothing, this property is strictly derived. Shall throw unsupported operation
            // exception?
        }

        public @Override int hashCode() {
            return 17 * super.hashCode();
        }

        public @Override boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (!(obj instanceof SimpleGeometryAttribute)) {
                return false;
            }
            return super.equals(obj);
        }
    }
}
