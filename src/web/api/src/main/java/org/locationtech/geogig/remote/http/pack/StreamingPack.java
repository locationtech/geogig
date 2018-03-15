package org.locationtech.geogig.remote.http.pack;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.remotes.RefDiff;
import org.locationtech.geogig.remotes.pack.ObjectReporter;
import org.locationtech.geogig.remotes.pack.Pack;
import org.locationtech.geogig.remotes.pack.PackProcessor;
import org.locationtech.geogig.remotes.pack.RefRequest;
import org.locationtech.geogig.repository.ProgressListener;

import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;

public class StreamingPack implements Pack {

    private LinkedHashMap<RefRequest, List<ObjectId>> missingCommits;

    private Iterator<RevObject> contents;

    private DataInputStream in;

    public StreamingPack(LinkedHashMap<RefRequest, List<ObjectId>> missingCommits,
            DataInputStream in) {
        this.missingCommits = missingCommits;
        this.in = in;
        contents = new RevObjectInputStream(in);
    }

    @Override
    public List<RefDiff> applyTo(PackProcessor target, ProgressListener progress) {

        final ObjectReporter report = new ObjectReporter(progress);

        Iterator<? extends RevObject> allContents = Iterators.filter(contents, (o) -> {
            report.add(o.getType());
            return true;
        });

        progress.setDescription("Applying changes...");

        // back up current progress indicator
        final Function<ProgressListener, String> defaultProgressIndicator;
        defaultProgressIndicator = progress.progressIndicator();
        // set our custom progress indicator
        progress.setProgressIndicator((p) -> report.toString());
        try {
            progress.started();
            final Stopwatch sw = Stopwatch.createStarted();
            target.putAll(allContents, report);
            sw.stop();
            progress.complete();
            if (report.total.get() > 0) {
                progress.started();
                String description = String.format("Objects inserted: %,d, repeated: %,d, time: %s",
                        report.inserted(), report.found(), sw);
                progress.setDescription(description);

                progress.complete();
            }
        } finally {
            progress.setProgressIndicator(defaultProgressIndicator);
        }

        List<RefDiff> refs = new ArrayList<>();
        for (RefRequest req : missingCommits.keySet()) {
            Ref oldRef = req.have.isPresent() ? new Ref(req.name, req.have.get()) : null;
            Ref newRef = new Ref(req.name, req.want);
            RefDiff changedRef = new RefDiff(oldRef, newRef);
            refs.add(changedRef);
        }
        return refs;
    }

    private static class RevObjectInputStream extends AbstractIterator<RevObject> {

        private DataInputStream in;

        private StreamingPackIO packedObjects = new StreamingPackIO();

        public RevObjectInputStream(DataInputStream in) {
            this.in = in;
        }

        @Override
        protected RevObject computeNext() {
            RevObject next;
            try {
                next = packedObjects.readObject(in);
            } catch (Exception e) {
                close();
                Throwables.throwIfUnchecked(e);
                throw new RuntimeException(e);
            }
            if (next == null) {
                close();
                return endOfData();
            }
            return next;
        }

        private void close() {
            try {
                in.close();
            } catch (IOException e1) {
            }
        }

    }

}
