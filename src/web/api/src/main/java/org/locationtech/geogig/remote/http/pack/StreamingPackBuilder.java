package org.locationtech.geogig.remote.http.pack;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.locationtech.geogig.remote.http.pack.StreamingPackIO.Event.REF_END;
import static org.locationtech.geogig.remote.http.pack.StreamingPackIO.Event.REF_START;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.remote.http.pack.StreamingPackIO.Event;
import org.locationtech.geogig.remotes.pack.AbstractPackBuilder;
import org.locationtech.geogig.remotes.pack.Pack;
import org.locationtech.geogig.remotes.pack.RefRequest;
import org.locationtech.geogig.repository.ProgressListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;

public class StreamingPackBuilder extends AbstractPackBuilder {

    private static final Logger log = LoggerFactory.getLogger(StreamingPackBuilder.class);
    
    private StreamingPackIO packIO = new StreamingPackIO();

    private DataInputStream in;

    private ProgressListener progress;

    public StreamingPackBuilder(DataInputStream dataInput, ProgressListener progressListener) {
        checkNotNull(dataInput);
        checkNotNull(progressListener);
        this.in = dataInput;
        this.progress = progressListener;
        this.progress = progressListener;
    }

    @Override
    public Pack build() {
        final java.util.function.Function<ProgressListener, String> oldIndicator = progress
                .progressIndicator();

        progress.setProgressIndicator((p) -> String
                .format("Server: resolving missing commits... %,d", (int) p.getProgress()));
        progress.started();
        LinkedHashMap<RefRequest, List<ObjectId>> missingCommits;
        try {
            // byte[] byteArray = ByteStreams.toByteArray(in);
            // in.close();
            // this.in = new DataInputStream(new ByteArrayInputStream(byteArray));
            packIO.readHeader(in);
            missingCommits = readRefsCommits();
        } catch (Exception e) {
            log.error("Error building pack", e);
            try {
                in.close();
            } catch (IOException ex) {
                log.warn("Error closing stream, but pack build already failed", ex);
            }
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
        progress.complete();
        progress.setProgressIndicator(oldIndicator);

        return new StreamingPack(missingCommits, in);
    }

    private LinkedHashMap<RefRequest, List<ObjectId>> readRefsCommits() throws IOException {
        LinkedHashMap<RefRequest, List<ObjectId>> missingCommits = new LinkedHashMap<>();
        Event evt;
        while (REF_START == (evt = packIO.readNextEvent(in))) {
            RefRequest req = new RefRequestIO().read(in);
            List<ObjectId> refCommits = readCommits();
            requireEvent(REF_END);
            missingCommits.put(req, refCommits);
        }
        return missingCommits;
    }

    private List<ObjectId> readCommits() throws IOException {
        List<ObjectId> commits = new ArrayList<>();
        ObjectId obj;
        while ((obj = packIO.readId(in)) != null) {
            commits.add(obj);
            progress.setProgress(progress.getProgress() + 1);
        }
        return commits;
    }

    private Event requireEvent(Event expected) throws IOException {
        Event evt = packIO.readNextEvent(in);
        checkState(expected == evt, "Expected %s, got %s", expected, evt);
        return evt;
    }

}
