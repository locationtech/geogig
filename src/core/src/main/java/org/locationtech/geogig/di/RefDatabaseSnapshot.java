package org.locationtech.geogig.di;

import java.util.Map;

import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.storage.decorator.ForwardingRefDatabase;
import org.locationtech.geogig.storage.memory.HeapRefDatabase;

import com.google.common.base.Preconditions;

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

    public @Override String getRef(String name) {
        String val = snapshot.getRef(name);
        return val;
    }

    public @Override String getSymRef(String name) {
        String val = snapshot.getSymRef(name);
        return val;
    }

    public @Override void putRef(String refName, String refValue) {
        actual.putRef(refName, refValue);
        snapshot.putRef(refName, refValue);
    }

    public @Override void putSymRef(String name, String val) {
        actual.putSymRef(name, val);
        snapshot.putSymRef(name, val);
    }

    public @Override String remove(String refName) {
        String ret = actual.remove(refName);
        snapshot.remove(refName);
        return ret;
    }

    public @Override Map<String, String> getAll() {
        return snapshot.getAll();
    }

    public @Override Map<String, String> getAll(String prefix) {
        return snapshot.getAll(prefix);
    }

    public @Override Map<String, String> removeAll(String namespace) {
        Map<String, String> ret = actual.removeAll(namespace);
        snapshot.removeAll(namespace);
        return ret;
    }
}
