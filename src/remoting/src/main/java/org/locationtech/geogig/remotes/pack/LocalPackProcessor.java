package org.locationtech.geogig.remotes.pack;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Iterator;

import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ObjectStore;

public class LocalPackProcessor implements PackProcessor {

    private ObjectStore target;

    public LocalPackProcessor(ObjectStore target) {
        checkNotNull(target);
        this.target = target;
    }

    @Override
    public void putAll(Iterator<? extends RevObject> iterator, BulkOpListener listener) {
        target.putAll(iterator, listener);
    }

}
