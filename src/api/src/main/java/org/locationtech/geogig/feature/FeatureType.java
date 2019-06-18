package org.locationtech.geogig.feature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.locationtech.geogig.crs.CoordinateReferenceSystem;
import org.locationtech.jts.geom.Geometry;

import com.google.common.base.Preconditions;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.NonFinal;

@EqualsAndHashCode(of = { "name", "descriptors" })
public @Value @Builder class FeatureType {

    private static final int UNSET_GEOMETRY_INDEX = Integer.MIN_VALUE;

    private static final int NO_GEOMETRY_PROPERTY = -1;

    private @NonNull Name name;

    private @NonNull List<PropertyDescriptor> descriptors;

    // unset, -1 once set to mean no geometry attribute
    private @NonFinal @Default int geometryDescriptorIndex = UNSET_GEOMETRY_INDEX;

    @Getter(value = AccessLevel.PRIVATE)
    private final ConcurrentMap<String, Integer> resolvedAttIndexes = new ConcurrentHashMap<>();

    public List<PropertyDescriptor> getDescriptors() {
        return new ArrayList<>(this.descriptors);
    }

    public String getTypeName() {
        return getName().getLocalPart();
    }

    public int getSize() {
        return descriptors.size();
    }

    /**
     * @throws IndexOutOfBoundsException
     */
    public PropertyDescriptor getDescriptor(int index) {
        return descriptors.get(index);
    }

    /**
     * @throws NoSuchElementException
     */
    public PropertyDescriptor getDescriptor(@NonNull String attName) {
        return descriptors.stream().filter(d -> attName.equals(d.getName().getLocalPart()))
                .findFirst().orElseThrow(() -> nosuchAttributeException(attName));
    }

    private NoSuchElementException nosuchAttributeException(String attName) {
        return new NoSuchElementException(
                String.format("Attribute '%s' does not exist: %s", attName, descriptors.stream()
                        .map(d -> d.getName().getLocalPart()).collect(Collectors.joining(", "))));
    }

    /**
     * @return CRS of the default geometry property, or {@code null}
     */
    public CoordinateReferenceSystem getCoordinateReferenceSystem() {
        return getGeometryDescriptor().map(PropertyDescriptor::getCoordinateReferenceSystem)
                .orElse(null);
    }

    /**
     * The default geometry property is either explicitly set when creating the featuretype
     * instance, or computed as the first geometry property in the descriptors list otherwise.
     * 
     * @return the index of the default geometry property, or {@code -1} if no geometry property
     *         exists at all
     */
    public int getGeometryDescriptorIndex() {
        if (this.geometryDescriptorIndex == UNSET_GEOMETRY_INDEX) {
            List<PropertyDescriptor> descriptors = getDescriptors();
            this.geometryDescriptorIndex = IntStream.range(0, descriptors.size())
                    .filter(i -> descriptors.get(i).isGeometryDescriptor()).findFirst()
                    .orElse(NO_GEOMETRY_PROPERTY);
        }
        return this.geometryDescriptorIndex;
    }

    /**
     * The default geometry property is either explicitly set when creating the featuretype
     * instance, or computed as the first geometry property in the descriptors list otherwise.
     * 
     * @return Optional containing the default geometry property, or {@link Optional#empty() empty}
     *         if no geometry property exists at all
     */
    public Optional<PropertyDescriptor> getGeometryDescriptor() {
        int index = getGeometryDescriptorIndex();
        return Optional.ofNullable(index == NO_GEOMETRY_PROPERTY ? null : descriptors.get(index));
    }

    public int getAttributeIndex(@NonNull Name name) {
        if (null == name.getNamespaceURI()) {
            return getAttributeIndex(name.getLocalPart());
        }
        for (int i = 0; i < descriptors.size(); i++) {
            if (name.equals(descriptors.get(i).getName())) {
                return i;
            }
        }
        throw nosuchAttributeException(name.toString());
    }

    public int getAttributeIndex(final @NonNull String name) {
        Integer index = resolvedAttIndexes.computeIfAbsent(name, att -> {
            for (int i = 0; i < descriptors.size(); i++) {
                PropertyDescriptor propertyDescriptor = descriptors.get(i);
                if (name.equals(propertyDescriptor.getLocalName())) {
                    return Integer.valueOf(i);
                }
            }
            return null;
        });
        if (index == null) {
            throw nosuchAttributeException(name);
        }
        return index.intValue();
    }

    public static class FeatureTypeBuilder {

        public FeatureTypeBuilder add(@NonNull String propertyName, @NonNull Class<?> binding,
                CoordinateReferenceSystem crs) {
            Preconditions.checkArgument(Geometry.class.isAssignableFrom(binding));
            Name name = new Name(propertyName);
            PropertyDescriptor descriptor = PropertyDescriptor.builder().name(name).typeName(name)
                    .binding(binding).coordinateReferenceSystem(crs).build();
            return add(descriptor);
        }

        public FeatureTypeBuilder add(@NonNull String propertyName, @NonNull Class<?> binding) {
            Name name = new Name(propertyName);
            PropertyDescriptor descriptor = PropertyDescriptor.builder().name(name).typeName(name)
                    .binding(binding).build();
            return add(descriptor);
        }

        public FeatureTypeBuilder add(@NonNull PropertyDescriptor p) {
            if (this.descriptors == null) {
                this.descriptors = new ArrayList<>();
            } else {
                Optional<PropertyDescriptor> existing = this.descriptors.stream()
                        .filter(d -> d.getName().equals(p.getName())).findFirst();
                if (existing.isPresent()) {
                    throw new IllegalArgumentException(
                            "Duplicate property descriptor name: " + p.getName());
                }
            }
            this.descriptors.add(p);
            return this;
        }

        public FeatureTypeBuilder localName(@NonNull String name) {
            return name(new Name(name));
        }

        public FeatureTypeBuilder descriptors(@NonNull List<PropertyDescriptor> descriptors) {
            final List<@NonNull Name> names = descriptors.stream().map(PropertyDescriptor::getName)
                    .collect(Collectors.toList());

            Set<@NonNull Name> duplicates = names.stream()
                    .filter(name -> Collections.frequency(names, name) > 1)
                    .collect(Collectors.toSet());
            if (!duplicates.isEmpty()) {
                throw new IllegalArgumentException("Duplicate attribute names: " + duplicates);
            }
            this.descriptors = new ArrayList<>(descriptors);
            return this;
        }
    }
}
