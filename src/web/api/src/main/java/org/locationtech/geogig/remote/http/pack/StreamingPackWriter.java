package org.locationtech.geogig.remote.http.pack;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.locationtech.geogig.storage.BulkOpListener.NOOP_LISTENER;

import java.io.DataOutputStream;
import java.util.Iterator;
import java.util.Set;

import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.remotes.pack.LocalPackBuilder;
import org.locationtech.geogig.remotes.pack.Pack;
import org.locationtech.geogig.remotes.pack.PackBuilder;
import org.locationtech.geogig.remotes.pack.PackProcessor;
import org.locationtech.geogig.remotes.pack.RefRequest;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.BulkOpListener;

public class StreamingPackWriter extends LocalPackBuilder implements PackBuilder, PackProcessor {

    private DataOutputStream out;

    public StreamingPackWriter(Repository localRepo, DataOutputStream out) {
        super(localRepo);
        checkNotNull(out);
        this.out = out;
    }

    public @Override void start(Set<RevTag> tags) {
        super.start(tags);
        writeHeader();
        sendTags(tags);
    }

    public @Override void startRefResponse(RefRequest req) {
        super.startRefResponse(req);
        sendStartRef(req);
    }

    public @Override void addCommit(RevCommit commit) {
        super.addCommit(commit);
        sendRefCommit(commit);
    }

    public @Override void endRefResponse() {
        super.endRefResponse();
        sendEndRef();
    }

    public @Override Pack build() {
        return super.build();
    }

    void putAll(Iterator<? extends RevObject> iterator) {
        putAll(iterator, NOOP_LISTENER);
    }

    public @Override void putAll(Iterator<? extends RevObject> iterator, BulkOpListener listener) {
        // TODO Auto-generated method stub

    }

    private void sendTags(Set<RevTag> tags) {
        putAll(tags.iterator());
    }

    private void writeHeader() {
        // TODO Auto-generated method stub

    }

    private void sendStartRef(RefRequest req) {
        // TODO Auto-generated method stub

    }

    private void sendEndRef() {
        // TODO Auto-generated method stub

    }

    private void sendRefCommit(RevCommit commit) {
        // TODO Auto-generated method stub

    }

}
