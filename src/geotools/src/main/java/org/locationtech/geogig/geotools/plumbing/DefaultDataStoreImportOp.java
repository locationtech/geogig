package org.locationtech.geogig.geotools.plumbing;

import org.geotools.data.DataStore;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.porcelain.AddOp;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.repository.WorkingTree;

public class DefaultDataStoreImportOp extends DataStoreImportOp<RevCommit> {

    @Override
    protected RevCommit callInternal() {
        final DataStore dataStore = dataStoreSupplier.get();

        RevCommit revCommit;

        /**
         * Import needs to: 1) Import the data 2) Add changes to be staged 3) Commit staged changes
         */
        try {
            // import data into the repository
            final ImportOp importOp = getImportOp(dataStore);
            importOp.setProgressListener(getProgressListener());
            importOp.call();
            // add the imported data to the staging area
            callAdd();
            // commit the staged changes
            revCommit = callCommit();
        } finally {
            dataStore.dispose();
            dataStoreSupplier.cleanupResources();
        }

        return revCommit;
    }

    private WorkingTree callAdd() {
        final AddOp addOp = context.command(AddOp.class);
        addOp.setProgressListener(getProgressListener());
        return addOp.call();
    }

    private RevCommit callCommit() {
        final CommitOp commitOp = context.command(CommitOp.class).setAll(true)
                .setAuthor(authorName, authorEmail).setMessage(commitMessage);
        commitOp.setProgressListener(getProgressListener());
        return commitOp.call();
    }

    private ImportOp getImportOp(DataStore dataStore) {
        final ImportOp importOp = context.command(ImportOp.class);
        return importOp.setDataStore(dataStore).setTable(table).setAll(all).setOverwrite(!add)
                .setAdaptToDefaultFeatureType(!forceFeatureType).setAlter(alter)
                .setDestinationPath(dest).setFidAttribute(fidAttribute);
    }

}
