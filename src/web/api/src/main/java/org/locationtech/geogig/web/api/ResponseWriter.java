/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.web.api;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.codehaus.jettison.AbstractXMLStreamWriter;
import org.geotools.metadata.iso.citation.Citations;
import org.geotools.referencing.CRS;
import org.locationtech.geogig.api.Bucket;
import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.FeatureBuilder;
import org.locationtech.geogig.api.FeatureInfo;
import org.locationtech.geogig.api.GeogigSimpleFeature;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.Remote;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureBuilder;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevPerson;
import org.locationtech.geogig.api.RevTag;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.SymRef;
import org.locationtech.geogig.api.plumbing.DiffIndex;
import org.locationtech.geogig.api.plumbing.DiffWorkTree;
import org.locationtech.geogig.api.plumbing.FindTreeChild;
import org.locationtech.geogig.api.plumbing.RevObjectParse;
import org.locationtech.geogig.api.plumbing.diff.AttributeDiff;
import org.locationtech.geogig.api.plumbing.diff.AttributeDiff.TYPE;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry.ChangeType;
import org.locationtech.geogig.api.plumbing.merge.Conflict;
import org.locationtech.geogig.api.plumbing.merge.MergeScenarioReport;
import org.locationtech.geogig.api.porcelain.BlameReport;
import org.locationtech.geogig.api.porcelain.FetchResult;
import org.locationtech.geogig.api.porcelain.FetchResult.ChangedRef;
import org.locationtech.geogig.api.porcelain.MergeOp.MergeReport;
import org.locationtech.geogig.api.porcelain.PullResult;
import org.locationtech.geogig.api.porcelain.ValueAndCommit;
import org.locationtech.geogig.storage.FieldType;
import org.locationtech.geogig.storage.text.CrsTextSerializer;
import org.locationtech.geogig.storage.text.TextValueSerializer;
import org.locationtech.geogig.web.api.commands.BranchWebOp;
import org.locationtech.geogig.web.api.commands.Commit;
import org.locationtech.geogig.web.api.commands.Log.CommitWithChangeCounts;
import org.locationtech.geogig.web.api.commands.LsTree;
import org.locationtech.geogig.web.api.commands.RefParseWeb;
import org.locationtech.geogig.web.api.commands.RemoteWebOp;
import org.locationtech.geogig.web.api.commands.StatisticsWebOp;
import org.locationtech.geogig.web.api.commands.TagWebOp;
import org.locationtech.geogig.web.api.commands.UpdateRefWeb;
import org.opengis.feature.type.GeometryType;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.feature.type.PropertyType;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;

/**
 * Provides a wrapper for writing common GeoGig objects to a provided {@link XMLStreamWriter}.
 */
public class ResponseWriter {

    protected final XMLStreamWriter out;

    /**
     * Constructs a new {code ResponseWriter} with the given {@link XMLStreamWriter}.
     * 
     * @param out the output stream to write to
     */
    public ResponseWriter(XMLStreamWriter out) {
        this.out = out;
        if (out instanceof AbstractXMLStreamWriter) {
            configureJSONOutput((AbstractXMLStreamWriter) out);
        }
    }

    private void configureJSONOutput(AbstractXMLStreamWriter out) {
    }

    /**
     * Ends the document stream.
     * 
     * @throws XMLStreamException
     */
    public void finish() throws XMLStreamException {
        out.writeEndElement(); // results
        out.writeEndDocument();
    }

    /**
     * Begins the document stream.
     * 
     * @throws XMLStreamException
     */
    public void start() throws XMLStreamException {
        start(true);
    }

    /**
     * Begins the document stream with the provided success flag.
     * 
     * @param success whether or not the operation was successful
     * @throws XMLStreamException
     */
    public void start(boolean success) throws XMLStreamException {
        out.writeStartDocument();
        out.writeStartElement("response");
        writeElement("success", Boolean.toString(success));
    }

    /**
     * Writes the given header elements to the stream. The array should be organized into key/value
     * pairs. For example {@code [key, value, key, value]}.
     * 
     * @param els the elements to write
     * @throws XMLStreamException
     */
    public void writeHeaderElements(String... els) throws XMLStreamException {
        out.writeStartElement("header");
        for (int i = 0; i < els.length; i += 2) {
            writeElement(els[i], els[i + 1]);
        }
        out.writeEndElement();
    }

    /**
     * Writes the given error elements to the stream. The array should be organized into key/value
     * pairs. For example {@code [key, value, key, value]}.
     * 
     * @param errors the errors to write
     * @throws XMLStreamException
     */
    public void writeErrors(String... errors) throws XMLStreamException {
        out.writeStartElement("errors");
        for (int i = 0; i < errors.length; i += 2) {
            writeElement(errors[i], errors[i + 1]);
        }
        out.writeEndElement();
    }

    /**
     * @return the {@link XMLStreamWriter} for this instance
     */
    public XMLStreamWriter getWriter() {
        return out;
    }

    /**
     * Writes the given element to the stream.
     * 
     * @param element the element name
     * @param content the element content
     * @throws XMLStreamException
     */
    public void writeElement(String element, @Nullable String content) throws XMLStreamException {
        out.writeStartElement(element);
        if (content != null) {
            out.writeCharacters(content);
        }
        out.writeEndElement();
    }

    /**
     * Writes staged changes to the stream.
     * 
     * @param setFilter the configured {@link DiffIndex} command
     * @param start the change number to start writing from
     * @param length the number of changes to write
     * @throws XMLStreamException
     */
    public void writeStaged(DiffIndex setFilter, int start, int length) throws XMLStreamException {
        writeDiffEntries("staged", start, length, setFilter.call());
    }

    /**
     * Writes unstaged changes to the stream.
     * 
     * @param setFilter the configured {@link DiffWorkTree} command
     * @param start the change number to start writing from
     * @param length the number of changes to write
     * @throws XMLStreamException
     */
    public void writeUnstaged(DiffWorkTree setFilter, int start, int length)
            throws XMLStreamException {
        writeDiffEntries("unstaged", start, length, setFilter.call());
    }

    public void writeUnmerged(List<Conflict> conflicts, int start, int length)
            throws XMLStreamException {
        Iterator<Conflict> entries = conflicts.iterator();

        Iterators.advance(entries, start);
        if (length < 0) {
            length = Integer.MAX_VALUE;
        }
        for (int i = 0; i < length && entries.hasNext(); i++) {
            Conflict entry = entries.next();
            out.writeStartElement("unmerged");
            writeElement("changeType", "CONFLICT");
            writeElement("path", entry.getPath());
            writeElement("ours", entry.getOurs().toString());
            writeElement("theirs", entry.getTheirs().toString());
            writeElement("ancestor", entry.getAncestor().toString());
            out.writeEndElement();
        }
    }

    /**
     * Writes a set of {@link DiffEntry}s to the stream.
     * 
     * @param name the element name
     * @param start the change number to start writing from
     * @param length the number of changes to write
     * @param entries an iterator for the DiffEntries to write
     * @throws XMLStreamException
     */
    public void writeDiffEntries(String name, int start, int length, Iterator<DiffEntry> entries)
            throws XMLStreamException {
        Iterators.advance(entries, start);
        if (length < 0) {
            length = Integer.MAX_VALUE;
        }
        int counter = 0;
        while (entries.hasNext() && counter < length) {
            DiffEntry entry = entries.next();
            out.writeStartElement(name);
            writeElement("changeType", entry.changeType().toString());
            NodeRef oldObject = entry.getOldObject();
            NodeRef newObject = entry.getNewObject();
            if (oldObject == null) {
                writeElement("newPath", newObject.path());
                writeElement("newObjectId", newObject.objectId().toString());
                writeElement("path", "");
                writeElement("oldObjectId", ObjectId.NULL.toString());
            } else if (newObject == null) {
                writeElement("newPath", "");
                writeElement("newObjectId", ObjectId.NULL.toString());
                writeElement("path", oldObject.path());
                writeElement("oldObjectId", oldObject.objectId().toString());
            } else {
                writeElement("newPath", newObject.path());
                writeElement("newObjectId", newObject.objectId().toString());
                writeElement("path", oldObject.path());
                writeElement("oldObjectId", oldObject.objectId().toString());
            }
            out.writeEndElement();
            counter++;
        }
        if (entries.hasNext()) {
            writeElement("nextPage", "true");
        }
    }

    public void writeCommit(RevCommit commit, String tag, @Nullable Integer adds,
            @Nullable Integer modifies, @Nullable Integer removes) throws XMLStreamException {
        out.writeStartElement(tag);
        writeElement("id", commit.getId().toString());
        writeElement("tree", commit.getTreeId().toString());

        ImmutableList<ObjectId> parentIds = commit.getParentIds();
        out.writeStartElement("parents");
        for (ObjectId parentId : parentIds) {
            writeElement("id", parentId.toString());
        }
        out.writeEndElement();

        writePerson("author", commit.getAuthor());
        writePerson("committer", commit.getCommitter());

        if (adds != null) {
            writeElement("adds", adds.toString());
        }
        if (modifies != null) {
            writeElement("modifies", modifies.toString());
        }
        if (removes != null) {
            writeElement("removes", removes.toString());
        }

        out.writeStartElement("message");
        if (commit.getMessage() != null) {
            out.writeCData(commit.getMessage());
        }
        out.writeEndElement();

        out.writeEndElement();
    }

    private void writeNode(Node node, String tag) throws XMLStreamException {
        out.writeStartElement(tag);
        writeElement("name", node.getName());
        writeElement("type", node.getType().name());
        writeElement("objectid", node.getObjectId().toString());
        writeElement("metadataid", node.getMetadataId().or(ObjectId.NULL).toString());
        out.writeEndElement();
    }

    public void writeTree(RevTree tree, String tag) throws XMLStreamException {
        out.writeStartElement(tag);
        writeElement("id", tree.getId().toString());
        writeElement("size", Long.toString(tree.size()));
        writeElement("numtrees", Integer.toString(tree.numTrees()));
        if (tree.trees().isPresent()) {
            ImmutableList<Node> trees = tree.trees().get();
            for (Node ref : trees) {
                writeNode(ref, "tree");
            }
        }
        if (tree.features().isPresent()) {
            ImmutableList<Node> features = tree.features().get();
            for (Node ref : features) {
                writeNode(ref, "feature");
            }
        } else if (tree.buckets().isPresent()) {
            Map<Integer, Bucket> buckets = tree.buckets().get();
            for (Entry<Integer, Bucket> entry : buckets.entrySet()) {
                Integer bucketIndex = entry.getKey();
                Bucket bucket = entry.getValue();
                out.writeStartElement("bucket");
                writeElement("bucketindex", bucketIndex.toString());
                writeElement("bucketid", bucket.id().toString());
                Envelope env = new Envelope();
                env.setToNull();
                bucket.expand(env);
                out.writeStartElement("bbox");
                writeElement("minx", Double.toString(env.getMinX()));
                writeElement("maxx", Double.toString(env.getMaxX()));
                writeElement("miny", Double.toString(env.getMinY()));
                writeElement("maxy", Double.toString(env.getMaxY()));
                out.writeEndElement();
                out.writeEndElement();
            }
        }

        out.writeEndElement();
    }

    public void writeFeature(RevFeature feature, String tag) throws XMLStreamException {
        out.writeStartElement(tag);
        writeElement("id", feature.getId().toString());
        ImmutableList<Optional<Object>> values = feature.getValues();
        for (Optional<Object> opt : values) {
            final FieldType type = FieldType.forValue(opt);
            String valueString = TextValueSerializer.asString(opt);
            out.writeStartElement("attribute");
            writeElement("type", type.toString());
            writeElement("value", valueString);
            out.writeEndElement();
        }

        out.writeEndElement();
    }

    public void writeFeatureType(RevFeatureType featureType, String tag) throws XMLStreamException {
        out.writeStartElement(tag);
        writeElement("id", featureType.getId().toString());
        writeElement("name", featureType.getName().toString());
        ImmutableList<PropertyDescriptor> descriptors = featureType.sortedDescriptors();
        for (PropertyDescriptor descriptor : descriptors) {
            out.writeStartElement("attribute");
            writeElement("name", descriptor.getName().toString());
            writeElement("type", FieldType.forBinding(descriptor.getType().getBinding()).name());
            writeElement("minoccurs", Integer.toString(descriptor.getMinOccurs()));
            writeElement("maxoccurs", Integer.toString(descriptor.getMaxOccurs()));
            writeElement("nillable", Boolean.toString(descriptor.isNillable()));
            PropertyType attrType = descriptor.getType();
            if (attrType instanceof GeometryType) {
                GeometryType gt = (GeometryType) attrType;
                CoordinateReferenceSystem crs = gt.getCoordinateReferenceSystem();
                String crsText = CrsTextSerializer.serialize(crs);
                writeElement("crs", crsText);
            }
            out.writeEndElement();
        }

        out.writeEndElement();
    }

    public void writeTag(RevTag revTag, String tag) throws XMLStreamException {
        out.writeStartElement(tag);
        writeElement("id", revTag.getId().toString());
        writeElement("commitid", revTag.getCommitId().toString());
        writeElement("name", revTag.getName());
        writeElement("message", revTag.getMessage());
        writePerson("tagger", revTag.getTagger());

        out.writeEndElement();
    }

    /**
     * Writes a set of {@link RevCommit}s to the stream.
     * 
     * @param entries an iterator for the RevCommits to write
     * @param elementsPerPage the number of commits per page
     * @param returnRange only return the range if true
     * @throws XMLStreamException
     */
    public void writeCommits(Iterator<RevCommit> entries, int elementsPerPage, boolean returnRange)
            throws XMLStreamException {
        int counter = 0;
        RevCommit lastCommit = null;
        if (returnRange) {
            if (entries.hasNext()) {
                lastCommit = entries.next();
                writeCommit(lastCommit, "untilCommit", null, null, null);
                counter++;
            }
        }
        while (entries.hasNext() && (returnRange || counter < elementsPerPage)) {
            lastCommit = entries.next();

            if (!returnRange) {
                writeCommit(lastCommit, "commit", null, null, null);
            }

            counter++;
        }
        if (returnRange) {
            if (lastCommit != null) {
                writeCommit(lastCommit, "sinceCommit", null, null, null);
            }
            writeElement("numCommits", Integer.toString(counter));
        }
        if (entries.hasNext()) {
            writeElement("nextPage", "true");
        }
    }

    public void writeCommitsWithChangeCounts(Iterator<CommitWithChangeCounts> entries,
            int elementsPerPage) throws XMLStreamException {
        int counter = 0;

        while (entries.hasNext() && counter < elementsPerPage) {
            CommitWithChangeCounts entry = entries.next();

            writeCommit(entry.getCommit(), "commit", entry.getAdds(), entry.getModifies(),
                    entry.getRemoves());

            counter++;
        }

        if (entries.hasNext()) {
            writeElement("nextPage", "true");
        }

    }

    /**
     * Writes a {@link RevPerson} to the stream.
     * 
     * @param enclosingElement the element name
     * @param p the RevPerson to writes
     * @throws XMLStreamException
     */
    public void writePerson(String enclosingElement, RevPerson p) throws XMLStreamException {
        out.writeStartElement(enclosingElement);
        writeElement("name", p.getName().orNull());
        writeElement("email", p.getEmail().orNull());
        writeElement("timestamp", Long.toString(p.getTimestamp()));
        writeElement("timeZoneOffset", Long.toString(p.getTimeZoneOffset()));
        out.writeEndElement();
    }

    /**
     * Writes the response for the {@link Commit} command to the stream.
     * 
     * @param commit the commit
     * @param diff the changes returned from the command
     * @throws XMLStreamException
     */
    public void writeCommitResponse(RevCommit commit, Iterator<DiffEntry> diff)
            throws XMLStreamException {
        int adds = 0, deletes = 0, changes = 0;
        DiffEntry diffEntry;
        while (diff.hasNext()) {
            diffEntry = diff.next();
            switch (diffEntry.changeType()) {
            case ADDED:
                ++adds;
                break;
            case REMOVED:
                ++deletes;
                break;
            case MODIFIED:
                ++changes;
                break;
            }
        }
        writeElement("commitId", commit.getId().toString());
        writeElement("added", Integer.toString(adds));
        writeElement("changed", Integer.toString(changes));
        writeElement("deleted", Integer.toString(deletes));
    }

    /**
     * Writes the response for the {@link LsTree} command to the stream.
     * 
     * @param iter the iterator of {@link NodeRefs}
     * @param verbose if true, more detailed information about each node will be provided
     * @throws XMLStreamException
     */
    public void writeLsTreeResponse(Iterator<NodeRef> iter, boolean verbose)
            throws XMLStreamException {

        while (iter.hasNext()) {
            NodeRef node = iter.next();
            out.writeStartElement("node");
            writeElement("path", node.path());
            if (verbose) {
                writeElement("metadataId", node.getMetadataId().toString());
                writeElement("type", node.getType().toString().toLowerCase());
                writeElement("objectId", node.objectId().toString());
            }
            out.writeEndElement();
        }

    }

    /**
     * Writes the response for the {@link UpdateRefWeb} command to the stream.
     * 
     * @param ref the ref returned from the command
     * @throws XMLStreamException
     */
    public void writeUpdateRefResponse(Ref ref) throws XMLStreamException {
        out.writeStartElement("ChangedRef");
        writeElement("name", ref.getName());
        writeElement("objectId", ref.getObjectId().toString());
        if (ref instanceof SymRef) {
            writeElement("target", ((SymRef) ref).getTarget());
        }
        out.writeEndElement();
    }

    /**
     * Writes the response for the {@link RefParseWeb} command to the stream.
     * 
     * @param ref the ref returned from the command
     * @throws XMLStreamException
     */
    public void writeRefParseResponse(Ref ref) throws XMLStreamException {
        out.writeStartElement("Ref");
        writeElement("name", ref.getName());
        writeElement("objectId", ref.getObjectId().toString());
        if (ref instanceof SymRef) {
            writeElement("target", ((SymRef) ref).getTarget());
        }
        out.writeEndElement();
    }

    /**
     * Writes an empty ref response for when a {@link Ref} was not found.
     * 
     * @throws XMLStreamException
     */
    public void writeEmptyRefResponse() throws XMLStreamException {
        out.writeStartElement("RefNotFound");
        out.writeEndElement();
    }

    /**
     * Writes the response for the {@link BranchWebOp} command to the stream.
     * 
     * @param localBranches the local branches of the repository
     * @param remoteBranches the remote branches of the repository
     * @throws XMLStreamException
     */
    public void writeBranchListResponse(List<Ref> localBranches, List<Ref> remoteBranches)
            throws XMLStreamException {

        out.writeStartElement("Local");
        for (Ref branch : localBranches) {
            out.writeStartElement("Branch");
            writeElement("name", branch.localName());
            out.writeEndElement();
        }
        out.writeEndElement();

        out.writeStartElement("Remote");
        for (Ref branch : remoteBranches) {
            if (!(branch instanceof SymRef)) {
                out.writeStartElement("Branch");
                writeElement("remoteName", branch.namespace().replace(Ref.REMOTES_PREFIX + "/", ""));
                writeElement("name", branch.localName());
                out.writeEndElement();
            }
        }
        out.writeEndElement();

    }

    /**
     * Writes the response for the {@link RemoteWebOp} command to the stream.
     * 
     * @param remotes the list of the {@link Remote}s of this repository
     * @throws XMLStreamException
     */
    public void writeRemoteListResponse(List<Remote> remotes, boolean verbose)
            throws XMLStreamException {
        for (Remote remote : remotes) {
            out.writeStartElement("Remote");
            writeElement("name", remote.getName());
            if (verbose) {
                writeElement("url", remote.getFetchURL());
                if (remote.getUserName() != null) {
                    writeElement("username", remote.getUserName());
                }
            }
            out.writeEndElement();
        }
    }

    /**
     * Writes the response for the {@link RemoteWebOp} command to the stream.
     * 
     * @param success whether or not the ping was successful
     * @throws XMLStreamException
     */
    public void writeRemotePingResponse(boolean success) throws XMLStreamException {
        out.writeStartElement("ping");
        writeElement("success", Boolean.toString(success));
        out.writeEndElement();
    }

    /**
     * Writes the response for the {@link TagWebOp} command to the stream.
     * 
     * @param tags the list of {@link RevTag}s of this repository
     * @throws XMLStreamException
     */
    public void writeTagListResponse(List<RevTag> tags) throws XMLStreamException {
        for (RevTag tag : tags) {
            out.writeStartElement("Tag");
            writeElement("name", tag.getName());
            out.writeEndElement();
        }
    }

    public void writeRebuildGraphResponse(ImmutableList<ObjectId> updatedObjects, boolean quiet)
            throws XMLStreamException {
        out.writeStartElement("RebuildGraph");
        if (updatedObjects.size() > 0) {
            writeElement("updatedGraphElements", Integer.toString(updatedObjects.size()));
            if (!quiet) {
                for (ObjectId object : updatedObjects) {
                    out.writeStartElement("UpdatedObject");
                    writeElement("ref", object.toString());
                    out.writeEndElement();
                }
            }
        } else {
            writeElement("response",
                    "No missing or incomplete graph elements (commits) were found.");
        }
        out.writeEndElement();
    }

    public void writeFetchResponse(FetchResult result) throws XMLStreamException {
        out.writeStartElement("Fetch");
        if (result.getChangedRefs().entrySet().size() > 0) {
            for (Entry<String, List<ChangedRef>> entry : result.getChangedRefs().entrySet()) {
                out.writeStartElement("Remote");
                writeElement("remoteName", entry.getKey());
                for (ChangedRef ref : entry.getValue()) {
                    out.writeStartElement("Branch");

                    writeElement("changeType", ref.getType().toString());
                    if (ref.getOldRef() != null) {
                        writeElement("name", ref.getOldRef().localName());
                        writeElement("oldValue", ref.getOldRef().getObjectId().toString());
                    }
                    if (ref.getNewRef() != null) {
                        if (ref.getOldRef() == null) {
                            writeElement("name", ref.getNewRef().localName());
                        }
                        writeElement("newValue", ref.getNewRef().getObjectId().toString());
                    }
                    out.writeEndElement();
                }
                out.writeEndElement();
            }
        }
        out.writeEndElement();
    }

    public void writePullResponse(PullResult result, Iterator<DiffEntry> iter, Context geogig)
            throws XMLStreamException {
        out.writeStartElement("Pull");
        writeFetchResponse(result.getFetchResult());
        if (iter != null) {
            writeElement("Remote", result.getRemoteName());
            writeElement("Ref", result.getNewRef().localName());
            int added = 0;
            int removed = 0;
            int modified = 0;
            while (iter.hasNext()) {
                DiffEntry entry = iter.next();
                if (entry.changeType() == ChangeType.ADDED) {
                    added++;
                } else if (entry.changeType() == ChangeType.MODIFIED) {
                    modified++;
                } else if (entry.changeType() == ChangeType.REMOVED) {
                    removed++;
                }
            }
            writeElement("Added", Integer.toString(added));
            writeElement("Modified", Integer.toString(modified));
            writeElement("Removed", Integer.toString(removed));
        }
        if (result.getMergeReport().isPresent()
                && result.getMergeReport().get().getReport().isPresent()) {
            MergeReport report = result.getMergeReport().get();
            writeMergeResponse(Optional.fromNullable(report.getMergeCommit()), report.getReport()
                    .get(), geogig, report.getOurs(), report.getPairs().get(0).getTheirs(), report
                    .getPairs().get(0).getAncestor());
        }
        out.writeEndElement();
    }

    /**
     * Writes a set of feature diffs to the stream.
     * 
     * @param diffs a map of {@link PropertyDescriptor} to {@link AttributeDiffs} that specify the
     *        difference between two features
     * @throws XMLStreamException
     */
    public void writeFeatureDiffResponse(Map<PropertyDescriptor, AttributeDiff> diffs)
            throws XMLStreamException {
        Set<Entry<PropertyDescriptor, AttributeDiff>> entries = diffs.entrySet();
        Iterator<Entry<PropertyDescriptor, AttributeDiff>> iter = entries.iterator();
        while (iter.hasNext()) {
            Entry<PropertyDescriptor, AttributeDiff> entry = iter.next();
            out.writeStartElement("diff");
            PropertyType attrType = entry.getKey().getType();
            if (attrType instanceof GeometryType) {
                writeElement("geometry", "true");
                GeometryType gt = (GeometryType) attrType;
                CoordinateReferenceSystem crs = gt.getCoordinateReferenceSystem();
                if (crs != null) {
                    String crsCode = null;
                    try {
                        crsCode = CRS.lookupIdentifier(Citations.EPSG, crs, false);
                    } catch (FactoryException e) {
                        crsCode = null;
                    }
                    if (crsCode != null) {
                        writeElement("crs", "EPSG:" + crsCode);
                    }
                }
            }
            writeElement("attributename", entry.getKey().getName().toString());
            writeElement("changetype", entry.getValue().getType().toString());
            if (entry.getValue().getOldValue() != null
                    && entry.getValue().getOldValue().isPresent()) {
                writeElement("oldvalue", entry.getValue().getOldValue().get().toString());
            }
            if (entry.getValue().getNewValue() != null
                    && entry.getValue().getNewValue().isPresent()
                    && !entry.getValue().getType().equals(TYPE.NO_CHANGE)) {
                writeElement("newvalue", entry.getValue().getNewValue().get().toString());
            }
            out.writeEndElement();
        }
    }

    /**
     * Writes the response for a set of diffs while also supplying the geometry.
     * 
     * @param geogig - a CommandLocator to call commands from
     * @param diff - a DiffEntry iterator to build the response from
     * @throws XMLStreamException
     */
    public void writeGeometryChanges(final Context geogig, Iterator<DiffEntry> diff, int page,
            int elementsPerPage) throws XMLStreamException {

        Iterators.advance(diff, page * elementsPerPage);
        int counter = 0;

        Iterator<GeometryChange> changeIterator = Iterators.transform(diff,
                new Function<DiffEntry, GeometryChange>() {
                    @Override
                    public GeometryChange apply(DiffEntry input) {
                        Optional<RevObject> feature = Optional.absent();
                        Optional<RevObject> type = Optional.absent();
                        String path = null;
                        String crsCode = null;
                        GeometryChange change = null;
                        if (input.changeType() == ChangeType.ADDED
                                || input.changeType() == ChangeType.MODIFIED) {
                            feature = geogig.command(RevObjectParse.class)
                                    .setObjectId(input.newObjectId()).call();
                            type = geogig.command(RevObjectParse.class)
                                    .setObjectId(input.getNewObject().getMetadataId()).call();
                            path = input.getNewObject().path();

                        } else if (input.changeType() == ChangeType.REMOVED) {
                            feature = geogig.command(RevObjectParse.class)
                                    .setObjectId(input.oldObjectId()).call();
                            type = geogig.command(RevObjectParse.class)
                                    .setObjectId(input.getOldObject().getMetadataId()).call();
                            path = input.getOldObject().path();
                        }
                        if (feature.isPresent() && feature.get() instanceof RevFeature
                                && type.isPresent() && type.get() instanceof RevFeatureType) {
                            RevFeatureType featureType = (RevFeatureType) type.get();
                            Collection<PropertyDescriptor> attribs = featureType.type()
                                    .getDescriptors();

                            for (PropertyDescriptor attrib : attribs) {
                                PropertyType attrType = attrib.getType();
                                if (attrType instanceof GeometryType) {
                                    GeometryType gt = (GeometryType) attrType;
                                    CoordinateReferenceSystem crs = gt
                                            .getCoordinateReferenceSystem();
                                    if (crs != null) {
                                        try {
                                            crsCode = CRS.lookupIdentifier(Citations.EPSG, crs,
                                                    false);
                                        } catch (FactoryException e) {
                                            crsCode = null;
                                        }
                                        if (crsCode != null) {
                                            crsCode = "EPSG:" + crsCode;
                                        }
                                    }
                                    break;
                                }
                            }

                            RevFeature revFeature = (RevFeature) feature.get();
                            FeatureBuilder builder = new FeatureBuilder(featureType);
                            GeogigSimpleFeature simpleFeature = (GeogigSimpleFeature) builder
                                    .build(revFeature.getId().toString(), revFeature);
                            change = new GeometryChange(simpleFeature, input.changeType(), path,
                                    crsCode);
                        }
                        return change;
                    }
                });

        while (changeIterator.hasNext() && (elementsPerPage == 0 || counter < elementsPerPage)) {
            GeometryChange next = changeIterator.next();
            if (next != null) {
                GeogigSimpleFeature feature = next.getFeature();
                ChangeType change = next.getChangeType();
                out.writeStartElement("Feature");
                writeElement("change", change.toString());
                writeElement("id", next.getPath());
                List<Object> attributes = feature.getAttributes();
                for (Object attribute : attributes) {
                    if (attribute instanceof Geometry) {
                        writeElement("geometry", ((Geometry) attribute).toText());
                        break;
                    }
                }
                if (next.getCRS() != null) {
                    writeElement("crs", next.getCRS());
                }
                out.writeEndElement();
                counter++;
            }
        }
        if (changeIterator.hasNext()) {
            writeElement("nextPage", "true");
        }
    }

    /**
     * Writes the response for a set of conflicts while also supplying the geometry.
     * 
     * @param geogig - a CommandLocator to call commands from
     * @param conflicts - a Conflict iterator to build the response from
     * @throws XMLStreamException
     */
    public void writeConflicts(final Context geogig, Iterator<Conflict> conflicts,
            final ObjectId ours, final ObjectId theirs) throws XMLStreamException {
        Iterator<GeometryConflict> conflictIterator = Iterators.transform(conflicts,
                new Function<Conflict, GeometryConflict>() {
                    @Override
                    public GeometryConflict apply(Conflict input) {
                        ObjectId commitId = ours;
                        if (input.getOurs().equals(ObjectId.NULL)) {
                            commitId = theirs;
                        }
                        Optional<RevObject> object = geogig.command(RevObjectParse.class)
                                .setObjectId(commitId).call();
                        RevCommit commit = null;
                        if (object.isPresent() && object.get() instanceof RevCommit) {
                            commit = (RevCommit) object.get();
                        } else {
                            throw new CommandSpecException("Couldn't resolve id: "
                                    + commitId.toString() + " to a commit");
                        }

                        object = geogig.command(RevObjectParse.class)
                                .setObjectId(commit.getTreeId()).call();
                        Optional<NodeRef> node = Optional.absent();
                        if (object.isPresent()) {
                            RevTree tree = (RevTree) object.get();
                            node = geogig.command(FindTreeChild.class).setParent(tree)
                                    .setChildPath(input.getPath()).call();
                        } else {
                            throw new CommandSpecException("Couldn't resolve commit's treeId");
                        }

                        RevFeatureType type = null;
                        RevFeature feature = null;

                        if (node.isPresent()) {
                            object = geogig.command(RevObjectParse.class)
                                    .setObjectId(node.get().getMetadataId()).call();
                            if (object.isPresent() && object.get() instanceof RevFeatureType) {
                                type = (RevFeatureType) object.get();
                            } else {
                                throw new CommandSpecException(
                                        "Couldn't resolve newCommit's featureType");
                            }
                            object = geogig.command(RevObjectParse.class)
                                    .setObjectId(node.get().objectId()).call();
                            if (object.isPresent() && object.get() instanceof RevFeature) {
                                feature = (RevFeature) object.get();
                            } else {
                                throw new CommandSpecException(
                                        "Couldn't resolve newCommit's feature");
                            }
                        }

                        GeometryConflict conflict = null;

                        if (feature != null && type != null) {
                            String crsCode = null;
                            Collection<PropertyDescriptor> attribs = type.type().getDescriptors();

                            for (PropertyDescriptor attrib : attribs) {
                                PropertyType attrType = attrib.getType();
                                if (attrType instanceof GeometryType) {
                                    GeometryType gt = (GeometryType) attrType;
                                    CoordinateReferenceSystem crs = gt
                                            .getCoordinateReferenceSystem();

                                    if (crs != null) {
                                        try {
                                            crsCode = CRS.lookupIdentifier(Citations.EPSG, crs,
                                                    false);
                                        } catch (FactoryException e) {
                                            crsCode = null;
                                        }
                                        if (crsCode != null) {
                                            crsCode = "EPSG:" + crsCode;
                                        }
                                    }
                                    break;
                                }
                            }

                            FeatureBuilder builder = new FeatureBuilder(type);
                            GeogigSimpleFeature simpleFeature = (GeogigSimpleFeature) builder
                                    .build(feature.getId().toString(), feature);
                            Geometry geom = null;
                            List<Object> attributes = simpleFeature.getAttributes();
                            for (Object attribute : attributes) {
                                if (attribute instanceof Geometry) {
                                    geom = (Geometry) attribute;
                                    break;
                                }
                            }
                            conflict = new GeometryConflict(input, geom, crsCode);
                        }
                        return conflict;
                    }
                });

        while (conflictIterator.hasNext()) {
            GeometryConflict next = conflictIterator.next();
            if (next != null) {
                out.writeStartElement("Feature");
                writeElement("change", "CONFLICT");
                writeElement("id", next.getConflict().getPath());
                writeElement("ourvalue", next.getConflict().getOurs().toString());
                writeElement("theirvalue", next.getConflict().getTheirs().toString());
                writeElement("geometry", next.getGeometry().toText());
                if (next.getCRS() != null) {
                    writeElement("crs", next.getCRS());
                }
                out.writeEndElement();
            }
        }
    }

    /**
     * Writes the response for a set of merged features while also supplying the geometry.
     * 
     * @param geogig - a CommandLocator to call commands from
     * @param features - a FeatureInfo iterator to build the response from
     * @throws XMLStreamException
     */
    public void writeMerged(final Context geogig, Iterator<FeatureInfo> features)
            throws XMLStreamException {
        Iterator<GeometryChange> changeIterator = Iterators.transform(features,
                new Function<FeatureInfo, GeometryChange>() {
                    @Override
                    public GeometryChange apply(FeatureInfo input) {
                        GeometryChange change = null;
                        RevFeature revFeature = RevFeatureBuilder.build(input.getFeature());
                        RevFeatureType featureType = input.getFeatureType();
                        Collection<PropertyDescriptor> attribs = featureType.type()
                                .getDescriptors();
                        String crsCode = null;

                        for (PropertyDescriptor attrib : attribs) {
                            PropertyType attrType = attrib.getType();
                            if (attrType instanceof GeometryType) {
                                GeometryType gt = (GeometryType) attrType;
                                CoordinateReferenceSystem crs = gt.getCoordinateReferenceSystem();
                                if (crs != null) {
                                    try {
                                        crsCode = CRS.lookupIdentifier(Citations.EPSG, crs, false);
                                    } catch (FactoryException e) {
                                        crsCode = null;
                                    }
                                    if (crsCode != null) {
                                        crsCode = "EPSG:" + crsCode;
                                    }
                                }
                                break;
                            }
                        }

                        FeatureBuilder builder = new FeatureBuilder(featureType);
                        GeogigSimpleFeature simpleFeature = (GeogigSimpleFeature) builder.build(
                                revFeature.getId().toString(), revFeature);
                        change = new GeometryChange(simpleFeature, ChangeType.MODIFIED, input
                                .getPath(), crsCode);
                        return change;
                    }
                });

        while (changeIterator.hasNext()) {
            GeometryChange next = changeIterator.next();
            if (next != null) {
                GeogigSimpleFeature feature = next.getFeature();
                out.writeStartElement("Feature");
                writeElement("change", "MERGED");
                writeElement("id", next.getPath());
                List<Object> attributes = feature.getAttributes();
                for (Object attribute : attributes) {
                    if (attribute instanceof Geometry) {
                        writeElement("geometry", ((Geometry) attribute).toText());
                        break;
                    }
                }
                if (next.getCRS() != null) {
                    writeElement("crs", next.getCRS());
                }
                out.writeEndElement();
            }
        }
    }

    /**
     * Writes the response for a merge dry-run, contains unconflicted, conflicted and merged
     * features.
     * 
     * @param report - the MergeScenarioReport containing all the merge results
     * @param transaction - a transaction aware injector to call commands from
     * @throws XMLStreamException
     */
    public void writeMergeResponse(Optional<RevCommit> mergeCommit, MergeScenarioReport report,
            Context transaction, ObjectId ours, ObjectId theirs, ObjectId ancestor)
            throws XMLStreamException {
        out.writeStartElement("Merge");
        writeElement("ours", ours.toString());
        writeElement("theirs", theirs.toString());
        writeElement("ancestor", ancestor.toString());
        if (mergeCommit.isPresent()) {
            writeElement("mergedCommit", mergeCommit.get().getId().toString());
        }
        if (report.getConflicts().size() > 0) {
            writeElement("conflicts", Integer.toString(report.getConflicts().size()));
        }
        writeGeometryChanges(transaction, report.getUnconflicted().iterator(), 0, 0);
        writeConflicts(transaction, report.getConflicts().iterator(), ours, theirs);
        writeMerged(transaction, report.getMerged().iterator());
        out.writeEndElement();
    }

    /**
     * Writes the id of the transaction created or nothing if it was ended successfully.
     * 
     * @param transactionId - the id of the transaction or null if the transaction was closed
     *        successfully
     * @throws XMLStreamException
     */
    public void writeTransactionId(UUID transactionId) throws XMLStreamException {
        out.writeStartElement("Transaction");
        if (transactionId != null) {
            writeElement("ID", transactionId.toString());
        }
        out.writeEndElement();
    }

    /**
     * Writes the response for the blame operation.
     * 
     * @param report - the result of the blame operation
     * @throws XMLStreamException
     */
    public void writeBlameReport(BlameReport report) throws XMLStreamException {
        out.writeStartElement("Blame");
        Map<String, ValueAndCommit> changes = report.getChanges();
        Iterator<String> iter = changes.keySet().iterator();
        while (iter.hasNext()) {
            String attrib = iter.next();
            ValueAndCommit valueAndCommit = changes.get(attrib);
            RevCommit commit = valueAndCommit.commit;
            Optional<?> value = valueAndCommit.value;
            out.writeStartElement("Attribute");
            writeElement("name", attrib);
            writeElement("value",
                    TextValueSerializer.asString(Optional.fromNullable((Object) value.orNull())));
            writeCommit(commit, "commit", null, null, null);
            out.writeEndElement();
        }
        out.writeEndElement();
    }

    public void writeStatistics(List<StatisticsWebOp.FeatureTypeStats> stats,
            RevCommit firstCommit, RevCommit lastCommit, int totalCommits, List<RevPerson> authors,
            int totalAdded, int totalModified, int totalRemoved) throws XMLStreamException {
        out.writeStartElement("Statistics");
        int numFeatureTypes = 0;
        int totalNumFeatures = 0;
        if (!stats.isEmpty()) {
            out.writeStartElement("FeatureTypes");
            for (StatisticsWebOp.FeatureTypeStats stat : stats) {
                numFeatureTypes++;
                out.writeStartElement("FeatureType");
                writeElement("name", stat.getName());
                writeElement("numFeatures", Long.toString(stat.getNumFeatures()));
                totalNumFeatures += stat.getNumFeatures();
                out.writeEndElement();
            }
            if (numFeatureTypes > 1) {
                writeElement("totalFeatureTypes", Integer.toString(numFeatureTypes));
                writeElement("totalFeatures", Integer.toString(totalNumFeatures));
            }
            out.writeEndElement();
        }
        if (lastCommit != null) {
            writeCommit(lastCommit, "latestCommit", null, null, null);
        }
        if (firstCommit != null) {
            writeCommit(firstCommit, "firstCommit", null, null, null);
        }
        if (totalCommits > 0) {
            writeElement("totalCommits", Integer.toString(totalCommits));
        }
        if (totalAdded > 0) {
            writeElement("totalAdded", Integer.toString(totalAdded));
        }
        if (totalRemoved > 0) {
            writeElement("totalRemoved", Integer.toString(totalRemoved));
        }
        if (totalModified > 0) {
            writeElement("totalModified", Integer.toString(totalModified));
        }
        {
            out.writeStartElement("Authors");

            for (RevPerson author : authors) {
                if (author.getName().isPresent() || author.getEmail().isPresent()) {
                    out.writeStartElement("Author");
                    if (author.getName().isPresent()) {
                        writeElement("name", author.getName().get());
                    }
                    if (author.getEmail().isPresent()) {
                        writeElement("email", author.getEmail().get());
                    }
                    out.writeEndElement();
                }
            }

            writeElement("totalAuthors", Integer.toString(authors.size()));
            out.writeEndElement();
        }
        out.writeEndElement();

    }

    private class GeometryChange {
        private GeogigSimpleFeature feature;

        private ChangeType changeType;

        private String path;

        private String crs;

        public GeometryChange(GeogigSimpleFeature feature, ChangeType changeType, String path,
                String crs) {
            this.feature = feature;
            this.changeType = changeType;
            this.path = path;
            this.crs = crs;
        }

        public GeogigSimpleFeature getFeature() {
            return feature;
        }

        public ChangeType getChangeType() {
            return changeType;
        }

        public String getPath() {
            return path;
        }

        public String getCRS() {
            return crs;
        }
    }

    private class GeometryConflict {
        private Conflict conflict;

        private Geometry geom;

        private String crs;

        public GeometryConflict(Conflict conflict, Geometry geom, String crs) {
            this.conflict = conflict;
            this.geom = geom;
            this.crs = crs;
        }

        public Conflict getConflict() {
            return conflict;
        }

        public Geometry getGeometry() {
            return geom;
        }

        public String getCRS() {
            return crs;
        }
    }
}
