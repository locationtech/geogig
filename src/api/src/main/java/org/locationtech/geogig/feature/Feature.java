package org.locationtech.geogig.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.ValueArray;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@ToString
@EqualsAndHashCode
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public abstract @RequiredArgsConstructor class Feature implements Iterable<Object> {

    protected @Getter @Setter @NonNull String id;

    protected @Getter @NonNull FeatureType type;

    protected @Getter @Setter ObjectId revision;

    public Feature(@NonNull FeatureType type) {
        this.type = type;
        this.id = null;
    }

    public abstract Object getAttribute(int index);

    public abstract void setAttribute(int index, Object value);

    public int getAttributeCount() {
        return getType().getSize();
    }

    public Object getAttribute(@NonNull String name) {
        return getAttribute(getType().getAttributeIndex(name));
    }

    public Object getAttribute(@NonNull Name name) {
        return getAttribute(getType().getAttributeIndex(name));
    }

    public void setAttribute(@NonNull String name, Object value) {
        this.setAttribute(getType().getAttributeIndex(name), value);
    }

    public void setAttribute(@NonNull Name name, Object value) {
        this.setAttribute(getType().getAttributeIndex(name), value);
    }

    public @Nullable Geometry getDefaultGeometry() {
        final int index = getType().getGeometryDescriptorIndex();
        return -1 == index ? null : Geometry.class.cast(getAttribute(index));
    }

    public @NonNull Envelope getDefaultGeometryBounds() {
        Geometry geom = getDefaultGeometry();
        return geom == null ? new Envelope() : geom.getEnvelopeInternal();
    }

    public static Feature build(@NonNull String id, @NonNull RevFeatureType type) {
        return new FeatureImpl(id, type.type());
    }

    public static Feature build(@NonNull String id, @NonNull RevFeatureType type,
            @NonNull ValueArray values) {
        ObjectId oid = values instanceof RevFeature ? ((RevFeature) values).getId() : null;
        return new FeatureImplLazy(id, type.type(), oid, values);
    }

    public static Feature build(@NonNull String id, @NonNull FeatureType type, Object... values) {
        FeatureImpl f = new FeatureImpl(id, type);
        if (values != null) {
            for (int i = 0; i < values.length; i++) {
                f.setAttribute(i, values[i]);
            }
        }
        return f;
    }

    public static Feature build(@NonNull String id, @NonNull FeatureType type,
            @NonNull ValueArray values) {
        ObjectId oid = values instanceof RevFeature ? ((RevFeature) values).getId() : null;
        return new FeatureImplLazy(id, type, oid, values);
    }

    public static Feature build(@NonNull String id, @NonNull FeatureType type,
            @NonNull ValueArray values, GeometryFactory geometryFactory) {
        ObjectId oid = values instanceof RevFeature ? ((RevFeature) values).getId() : null;
        return new FeatureImplLazy(id, type, oid, values, geometryFactory);
    }
    //// Iterable

    public @Override Spliterator<Object> spliterator() {
        return Spliterators.spliterator(iterator(), getAttributeCount(), Spliterator.SIZED);
    }

    public @Override void forEach(Consumer<? super Object> action) {
        Objects.requireNonNull(action);
        final int size = getAttributeCount();
        for (int i = 0; i < size; i++) {
            action.accept(getAttribute(i));
        }
    }

    public List<Object> getAttributes() {
        int size = this.getAttributeCount();
        List<Object> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(getAttribute(i));
        }
        return list;
    }

    public abstract Feature createCopy(@NonNull String newId);

}
