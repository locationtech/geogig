package org.locationtech.geogig.di;

import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.storage.memory.HeapRefDatabase;

import com.google.common.base.Preconditions;

class RefDatabaseSnapshot implements RefDatabase {

    private final RefDatabase actual;

    private HeapRefDatabase snapshot;

    public RefDatabaseSnapshot(RefDatabase actual) {
        Preconditions.checkArgument(!(actual instanceof RefDatabaseSnapshot));
        this.actual = actual;
        this.snapshot = new HeapRefDatabase();
    }

    @Override
    public void lock() throws TimeoutException {
        actual.lock();
    }

    @Override
    public void configure() throws RepositoryConnectionException {
        actual.configure();
    }

    @Override
    public boolean checkConfig() throws RepositoryConnectionException {
        return actual.checkConfig();
    }

    @Override
    public void unlock() {
        actual.unlock();
    }

    @Override
    public void create() {
        actual.create();
        snapshot.create();
        snapshot.putAll(actual.getAll());
    }

    @Override
    public void close() {
        actual.close();
        snapshot.close();
    }

    @Override
    public String getRef(String name) {
        String val = snapshot.getRef(name);
        return val;
    }

    @Override
    public String getSymRef(String name) {
        String val = snapshot.getSymRef(name);
        return val;
    }

    @Override
    public void putRef(String refName, String refValue) {
        actual.putRef(refName, refValue);
        snapshot.putRef(refName, refValue);
    }

    @Override
    public void putSymRef(String name, String val) {
        actual.putSymRef(name, val);
        snapshot.putSymRef(name, val);
    }

    @Override
    public String remove(String refName) {
        String ret = actual.remove(refName);
        snapshot.remove(refName);
        return ret;
    }

    @Override
    public Map<String, String> getAll() {
        return snapshot.getAll();
    }

    @Override
    public Map<String, String> getAll(String prefix) {
        return snapshot.getAll(prefix);
    }

    @Override
    public Map<String, String> removeAll(String namespace) {
        Map<String, String> ret = actual.removeAll(namespace);
        snapshot.removeAll(namespace);
        return ret;
    }

}
