package org.locationtech.geogig.geotools.cli.geopkg.interchange;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.locationtech.geogig.api.DefaultProgressListener;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.ProgressListener;
import org.locationtech.geogig.api.plumbing.ResolveTreeish;
import org.locationtech.geogig.repository.Repository;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

public class InterchangeFormat {

    private Repository repository;

    private ProgressListener progressListener = DefaultProgressListener.NULL;

    private File geopackageDbFile;

    public InterchangeFormat(File geopackageDbFile, Repository repository) {
        this.geopackageDbFile = geopackageDbFile;
        this.repository = repository;
    }

    public InterchangeFormat setProgressListener(ProgressListener progressListener) {
        Preconditions.checkNotNull(progressListener);
        this.progressListener = progressListener;
        return this;
    }

    private void log(String msgFormat, Object... args) {
        progressListener.setDescription(format(msgFormat, args));
    }

    /**
     * @param sourceTreeIsh tree-ish from which features have been exported (supports format
     *        {@code <[<tree-ish>:]<treePath>>}. e.g. {@code buildings}, {@code HEAD~2:buildings},
     *        {@code abc123fg:buildings}, {@code origin/master:buildings} ). {@code buildings}
     *        resolves to {@code WORK_HEAD:buildings}.
     * @param targetTableName name of table where features from {@code sourceTreeIsh} have been
     *        exported
     */
    public void export(final String sourceTreeIsh, final String targetTableName) throws IOException {

        final String refspec;
        final String headTreeish;
        final String featureTreePath;
        final ObjectId rootTreeId;

        {
            if (sourceTreeIsh.contains(":")) {
                refspec = sourceTreeIsh;
            } else {
                refspec = "WORK_HEAD:" + sourceTreeIsh;
            }

            checkArgument(!refspec.endsWith(":"), "No path specified.");

            String[] split = refspec.split(":");
            headTreeish = split[0];
            featureTreePath = split[1];
            Optional<ObjectId> rootId = repository.command(ResolveTreeish.class)
                    .setTreeish(headTreeish).call();

            checkArgument(rootId.isPresent(), "Couldn't resolve '" + refspec
                    + "' to a treeish object");
            rootTreeId = rootId.get();
        }

        log("Exporting repository metadata from '%s' (tree %s)...", refspec, rootTreeId);

        final GeoPackage geopackage = new GeoPackage(geopackageDbFile);
        try {
            FeatureEntry featureEntry = geopackage.feature(targetTableName);
            checkState(featureEntry != null, "Table '%s' does not exist", targetTableName);

            try {
                createAuditLog(geopackage, featureTreePath, featureEntry, rootTreeId);
            } catch (SQLException e) {
                throw Throwables.propagate(e);
            }

        } finally {
            geopackage.close();
        }
    }

    private void createAuditLog(final GeoPackage geopackage, final String mappedPath,
            final FeatureEntry fe, final ObjectId rootTreeId) throws SQLException {

        log("Creating audit metadata for table '%s'", fe.getIdentifier());

        final DataSource dataSource = geopackage.getDataSource();

        try (Connection connection = dataSource.getConnection()) {
            GeogigMetadata metadata = new GeogigMetadata(connection);
            URI repoURI = repository.getLocation();
            metadata.init(repoURI);

            final String auditedTable = fe.getIdentifier();

            metadata.createAudit(auditedTable, mappedPath, rootTreeId);

        }
    }

}
