package org.locationtech.geogig.porcelain.index;

import org.locationtech.geogig.repository.AbstractGeoGigOp;

/**
 * Creates a spatial index for every commit a given type tree is present at.
 *
 */
public class CreateFullHistoryIndex extends AbstractGeoGigOp<Void> {

    @Override
    protected Void _call() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    /*
    private void indexHistory(IndexInfo index) {
        ImmutableList<Ref> branches = command(BranchListOp.class).setLocal(true).setRemotes(true)
                .call();

        for (Ref ref : branches) {
            Iterator<RevCommit> commits = command(LogOp.class).setUntil(ref.getObjectId()).call();
            while (commits.hasNext()) {
                RevCommit next = commits.next();
                indexCommit(index, next.getId().toString());
            }
        }
    }

    private void indexCommit(IndexInfo index, String committish) {
        String treeSpec = committish + ":" + treeName;
        Optional<ObjectId> treeId = command(ResolveTreeish.class).setTreeish(treeSpec).call();
        if (!treeId.isPresent()) {
            return;
        }
        command(BuildIndexOp.class).setIndex(index).setCanonicalTreeId(treeId.get())
                .setProgressListener(getProgressListener()).call();
    }
    */
}
