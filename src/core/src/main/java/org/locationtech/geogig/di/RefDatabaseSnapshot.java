package org.locationtech.geogig.di;

import java.util.List;
import java.util.Optional;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.storage.RefChange;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.storage.decorator.ForwardingRefDatabase;
import org.locationtech.geogig.storage.memory.HeapRefDatabase;

import com.google.common.base.Preconditions;

import lombok.NonNull;

class RefDatabaseSnapshot extends ForwardingRefDatabase implements RefDatabase {

    private HeapRefDatabase snapshot;

    public RefDatabaseSnapshot(RefDatabase actual) {
        super(actual);
        Preconditions.checkArgument(!(actual instanceof RefDatabaseSnapshot));
        this.snapshot = new HeapRefDatabase();
    }

    public @Override void open() {
        actual.open();
        snapshot.open();
        snapshot.putAll(actual.getAll());
    }

    public @Override void close() {
        actual.close();
        snapshot.close();
    }

    public @Override Optional<Ref> get(@NonNull String name) {
        return snapshot.get(name);
    }

    public @Override RefChange put(@NonNull Ref ref) {
        snapshot.put(ref);
        return actual.put(ref);
    }

    public @Override List<RefChange> putAll(@NonNull Iterable<Ref> refs) {
        List<RefChange> ret = actual.putAll(refs);
        snapshot.putAll(refs);
        return ret;
    }

    public @Override RefChange delete(@NonNull String refName) {
        snapshot.delete(refName);
        return actual.delete(refName);
    }

    public @Override RefChange delete(@NonNull Ref ref) {
        snapshot.delete(ref);
        return actual.delete(ref);
    }

    public @Override List<Ref> deleteAll(@NonNull String namespace) {
        snapshot.deleteAll(namespace);
        return actual.deleteAll(namespace);
    }

    public @Override @NonNull List<Ref> getAll() {
        return snapshot.getAll();
    }

    public @Override @NonNull List<Ref> getAll(@NonNull String prefix) {
        return snapshot.getAll(prefix);
    }

    public @Override RefChange putRef(@NonNull String name, @NonNull ObjectId value) {
        RefChange ret = actual.putRef(name, value);
        snapshot.putRef(name, value);
        return ret;
    }

    public @Override RefChange putSymRef(@NonNull String name, @NonNull String target) {
        RefChange ret = actual.putSymRef(name, target);
        snapshot.put(actual.get(name).get());
        return ret;
    }

    public @Override List<RefChange> delete(@NonNull Iterable<String> refNames) {
        List<RefChange> ret = snapshot.delete(refNames);
        actual.delete(refNames);
        return ret;
    }

    public @Override @NonNull List<Ref> deleteAll() {
        snapshot.deleteAll();
        return actual.deleteAll();
    }
}
