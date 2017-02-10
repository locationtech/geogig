/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.data;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.logging.Level;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.data.DataStore;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.store.ContentDataStore;
import org.geotools.data.store.ContentEntry;
import org.geotools.data.store.ContentState;
import org.geotools.feature.NameImpl;
import org.locationtech.geogig.data.FindFeatureTypeTrees;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.RevParse;
import org.locationtech.geogig.plumbing.TransactionBegin;
import org.locationtech.geogig.porcelain.AddOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.index.CreateQuadTree;
import org.locationtech.geogig.porcelain.index.Index;
import org.locationtech.geogig.porcelain.index.UpdateIndexOp;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.repository.impl.GeogigTransaction;
import org.locationtech.geogig.storage.IndexDatabase;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.Name;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * A GeoTools {@link DataStore} that serves and edits {@link SimpleFeature}s in a geogig repository.
 * <p>
 * Multiple instances of this kind of data store may be created against the same repository,
 * possibly working against different {@link #setHead(String) heads}.
 * <p>
 * A head is any commit in GeoGig. If a head has a branch pointing at it then the store allows
 * transactions, otherwise no data modifications may be made.
 * 
 * A branch in Geogig is a separate line of history that may or may not share a common ancestor with
 * another branch. In the later case the branch is called "orphan" and by convention the default
 * branch is called "master", which is created when the geogig repo is first initialized, but does
 * not necessarily exist.
 * <p>
 * Every read operation (like in {@link #getFeatureSource(Name)}) reads committed features out of
 * the configured "head" branch. Write operations are only supported if a {@link Transaction} is
 * set, in which case a {@link GeogigTransaction} is tied to the geotools transaction by means of a
 * {@link GeogigTransactionState}. During the transaction, read operations will read from the
 * transaction's {@link WorkingTree} in order to "see" features that haven't been committed yet.
 * <p>
 * When the transaction is committed, the changes made inside that transaction are merged onto the
 * actual repository. If any other change was made to the repository meanwhile, a rebase will be
 * attempted, and the transaction commit will fail if the rebase operation finds any conflict. This
 * provides for optimistic locking and reduces thread contention.
 * 
 */
public class GeoGigDataStore extends ContentDataStore implements DataStore {

    private final Repository repository;

    private final Context _liveContext;

    /** @see #setHead(String) */
    private String refspec;

    /** When the configured head is not a branch, we disallow transactions */
    private boolean allowTransactions = true;

    /** Indicates if layers from this datastore should automatically index time/elevation dimension attributes **/
    private boolean autoIndexing;

    public GeoGigDataStore(Repository repository) {
        super();
        Preconditions.checkNotNull(repository);

        this.repository = repository;
        this._liveContext = repository.context();
    }

    @Override
    public void dispose() {
        super.dispose();
        repository.close();
    }

    /**
     * Instructs the datastore to operate against the specified refspec, or against the checked out
     * branch, whatever it is, if the argument is {@code null}.
     * 
     * Editing capabilities are disabled if the refspec is not a local branch.
     * 
     * @param refspec the name of the branch to work against, or {@code null} to default to the
     *        currently checked out branch
     * @see #getConfiguredBranch()
     * @see #getOrFigureOutHead()
     * @throws IllegalArgumentException if {@code refspec} is not null and no such commit exists in
     *         the repository
     */
    public void setHead(@Nullable final String refspec) throws IllegalArgumentException {
        if (refspec == null) {
            allowTransactions = true; // when no branch name is set we assume we should make
            // transactions against the current HEAD
        } else {
            final Context context = resolveContext(null);
            Optional<ObjectId> rev = context.command(RevParse.class).setRefSpec(refspec).call();
            if (!rev.isPresent()) {
                throw new IllegalArgumentException("Bad ref spec: " + refspec);
            }
            Optional<Ref> branchRef = context.command(RefParse.class).setName(refspec).call();
            if (branchRef.isPresent()) {
                Ref ref = branchRef.get();
                if (ref instanceof SymRef) {
                    ref = context.command(RefParse.class).setName(((SymRef) ref).getTarget()).call()
                            .orNull();
                }
                Preconditions.checkArgument(ref != null, "refSpec is a dead symref: " + refspec);
                if (ref.getName().startsWith(Ref.HEADS_PREFIX)) {
                    allowTransactions = true;
                } else {
                    allowTransactions = false;
                }
            } else {
                allowTransactions = false;
            }
        }
        this.refspec = refspec;
    }

    public String getOrFigureOutHead() {
        String branch = getConfiguredHead();
        if (branch != null) {
            return branch;
        }
        return getCheckedOutBranch();
    }

    /**
     * @return the configured refspec of the commit this datastore works against, or {@code null} if
     *         no head in particular has been set, meaning the data store works against whatever the
     *         currently checked out branch is.
     */
    @Nullable
    public String getConfiguredHead() {
        return this.refspec;
    }

    /**
     * @return whether or not we can support transactions against the configured head
     */
    public boolean isAllowTransactions() {
        return this.allowTransactions;
    }

    /**
     * @return the name of the currently checked out branch in the repository, not necessarily equal
     *         to {@link #getConfiguredBranch()}, or {@code null} in the (improbable) case HEAD is
     *         on a dettached state (i.e. no local branch is currently checked out)
     */
    @Nullable
    public String getCheckedOutBranch() {
        Optional<Ref> head = resolveContext(null).command(RefParse.class).setName(Ref.HEAD).call();
        if (!head.isPresent()) {
            return null;
        }
        Ref headRef = head.get();
        if (!(headRef instanceof SymRef)) {
            return null;
        }
        String refName = ((SymRef) headRef).getTarget();
        Preconditions.checkState(refName.startsWith(Ref.HEADS_PREFIX));
        String branchName = refName.substring(Ref.HEADS_PREFIX.length());
        return branchName;
    }

    public Context resolveContext(@Nullable Transaction transaction) {
        Context context = null;

        if (transaction != null && !Transaction.AUTO_COMMIT.equals(transaction)) {
            GeogigTransactionState state;
            state = (GeogigTransactionState) transaction.getState(GeogigTransactionState.class);
            Optional<GeogigTransaction> geogigTransaction = state.getGeogigTransaction();
            if (geogigTransaction.isPresent()) {
                context = geogigTransaction.get();
            } else {
                context = this._liveContext;
            }
        }

        if (context == null) {
            context = this._liveContext.snapshot();
        }
        return context;
    }

    public Name getDescriptorName(NodeRef treeRef) {
        Preconditions.checkNotNull(treeRef);
        Preconditions.checkArgument(TYPE.TREE.equals(treeRef.getType()));
        Preconditions.checkArgument(!treeRef.getMetadataId().isNull(),
                "NodeRef '%s' is not a feature type reference", treeRef.path());

        return new NameImpl(getNamespaceURI(), NodeRef.nodeFromPath(treeRef.path()));
    }

    public NodeRef findTypeRef(Name typeName, @Nullable Transaction tx) {
        Preconditions.checkNotNull(typeName);

        final String localName = typeName.getLocalPart();
        List<NodeRef> typeRefs = findTypeRefs(tx, false);
        NodeRef typeRef = findTypeRef(typeRefs, localName);
        if (typeRef == null) {
            typeRefs = findTypeRefs(tx, true);
            typeRef = findTypeRef(typeRefs, localName);
        }
        if (typeRef == null) {
            throw new NoSuchElementException(
                    String.format("No tree ref matched the name: %s", localName));
        }
        return typeRef;
    }

    private @Nullable NodeRef findTypeRef(List<NodeRef> typeRefs, String localName) {
        java.util.Optional<NodeRef> match = typeRefs.stream()
                .filter((r) -> NodeRef.nodeFromPath(r.path()).equals(localName)).findFirst();

        return match.orElse(null);
    }

    @Override
    protected ContentState createContentState(ContentEntry entry) {
        return new ContentState(entry);
    }

    @Override
    protected ImmutableList<Name> createTypeNames() throws IOException {
        List<NodeRef> typeTrees = findTypeRefs(Transaction.AUTO_COMMIT, true);
        return ImmutableList
                .copyOf(Collections2.transform(typeTrees, (ref) -> getDescriptorName(ref)));
    }

    /**
     * Cache type refs by repository head ref
     */
    private Map<String, List<NodeRef>> typeRefsByHead = new HashMap<>();

    private List<NodeRef> findTypeRefs(@Nullable Transaction tx, boolean forceReload) {
        List<NodeRef> typeTrees = null;

        final String rootRef = getRootRef(tx);
        if (!forceReload) {
            typeTrees = typeRefsByHead.get(rootRef);
        }
        if (typeTrees == null) {
            Context commandLocator = resolveContext(tx);
            typeTrees = commandLocator.command(FindFeatureTypeTrees.class).setRootTreeRef(rootRef)
                    .call();
            synchronized (typeRefsByHead) {
                typeRefsByHead.put(rootRef, typeTrees);
            }
        }
        return typeTrees;
    }

    String getRootRef(@Nullable Transaction tx) {
        final String rootRef;
        if (null == tx || Transaction.AUTO_COMMIT.equals(tx)) {
            rootRef = getOrFigureOutHead();
        } else {
            rootRef = Ref.WORK_HEAD;
        }
        return rootRef;
    }

    @Override
    protected GeogigFeatureStore createFeatureSource(ContentEntry entry) throws IOException {
        return new GeogigFeatureStore(entry);
    }

    /**
     * Creates a new feature type tree on the {@link #getOrFigureOutHead() current branch}.
     * <p>
     * Implementation detail: the operation is the homologous to starting a transaction, checking
     * out the current/configured branch, creating the type tree inside the transaction, issueing a
     * geogig commit, and committing the transaction for the created tree to be merged onto the
     * configured branch.
     */
    @Override
    public void createSchema(SimpleFeatureType featureType) throws IOException {
        if (!allowTransactions) {
            throw new IllegalStateException("Configured head " + refspec
                    + " is not a branch; transactions are not supported.");
        }
        GeogigTransaction tx = resolveContext(null).command(TransactionBegin.class).call();
        boolean abort = false;
        try {
            String treePath = featureType.getName().getLocalPart();
            // check out the datastore branch on the transaction space
            final String branch = getOrFigureOutHead();
            tx.command(CheckoutOp.class).setForce(true).setSource(branch).call();
            // now we can use the transaction working tree with the correct branch checked out
            WorkingTree workingTree = tx.workingTree();
            workingTree.createTypeTree(treePath, featureType);
            tx.command(AddOp.class).addPattern(treePath).call();
            tx.command(CommitOp.class).setMessage("Created feature type tree " + treePath).call();
            tx.commit();
        } catch (IllegalArgumentException alreadyExists) {
            abort = true;
            throw new IOException(alreadyExists.getMessage(), alreadyExists);
        } catch (Exception e) {
            abort = true;
            throw Throwables.propagate(e);
        } finally {
            if (abort) {
                tx.abort();
            }
        }
    }

    // Deliberately leaving the @Override annotation commented out so that the class builds
    // both against GeoTools 10.x and 11.x (as the method was added to DataStore in 11.x)
    // @Override
    public void removeSchema(Name name) throws IOException {
        throw new UnsupportedOperationException(
                "removeSchema not yet supported by geogig DataStore");
    }

    // Deliberately leaving the @Override annotation commented out so that the class builds
    // both against GeoTools 10.x and 11.x (as the method was added to DataStore in 11.x)
    // @Override
    public void removeSchema(String name) throws IOException {
        throw new UnsupportedOperationException(
                "removeSchema not yet supported by geogig DataStore");
    }

    public static enum ChangeType {
        ADDED, REMOVED, CHANGED_NEW, CHANGED_OLD;
    }

    /**
     * Builds a FeatureSource (read-only) that fetches features out of the differences between two
     * root trees: a provided tree-ish as the left side of the comparison, and the datastore's
     * configured HEAD as the right side of the comparison.
     * <p>
     * E.g., to get all features of a given feature type that were removed between a given commit
     * and its parent:
     * 
     * <pre>
     * <code>
     * String commitId = ...
     * GeoGigDataStore store = new GeoGigDataStore(geogig);
     * store.setHead(commitId);
     * FeatureSource removed = store.getDiffFeatureSource("roads", commitId + "~1", ChangeType.REMOVED);
     * </code>
     * </pre>
     * 
     * @param typeName the feature type name to look up a type tree for in the datastore's current
     *        {@link #getOrFigureOutHead() HEAD}
     * @param oldRoot a tree-ish string that resolves to the ROOT tree to be used as the left side
     *        of the diff
     * @param changeType the type of change between {@code oldRoot} and {@link #getOrFigureOutHead()
     *        head} to pick as the features to return.
     * @return a feature source whose features are computed out of the diff between the feature type
     *         diffs between the given {@code oldRoot} and the datastore's
     *         {@link #getOrFigureOutHead() HEAD}.
     */
    public SimpleFeatureSource getDiffFeatureSource(final String typeName, final String oldRoot,
            final ChangeType changeType) throws IOException {
        Preconditions.checkNotNull(typeName, "typeName");
        Preconditions.checkNotNull(oldRoot, "oldRoot");
        Preconditions.checkNotNull(changeType, "changeType");

        final Name name = name(typeName);
        final ContentEntry entry = ensureEntry(name);

        GeogigFeatureSource featureSource = new GeogigFeatureSource(entry);
        featureSource.setTransaction(Transaction.AUTO_COMMIT);
        featureSource.setChangeType(changeType);
        if (ObjectId.NULL.toString().equals(oldRoot)
                || RevTree.EMPTY_TREE_ID.toString().equals(oldRoot)) {
            featureSource.setOldRoot(null);
        } else {
            featureSource.setOldRoot(oldRoot);
        }

        return featureSource;
    }

    /**
     * Creates or updates an existing index for a given path/layer in the GeoGig repository/branch associated with this
     * DataStore. If an index does not already exist, a spatial index using the default geometry attribute is created.
     * If the provided list of extra attributes isn't empty, they will be added to the index. If an index for the
     * provided path/layer already exists, it will be updated if any extra attributes are provided AND they are not
     * already in the index. If no extra attributes are provided, or the index already contains all extra attributes
     * provided, no the index will not be updated. If an index is created or updated, it will also index the entire
     * history of the feature.
     *
     * @param layerName       The name of the path/layer for which to create an index. Must not be null.
     * @param extraAttributes Optional list of attribute names to include in the extra attributes of the index.
     *
     * @return An Optional containing the ObjectId of the IndexInfo created/updated, or Absent if no index was created
     *         or updated.
     */
    public Optional<ObjectId> createOrUpdateIndex(String layerName, String... extraAttributes) {
        Preconditions.checkNotNull(layerName, "Layer name must not be null");
        // list of non-null attributes provided
        final List<String> indexAttributes = Lists.newArrayList();
        if (extraAttributes != null && extraAttributes.length > 0) {
            // add any non-null attributes to the list to be indexed
            for (String attr : extraAttributes) {
                if (attr != null) {
                    indexAttributes.add(attr);
                }
            }
        }
        // see if there are any attributes to index
        if (indexAttributes.isEmpty()) {
            // no extra attributes specified
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(String.format("No attributes provided for indexing, layer: %s", layerName));
            }
        }
        // we have work to do
        // see if an index is already present for the specified layer name and attribute(s)
        final IndexDatabase indexDatabase = repository.indexDatabase();
        final List<IndexInfo> indexInfos = indexDatabase.getIndexInfos(layerName);

        Context context = resolveContext(Transaction.AUTO_COMMIT);
        for (IndexInfo indexInfo : indexInfos) {
            // get any existing attributes that are already part of the index
            final Set<String> materializedAttributeNames = IndexInfo.getMaterializedAttributeNames(indexInfo);
            // if the materialized attributes already contain the extra attributes provided, we are done
            if (materializedAttributeNames.containsAll(indexAttributes)) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine(String.format("Index already contains attributes: %s",
                            String.join(", ", indexAttributes)));
                }
                return Optional.of(indexInfo.getId());
            }
            // index doesn't conatin all the extra attributes provided, let's update the index
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(String.format("Updating index to include attributes: %s",
                        String.join(", ", indexAttributes)));
            }
            UpdateIndexOp command = context.command(UpdateIndexOp.class);
            // set ADD to true as we don't want to clobber existing attributes in the extra data
            Index index = command.setAdd(true)
                    // set the extra attributes
                    .setExtraAttributes(indexAttributes)
                    // set the layer/path
                    .setTreeRefSpec(indexInfo.getTreeName())
                    // index the histroy as well
                    .setIndexHistory(true)
                    .call();
            return Optional.of(index.info().getId());
        }
        // no index found for the layer/path, create one
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine(String.format("No Indexes found for layer %s, creating new index with extra attributes: %s.",
                    layerName, String.join("' ", indexAttributes)));
        }
        CreateQuadTree command = context.command(CreateQuadTree.class);
        Index index = command.setTreeRefSpec(layerName)
                .setExtraAttributes(indexAttributes)
                .setIndexHistory(true)
                .call();
        return Optional.of(index.info().getId());
    }

    public void setAutoIndexing(boolean autoIndexing) {
        this.autoIndexing = autoIndexing;
    }

    public boolean getAutoIndexing() {
        return this.autoIndexing;
    }
}
