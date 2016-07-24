package org.locationtech.geogig.repository;

import java.net.URI;

import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectInserter;
import org.locationtech.geogig.storage.RefDatabase;

import com.google.common.base.Optional;

/**
 * A repository is a collection of commits, each of which is an archive of what the project's
 * working tree looked like at a past date, whether on your machine or someone else's.
 * <p>
 * It also defines HEAD (see below), which identifies the branch or commit the current working tree
 * stemmed from. Lastly, it contains a set of branches and tags, to identify certain commits by
 * name.
 * </p>
 */
public interface Repository {

    String DEPTH_CONFIG_KEY = "core.depth";

    public static interface RepositoryListener {
        public void opened(Repository repo);

        public void closed();
    }

    void addListener(RepositoryListener listener);

    void configure() throws RepositoryConnectionException;

    boolean isOpen();

    void open() throws RepositoryConnectionException;

    /**
     * Closes the repository.
     */
    void close();

    URI getLocation();

    /**
     * Finds and returns an instance of a command of the specified class.
     * 
     * @param commandClass the kind of command to locate and instantiate
     * @return a new instance of the requested command class, with its dependencies resolved
     */
    <T extends AbstractGeoGigOp<?>> T command(Class<T> commandClass);

    /**
     * Test if a blob exists in the object database
     * 
     * @param id the ID of the blob in the object database
     * @return true if the blob exists with the parameter ID, false otherwise
     */
    boolean blobExists(ObjectId id);

    /**
     * @param revStr the string to parse
     * @return the parsed {@link Ref}, or {@link Optional#absent()} if it did not parse.
     */
    Optional<Ref> getRef(String revStr);

    /**
     * @return the {@link Ref} pointed to by HEAD, or {@link Optional#absent()} if it could not be
     *         resolved.
     */
    Optional<Ref> getHead();

    /**
     * Determines if a commit with the given {@link ObjectId} exists in the object database.
     * 
     * @param id the id to look for
     * @return true if the object was found, false otherwise
     */
    boolean commitExists(ObjectId id);

    /**
     * Gets the {@link RevCommit} with the given {@link ObjectId} from the object database.
     * 
     * @param commitId the {@code ObjectId} for the commit
     * @return the {@code RevCommit}
     */
    RevCommit getCommit(ObjectId commitId);

    /**
     * Test if a tree exists in the object database
     * 
     * @param id the ID of the tree in the object database
     * @return true if the tree exists with the parameter ID, false otherwise
     */
    boolean treeExists(ObjectId id);

    /**
     * @return the {@link ObjectId} of the root tree
     */
    ObjectId getRootTreeId();

    /**
     * @return an {@link ObjectInserter} to insert objects into the object database
     */
    ObjectInserter newObjectInserter();

    /**
     * @param contentId the {@link ObjectId} of the feature to get
     * @return the {@link RevFeature} that was found in the object database
     */
    RevFeature getFeature(ObjectId contentId);

    /**
     * @return the existing {@link RevTree} pointed to by HEAD, or a new {@code RevTree} if it did
     *         not exist
     */
    RevTree getOrCreateHeadTree();

    /**
     * @param treeId the tree to retrieve
     * @return the {@link RevTree} referred to by the given {@link ObjectId}
     */
    RevTree getTree(ObjectId treeId);

    /**
     * @param path the path to search for
     * @return an {@link Optional} of the {@link Node} for the child, or {@link Optional#absent()}
     *         if it wasn't found
     */
    Optional<Node> getRootTreeChild(String path);

    /**
     * Search the given tree for the child path.
     * 
     * @param tree the tree to search
     * @param childPath the path to search for
     * @return an {@link Optional} of the {@link Node} for the child path, or
     *         {@link Optional#absent()} if it wasn't found
     */
    Optional<Node> getTreeChild(RevTree tree, String childPath);

    /**
     * Gets the depth of the repository, or {@link Optional#absent} if this is not a shallow clone.
     * 
     * @return the depth
     */
    Optional<Integer> getDepth();

    /**
     * @return true if this is a sparse (mapped) clone.
     */
    boolean isSparse();

    Context context();

    // @Override
    WorkingTree workingTree();

    // @Override
    StagingArea index();

    // @Override
    RefDatabase refDatabase();

    // @Override
    Platform platform();

    // @Override
    ObjectDatabase objectDatabase();

    // @Override
    ConflictsDatabase conflictsDatabase();

    // @Override
    ConfigDatabase configDatabase();

    // @Override
    GraphDatabase graphDatabase();

    // @Override
    DeduplicationService deduplicationService();

    BlobStore blobStore();

}