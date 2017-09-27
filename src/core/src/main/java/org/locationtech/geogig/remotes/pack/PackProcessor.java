package org.locationtech.geogig.remotes.pack;

import java.util.Iterator;

import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.storage.BulkOpListener;

public interface PackProcessor {

    public void putAll(Iterator<? extends RevObject> iterator, BulkOpListener listener);

}
