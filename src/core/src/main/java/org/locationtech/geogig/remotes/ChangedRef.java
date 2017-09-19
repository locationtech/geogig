package org.locationtech.geogig.remotes;

import static com.google.common.base.Preconditions.*;
import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Ref;

import com.google.common.base.MoreObjects;

/**
 * Represents the state of a {@link Ref} at two different points in time
 */
public class ChangedRef {

    public enum Type {
        ADDED_REF, REMOVED_REF, CHANGED_REF
    }

    private @Nullable Ref oldRef;

    private @Nullable Ref newRef;

    public ChangedRef(@Nullable Ref oldRef, @Nullable Ref newRef) {
        checkArgument(oldRef != null || newRef != null);
//        checkArgument(oldRef != null && newRef != null ? oldRef.getName().equals(newRef.getName())
//                : true);
        this.oldRef = oldRef;
        this.newRef = newRef;
    }

    public static ChangedRef added(Ref ref) {
        checkNotNull(ref);
        return new ChangedRef(null, ref);
    }

    public static ChangedRef removed(Ref ref) {
        checkNotNull(ref);
        return new ChangedRef(ref, null);
    }

    public static ChangedRef updated(Ref oldRef, Ref newRef) {
        checkNotNull(oldRef);
        checkNotNull(newRef);
        return new ChangedRef(oldRef, newRef);
    }

    public boolean isDelete() {
        return getType() == Type.REMOVED_REF;
    }

    public boolean isNew() {
        return getType() == Type.ADDED_REF;
    }

    public boolean isUpdate() {
        return getType() == Type.CHANGED_REF;
    }

    public Ref getOldRef() {
        return oldRef;
    }

    public void setOldRef(Ref oldRef) {
        this.oldRef = oldRef;
    }

    public Ref getNewRef() {
        return newRef;
    }

    public void setNewRef(Ref newRef) {
        this.newRef = newRef;
    }

    public ChangedRef.Type getType() {
        Type type = Type.CHANGED_REF;
        if (oldRef == null) {
            type = Type.ADDED_REF;
        } else if (newRef == null) {
            type = Type.REMOVED_REF;
        }
        return type;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(ChangedRef.class) //
                .addValue(getType()) //
                .addValue(oldRef) //
                .addValue(newRef) //
                .toString();
    }
}