package org.locationtech.geogig.remotes.pack;

import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.storage.BulkOpListener;

public class ObjectReporter extends BulkOpListener.CountingListener {

    public final AtomicLong total = new AtomicLong();

    final AtomicLong tags = new AtomicLong();

    final AtomicLong commits = new AtomicLong();

    final AtomicLong trees = new AtomicLong();

    final AtomicLong buckets = new AtomicLong();

    final AtomicLong features = new AtomicLong();

    final AtomicLong featureTypes = new AtomicLong();

    final ProgressListener progress;

    public ObjectReporter(ProgressListener progress) {
        this.progress = progress;
    }

    public @Override void found(ObjectId object, @Nullable Integer storageSizeBytes) {
        super.found(object, storageSizeBytes);
        notifyProgressListener();
    }

    public @Override void inserted(ObjectId object, @Nullable Integer storageSizeBytes) {
        super.inserted(object, storageSizeBytes);
        notifyProgressListener();
    }

    public void addTree() {
        increment(trees);
    }

    public void addTag() {
        increment(tags);
    }

    public void addBucket() {
        // increment(buckets);
        increment(trees);
    }

    public void addFeature() {
        increment(features);
    }

    public void addFeatureType() {
        increment(featureTypes);
    }

    public void addCommit() {
        increment(commits);
    }

    private void increment(AtomicLong counter) {
        counter.incrementAndGet();
        total.incrementAndGet();
        notifyProgressListener();
    }

    private void notifyProgressListener() {
        progress.setProgress(progress.getProgress() + 1);
    }

    public void complete() {
        progress.complete();
    }

    public @Override String toString() {
        // return String.format(
        // "inserted %,d/%,d: commits: %,d, trees: %,d, buckets: %,d, features: %,d, ftypes: %,d",
        // super.inserted(), total.get(), commits.get(), trees.get(), buckets.get(),
        // features.get(), featureTypes.get());
        return String.format(
                "inserted %,d/%,d: commits: %,d, trees: %,d, features: %,d, ftypes: %,d",
                super.inserted(), total.get(), commits.get(), trees.get(), features.get(),
                featureTypes.get());
    }

    public void add(TYPE type) {
        switch (type) {
        case COMMIT:
            addCommit();
            break;
        case FEATURE:
            addFeature();
            break;
        case FEATURETYPE:
            addFeatureType();
            break;
        case TAG:
            addTag();
            break;
        case TREE:
            addTree();
            break;
        default:
            break;
        }
    }
}