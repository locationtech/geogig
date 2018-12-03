/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 * Erik Merkle (Boundless) - Jettison to JSR-353 conversion
 */
package org.locationtech.geogig.web.api;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import javax.xml.stream.XMLStreamWriter;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.metadata.iso.citation.Citations;
import org.geotools.referencing.CRS;
import org.locationtech.geogig.data.FeatureBuilder;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.DiffEntry.ChangeType;
import org.locationtech.geogig.model.FieldType;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevPerson;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.DiffIndex;
import org.locationtech.geogig.plumbing.DiffWorkTree;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.plumbing.diff.AttributeDiff;
import org.locationtech.geogig.plumbing.diff.AttributeDiff.TYPE;
import org.locationtech.geogig.plumbing.merge.MergeScenarioReport;
import org.locationtech.geogig.porcelain.BlameReport;
import org.locationtech.geogig.porcelain.MergeOp.MergeReport;
import org.locationtech.geogig.porcelain.ValueAndCommit;
import org.locationtech.geogig.remotes.PullResult;
import org.locationtech.geogig.remotes.RefDiff;
import org.locationtech.geogig.remotes.TransferSummary;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.FeatureInfo;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.storage.text.CrsTextSerializer;
import org.locationtech.geogig.storage.text.TextValueSerializer;
import org.locationtech.geogig.web.api.commands.Branch;
import org.locationtech.geogig.web.api.commands.Commit;
import org.locationtech.geogig.web.api.commands.Log.CommitWithChangeCounts;
import org.locationtech.geogig.web.api.commands.LsTree;
import org.locationtech.geogig.web.api.commands.RefParse;
import org.locationtech.geogig.web.api.commands.RemoteManagement;
import org.locationtech.geogig.web.api.commands.Statistics;
import org.locationtech.geogig.web.api.commands.Tag;
import org.locationtech.geogig.web.api.commands.UpdateRef;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.type.GeometryType;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.feature.type.PropertyType;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.springframework.http.MediaType;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;

/**
 * Provides a wrapper for writing common GeoGig objects to a provided {@link XMLStreamWriter}.
 */
public class ResponseWriter {

    protected final StreamingWriter out;

    private final MediaType format;

    /**
     * Constructs a new {code ResponseWriter} with the given {@link XMLStreamWriter}.
     * 
     * @param out the output stream to write to
     * @param format the output format
     */
    public ResponseWriter(StreamingWriter out, MediaType format) {
        this.out = out;
        this.format = format;
    }

    public void encodeAlternateAtomLink(String baseURL, String link) throws StreamWriterException {
        String href = RESTUtils.buildHref(baseURL, link, format);
        RESTUtils.encodeAlternateAtomLink(format, out, href);
    }

    /**
     * Ends the document stream.
     * 
     * @throws StreamWriterException
     */
    public void finish() throws StreamWriterException {
        out.writeEndElement(); // results
    }

    /**
     * Begins the document stream.
     * 
     * @throws StreamWriterException
     */
    public void start() throws StreamWriterException {
        start(true);
    }

    /**
     * Begins the document stream with the provided success flag.
     * 
     * @param success whether or not the operation was successful
     * @throws StreamWriterException
     */
    public void start(boolean success) throws StreamWriterException {
        out.writeStartElement("response");
        writeElement("success", Boolean.toString(success));
    }

    /**
     * Writes the given header elements to the stream. The array should be organized into key/value
     * pairs. For example {@code [key, value, key, value]}.
     * 
     * @param els the elements to write
     * @throws StreamWriterException
     */
    public void writeHeaderElements(String... els) throws StreamWriterException {
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
     * @throws StreamWriterException
     */
    public void writeErrors(String... errors) throws StreamWriterException {
        out.writeStartElement("errors");
        for (int i = 0; i < errors.length; i += 2) {
            writeElement(errors[i], errors[i + 1]);
        }
        out.writeEndElement();
    }

    /**
     * Writes the given element to the stream.
     * 
     * @param element the element name
     * @param content the element content
     * @throws StreamWriterException
     */
    public void writeElement(String element, @Nullable String content)
            throws StreamWriterException {
        out.writeElement(element, content);
    }

    /**
     * Writes staged changes to the stream.
     * 
     * @param setFilter the configured {@link DiffIndex} command
     * @param start the change number to start writing from
     * @param length the number of changes to write
     * @throws StreamWriterException
     */
    public void writeStaged(DiffIndex setFilter, int start, int length)
            throws StreamWriterException {
        writeDiffEntries("staged", start, length, setFilter.call());
    }

    /**
     * Writes unstaged changes to the stream.
     * 
     * @param setFilter the configured {@link DiffWorkTree} command
     * @param start the change number to start writing from
     * @param length the number of changes to write
     * @throws StreamWriterException
     */
    public void writeUnstaged(DiffWorkTree setFilter, int start, int length)
            throws StreamWriterException {
        writeDiffEntries("unstaged", start, length, setFilter.call());
    }

    public void writeUnmerged(Iterator<Conflict> conflicts, int start, int length)
            throws StreamWriterException {

        Iterators.advance(conflicts, start);
        if (length >= 0) {
            conflicts = Iterators.limit(conflicts, length);
        }
        out.writeStartArray("unmerged");
        while (conflicts.hasNext()) {
            Conflict entry = conflicts.next();
            out.writeStartArrayElement("unmerged");
            writeElement("changeType", "CONFLICT");
            writeElement("path", entry.getPath());
            writeElement("ours", entry.getOurs().toString());
            writeElement("theirs", entry.getTheirs().toString());
            writeElement("ancestor", entry.getAncestor().toString());
            out.writeEndArrayElement();
        }
        out.writeEndArray();
    }

    /**
     * Writes a set of {@link DiffEntry}s to the stream.
     * 
     * @param name the element name
     * @param start the change number to start writing from
     * @param length the number of changes to write
     * @param entries an iterator for the DiffEntries to write
     * @throws StreamWriterException
     */
    public void writeDiffEntries(String name, int start, int length, Iterator<DiffEntry> entries)
            throws StreamWriterException {
        Iterators.advance(entries, start);
        if (length < 0) {
            length = Integer.MAX_VALUE;
        }
        int counter = 0;
        out.writeStartArray(name);
        while (entries.hasNext() && counter < length) {
            DiffEntry entry = entries.next();
            out.writeStartArrayElement(name);
            writeElement("changeType", entry.changeType().toString());
            NodeRef oldObject = entry.getOldObject();
            NodeRef newObject = entry.getNewObject();
            if (oldObject == null) {
                writeElement("newPath", newObject.path());
                writeElement("newObjectId", newObject.getObjectId().toString());
                writeElement("path", "");
                writeElement("oldObjectId", ObjectId.NULL.toString());
            } else if (newObject == null) {
                writeElement("newPath", "");
                writeElement("newObjectId", ObjectId.NULL.toString());
                writeElement("path", oldObject.path());
                writeElement("oldObjectId", oldObject.getObjectId().toString());
            } else {
                writeElement("newPath", newObject.path());
                writeElement("newObjectId", newObject.getObjectId().toString());
                writeElement("path", oldObject.path());
                writeElement("oldObjectId", oldObject.getObjectId().toString());
            }
            out.writeEndArrayElement();
            counter++;
        }
        out.writeEndArray();
        if (entries.hasNext()) {
            writeElement("nextPage", "true");
        }
    }

    public void writeCommit(RevCommit commit, String tag, @Nullable Integer adds,
            @Nullable Integer modifies, @Nullable Integer removes) throws StreamWriterException {
        writeCommit(commit, tag, adds, modifies, removes, false);
    }

    private void writeCommitImpl(RevCommit commit, String tag, @Nullable Integer adds,
            @Nullable Integer modifies, @Nullable Integer removes) throws StreamWriterException {
        writeElement("id", commit.getId().toString());
        writeElement("tree", commit.getTreeId().toString());

        ImmutableList<ObjectId> parentIds = commit.getParentIds();
        out.writeStartElement("parents");
        out.writeStartArray("id");
        for (ObjectId parentId : parentIds) {
            out.writeArrayElement("id", parentId.toString());
        }
        out.writeEndArray();
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

        out.writeCDataElement("message", commit.getMessage());
    }

    private void writeCommit(RevCommit commit, String tag, @Nullable Integer adds,
            @Nullable Integer modifies, @Nullable Integer removes, boolean isListCommit)
            throws StreamWriterException {
        if (isListCommit) {
            // in a list, write to an array
            out.writeStartArrayElement(tag);
        } else {
            out.writeStartElement(tag);
        }
        // write the commit
        writeCommitImpl(commit, tag, adds, modifies, removes);
        if (isListCommit) {
            // in a list, write to an array
            out.writeEndArrayElement();
        } else {
            out.writeEndElement();
        }
    }

    private void writeNode(Node node, String tag) throws StreamWriterException {
        out.writeStartArrayElement(tag);
        writeElement("name", node.getName());
        writeElement("type", node.getType().name());
        writeElement("objectid", node.getObjectId().toString());
        writeElement("metadataid", node.getMetadataId().or(ObjectId.NULL).toString());
        out.writeEndArrayElement();
    }

    public void writeTree(RevTree tree, String tag) throws StreamWriterException {
        out.writeStartElement(tag);
        writeElement("id", tree.getId().toString());
        writeElement("size", Long.toString(tree.size()));
        writeElement("numtrees", Integer.toString(tree.numTrees()));

        out.writeStartArray("subtree");
        tree.forEachTree((t) -> writeNode(t, "subtree"));
        out.writeEndArray();

        out.writeStartArray("feature");
        tree.forEachFeature((f) -> writeNode(f, "feature"));
        out.writeEndArray();

        out.writeStartArray("bucket");
        tree.forEachBucket(bucket -> {
            out.writeStartArrayElement("bucket");
            writeElement("bucketindex", String.valueOf(bucket.getIndex()));
            writeElement("bucketid", bucket.getObjectId().toString());
            Envelope env = new Envelope();
            env.setToNull();
            bucket.expand(env);
            out.writeStartElement("bbox");
            writeElement("minx", Double.toString(env.getMinX()));
            writeElement("maxx", Double.toString(env.getMaxX()));
            writeElement("miny", Double.toString(env.getMinY()));
            writeElement("maxy", Double.toString(env.getMaxY()));
            out.writeEndElement();
            out.writeEndArrayElement();
        });
        out.writeEndArray();
        out.writeEndElement();
    }

    public void writeFeature(RevFeature feature, String tag) throws StreamWriterException {
        out.writeStartElement(tag);
        writeElement("id", feature.getId().toString());
        out.writeStartArray("attribute");
        for (int i = 0; i < feature.size(); i++) {
            Object value = feature.get(i).orNull();
            final FieldType type = FieldType.forValue(value);
            String valueString = TextValueSerializer.asString(value);
            out.writeStartArrayElement("attribute");
            writeElement("type", type.toString());
            writeElement("value", valueString);
            out.writeEndArrayElement();
        }
        out.writeEndArray();
        out.writeEndElement();
    }

    public void writeFeatureType(RevFeatureType featureType, String tag)
            throws StreamWriterException {
        out.writeStartElement(tag);
        writeElement("id", featureType.getId().toString());
        writeElement("name", featureType.getName().toString());
        ImmutableList<PropertyDescriptor> descriptors = featureType.descriptors();
        out.writeStartArray("attribute");
        for (PropertyDescriptor descriptor : descriptors) {
            out.writeStartArrayElement("attribute");
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
            out.writeEndArrayElement();
        }
        out.writeEndArray();
        out.writeEndElement();
    }

    public void writeTag(RevTag revTag, String tag) throws StreamWriterException {
        writeTag(revTag, tag, false);
    }

    public void writeIndexInfos(final List<IndexInfo> indexInfos, String tag) {
        out.writeStartArray(tag);
        for (IndexInfo indexInfo : indexInfos) {
            writeIndexInfo(indexInfo, tag, true);
        }
        out.writeEndArray();
    }

    public void writeIndexInfo(final IndexInfo indexInfo, String tag, boolean isListItem) {
        if (isListItem) {
            out.writeStartArrayElement(tag);
        } else {
            out.writeStartElement(tag);
        }
        writeElement("treeName", indexInfo.getTreeName());
        writeElement("attributeName", indexInfo.getAttributeName());
        writeElement("indexType", indexInfo.getIndexType().toString());
        Map<String, Object> metadata = indexInfo.getMetadata();
        if (metadata.containsKey(IndexInfo.MD_QUAD_MAX_BOUNDS)) {
            writeElement("bounds", metadata.get(IndexInfo.MD_QUAD_MAX_BOUNDS).toString());
        }
        if (metadata.containsKey(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA)) {
            String[] extraAttributes = (String[]) metadata
                    .get(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA);
            out.writeStartArray("extraAttribute");
            for (String extraAttribute : extraAttributes) {
                out.writeArrayElement("extraAttribute", extraAttribute);
            }
            out.writeEndArray();
        }
        if (isListItem) {
            out.writeEndArrayElement();
        } else {
            out.writeEndElement();
        }
    }

    private void writeTagImpl(RevTag revTag, String tag) throws StreamWriterException {
        writeElement("id", revTag.getId().toString());
        writeElement("commitid", revTag.getCommitId().toString());
        writeElement("name", revTag.getName());
        writeElement("message", revTag.getMessage());
        writePerson("tagger", revTag.getTagger());
    }

    private void writeTag(RevTag revTag, String tag, boolean isListTag)
            throws StreamWriterException {
        if (isListTag) {
            // Tag is in a List
            out.writeStartArrayElement(tag);
        } else {
            // not in a list
            out.writeStartElement(tag);
        }
        // write the Tag
        writeTagImpl(revTag, tag);
        if (isListTag) {
            out.writeEndArrayElement();
        } else {
            out.writeEndElement();
        }
    }

    /**
     * Writes a set of {@link RevCommit}s to the stream.
     * 
     * @param entries an iterator for the RevCommits to write
     * @param elementsPerPage the number of commits per page
     * @param returnRange only return the range if true
     * @throws StreamWriterException
     */
    public void writeCommits(Iterator<RevCommit> entries, int elementsPerPage, boolean returnRange)
            throws StreamWriterException {
        int counter = 0;
        RevCommit lastCommit = null;
        if (returnRange) {
            if (entries.hasNext()) {
                lastCommit = entries.next();
                writeCommit(lastCommit, "untilCommit", null, null, null);
                counter++;
            }
        }
        out.writeStartArray("commit");
        while (entries.hasNext() && (returnRange || counter < elementsPerPage)) {
            lastCommit = entries.next();

            if (!returnRange) {
                writeCommit(lastCommit, "commit", null, null, null, true);
            }

            counter++;
        }
        out.writeEndArray();
        if (returnRange) {
            if (lastCommit != null) {
                writeCommit(lastCommit, "sinceCommit", null, null, null, false);
            }
            writeElement("numCommits", Integer.toString(counter));
        }
        if (entries.hasNext()) {
            writeElement("nextPage", "true");
        }
    }

    public void writeCommitsWithChangeCounts(Iterator<CommitWithChangeCounts> entries,
            int elementsPerPage) throws StreamWriterException {
        int counter = 0;

        out.writeStartArray("commit");
        while (entries.hasNext() && counter < elementsPerPage) {
            CommitWithChangeCounts entry = entries.next();

            writeCommit(entry.getCommit(), "commit", entry.getAdds(), entry.getModifies(),
                    entry.getRemoves(), true);

            counter++;
        }
        out.writeEndArray();
        if (entries.hasNext()) {
            writeElement("nextPage", "true");
        }

    }

    /**
     * Writes a {@link RevPerson} to the stream.
     * 
     * @param enclosingElement the element name
     * @param p the RevPerson to writes
     * @throws StreamWriterException
     */
    public void writePerson(String enclosingElement, RevPerson p) throws StreamWriterException {
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
     * @throws StreamWriterException
     */
    public void writeCommitResponse(RevCommit commit, int adds, int deletes, int changes)
            throws StreamWriterException {
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
     * @throws StreamWriterException
     */
    public void writeLsTreeResponse(Iterator<NodeRef> iter, boolean verbose)
            throws StreamWriterException {

        out.writeStartArray("node");
        while (iter.hasNext()) {
            NodeRef node = iter.next();
            out.writeStartArrayElement("node");
            writeElement("path", node.path());
            if (verbose) {
                writeElement("metadataId", node.getMetadataId().toString());
                writeElement("type", node.getType().toString().toLowerCase());
                writeElement("objectId", node.getObjectId().toString());
            }
            out.writeEndArrayElement();
        }
        out.writeEndArray();
    }

    /**
     * Writes the response for the {@link UpdateRef} command to the stream.
     * 
     * @param ref the ref returned from the command
     * @throws StreamWriterException
     */
    public void writeUpdateRefResponse(Ref ref) throws StreamWriterException {
        out.writeStartElement("ChangedRef");
        writeElement("name", ref.getName());
        writeElement("objectId", ref.getObjectId().toString());
        if (ref instanceof SymRef) {
            writeElement("target", ((SymRef) ref).getTarget());
        }
        out.writeEndElement();
    }

    /**
     * Writes the response for the {@link RefParse} command to the stream.
     * 
     * @param ref the ref returned from the command
     * @throws StreamWriterException
     */
    public void writeRefParseResponse(Ref ref) throws StreamWriterException {
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
     * @throws StreamWriterException
     */
    public void writeEmptyRefResponse() throws StreamWriterException {
        out.writeStartElement("RefNotFound");
        out.writeEndElement();
    }

    /**
     * Writes the response for the {@link Branch} command to the stream.
     * 
     * @param localBranches the local branches of the repository
     * @param remoteBranches the remote branches of the repository
     * @throws StreamWriterException
     */
    public void writeBranchListResponse(List<Ref> localBranches, List<Ref> remoteBranches)
            throws StreamWriterException {

        out.writeStartElement("Local");
        out.writeStartArray("Branch");
        for (Ref branch : localBranches) {
            out.writeStartArrayElement("Branch");
            writeElement("name", branch.localName());
            out.writeEndArrayElement();
        }
        out.writeEndArray();
        out.writeEndElement();

        out.writeStartElement("Remote");
        out.writeStartArray("Branch");
        for (Ref branch : remoteBranches) {
            if (!(branch instanceof SymRef)) {
                out.writeStartArrayElement("Branch");
                String namespace = branch.namespace();
                String remoteName = namespace.replace(Ref.REMOTES_PREFIX, "").replace("/", "");
                writeElement("remoteName", remoteName);
                writeElement("name", branch.localName());
                out.writeEndArrayElement();
            }
        }
        out.writeEndArray();
        out.writeEndElement();

    }

    public void writeBranchCreateResponse(Ref createdBranch) throws StreamWriterException {
        out.writeStartElement("BranchCreated");
        writeElement("name", createdBranch.localName());
        writeElement("source", createdBranch.getObjectId().toString());
        out.writeEndElement(); // End BranchCreated
    }

    /**
     * Writes the response for the {@link RemoteManagement} command to the stream.
     * 
     * @param remotes the list of the {@link Remote}s of this repository
     * @throws StreamWriterException
     */
    public void writeRemoteListResponse(List<Remote> remotes, boolean verbose)
            throws StreamWriterException {
        out.writeStartArray("Remote");
        for (Remote remote : remotes) {
            out.writeStartArrayElement("Remote");
            writeElement("name", remote.getName());
            if (verbose) {
                writeElement("url", remote.getFetchURL());
                if (remote.getUserName() != null) {
                    writeElement("username", remote.getUserName());
                }
            }
            out.writeEndArrayElement();
        }
        out.writeEndArray();
    }

    /**
     * Writes the response for the {@link RemoteManagement} command to the stream.
     * 
     * @param success whether or not the ping was successful
     * @throws StreamWriterException
     */
    public void writeRemotePingResponse(boolean success) throws StreamWriterException {
        out.writeStartElement("ping");
        writeElement("success", Boolean.toString(success));
        out.writeEndElement();
    }

    /**
     * Writes the list response for the {@link Tag} command to the stream.
     * 
     * @param tags the list of {@link RevTag}s of this repository
     * @throws StreamWriterException
     */
    public void writeTagListResponse(List<RevTag> tags) throws StreamWriterException {
        out.writeStartArray("Tag");
        for (RevTag tag : tags) {
            writeTag(tag, "Tag", true);
        }
        out.writeEndArray();
    }

    /**
     * Writes the delete response for the {@link Tag} command to the stream.
     * 
     * @param tag the removed {@link RevTag}
     * @throws StreamWriterException
     */
    public void writeTagDeleteResponse(RevTag tag) throws StreamWriterException {
        writeTag(tag, "DeletedTag", false);
    }

    /**
     * Writes the create response for the {@link Tag} command to the stream.
     * 
     * @param tag the created {@link RevTag}
     * @throws StreamWriterException
     */
    public void writeTagCreateResponse(RevTag tag) throws StreamWriterException {
        writeTag(tag, "Tag", false);
    }

    public void writeRebuildGraphResponse(ImmutableList<ObjectId> updatedObjects, boolean quiet)
            throws StreamWriterException {
        out.writeStartElement("RebuildGraph");
        if (updatedObjects.size() > 0) {
            writeElement("updatedGraphElements", Integer.toString(updatedObjects.size()));
            if (!quiet) {
                out.writeStartArray("UpdatedObject");
                for (ObjectId object : updatedObjects) {
                    out.writeStartArrayElement("UpdatedObject");
                    writeElement("ref", object.toString());
                    out.writeEndArrayElement();
                }
                out.writeEndArray();
            }
        } else {
            writeElement("response",
                    "No missing or incomplete graph elements (commits) were found.");
        }
        out.writeEndElement();
    }

    public void writeFetchResponse(TransferSummary result) throws StreamWriterException {
        out.writeStartElement("Fetch");
        for (Entry<String, Collection<RefDiff>> entry : result.getRefDiffs().entrySet()) {
            Iterable<RefDiff> branches = Iterables.filter(entry.getValue(), //
                    (e) -> !(e.getNewRef() instanceof SymRef));
            List<RefDiff> refs = Lists.newArrayList(branches);
            if (refs.isEmpty()) {
                continue;
            }
            out.writeStartElement("Remote");
            writeElement("remoteURL", entry.getKey());
            out.writeStartArray("Branch");
            for (RefDiff ref : refs) {
                out.writeStartArrayElement("Branch");

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
                out.writeEndArrayElement();
            }
            out.writeEndArray();
            out.writeEndElement();
        }
        out.writeEndElement();
    }

    public void writePullResponse(PullResult result, Iterator<DiffEntry> iter)
            throws StreamWriterException {
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
            writeMergeResponse(Optional.fromNullable(report.getMergeCommit()),
                    report.getReport().get(), report.getOurs(),
                    report.getPairs().get(0).getTheirs(), report.getPairs().get(0).getAncestor());
        }
        out.writeEndElement();
    }

    /**
     * Writes a set of feature diffs to the stream.
     * 
     * @param diffs a map of {@link PropertyDescriptor} to {@link AttributeDiffs} that specify the
     *        difference between two features
     * @throws StreamWriterException
     */
    public void writeFeatureDiffResponse(Map<PropertyDescriptor, AttributeDiff> diffs)
            throws StreamWriterException {
        Set<Entry<PropertyDescriptor, AttributeDiff>> entries = diffs.entrySet();
        Iterator<Entry<PropertyDescriptor, AttributeDiff>> iter = entries.iterator();
        out.writeStartArray("diff");
        while (iter.hasNext()) {
            Entry<PropertyDescriptor, AttributeDiff> entry = iter.next();
            out.writeStartArrayElement("diff");
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
            if (entry.getValue().getOldValue() != null) {
                writeElement("oldvalue", entry.getValue().getOldValue().toString());
            }
            if (entry.getValue().getNewValue() != null
                    && !entry.getValue().getType().equals(TYPE.NO_CHANGE)) {
                writeElement("newvalue", entry.getValue().getNewValue().toString());
            }
            out.writeEndArrayElement();
        }
        out.writeEndArray();
    }

    /**
     * Writes the response for a set of diffs while also supplying the geometry.
     * 
     * @param geogig - a CommandLocator to call commands from
     * @param diff - a DiffEntry iterator to build the response from
     * @throws StreamWriterException
     */
    public void writeGeometryChanges(final Context geogig, Iterator<DiffEntry> diff, int page,
            int elementsPerPage) throws StreamWriterException {
        writeGeometryChanges(geogig, diff, page, elementsPerPage, false);
    }

    private void writeGeometryChanges(final Context geogig, Iterator<DiffEntry> diff, int page,
            int elementsPerPage, boolean alreadyInArray) throws StreamWriterException {

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
                            SimpleFeature simpleFeature = (SimpleFeature) builder
                                    .build(revFeature.getId().toString(), revFeature);
                            change = new GeometryChange(simpleFeature, input.changeType(), path,
                                    crsCode);
                        }
                        return change;
                    }
                });
        if (!alreadyInArray) {
            out.writeStartArray("Feature");
        }
        while (changeIterator.hasNext() && (elementsPerPage == 0 || counter < elementsPerPage)) {
            GeometryChange next = changeIterator.next();
            if (next != null) {
                SimpleFeature feature = next.getFeature();
                ChangeType change = next.getChangeType();
                out.writeStartArrayElement("Feature");
                writeElement("change", change.toString());
                writeElement("id", next.getPath());
                List<Object> attributes = feature.getAttributes();
                out.writeStartArray("geometry");
                for (Object attribute : attributes) {
                    if (attribute instanceof Geometry) {
                        out.writeArrayElement("geometry", ((Geometry) attribute).toText());
                        break;
                    }
                }
                out.writeEndArray();
                if (next.getCRS() != null) {
                    writeElement("crs", next.getCRS());
                }
                out.writeEndArrayElement();
                counter++;
            }
        }
        if (!alreadyInArray) {
            out.writeEndArray();
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
     * @throws StreamWriterException
     */
    public void writeConflicts(final Context geogig, Iterator<Conflict> conflicts,
            final ObjectId ours, final ObjectId theirs) throws StreamWriterException {
        writeConflicts(geogig, conflicts, ours, theirs, false);
    }

    private void writeConflicts(final Context geogig, Iterator<Conflict> conflicts,
            final ObjectId ours, final ObjectId theirs, boolean alreadyInArray)
            throws StreamWriterException {
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
                            throw new CommandSpecException(
                                    "Couldn't resolve id: " + commitId.toString() + " to a commit");
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
                                    .setObjectId(node.get().getObjectId()).call();
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
                            SimpleFeature simpleFeature = (SimpleFeature) builder
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

        if (!alreadyInArray) {
            out.writeStartArray("Feature");
        }
        while (conflictIterator.hasNext()) {
            GeometryConflict next = conflictIterator.next();
            if (next != null) {
                out.writeStartArrayElement("Feature");
                writeElement("change", "CONFLICT");
                writeElement("id", next.getConflict().getPath());
                writeElement("ourvalue", next.getConflict().getOurs().toString());
                writeElement("theirvalue", next.getConflict().getTheirs().toString());
                out.writeStartArray("geometry");
                out.writeArrayElement("geometry", next.getGeometry().toText());
                out.writeEndArray();
                if (next.getCRS() != null) {
                    writeElement("crs", next.getCRS());
                }
                out.writeEndArrayElement();
            }
        }
        if (!alreadyInArray) {
            out.writeEndArray();
        }
    }

    /**
     * Writes the response for a set of merged features while also supplying the geometry.
     * 
     * @param geogig - a CommandLocator to call commands from
     * @param features - a FeatureInfo iterator to build the response from
     * @throws StreamWriterException
     */
    public void writeMerged(final Context geogig, Iterator<FeatureInfo> features)
            throws StreamWriterException {
        writeMerged(geogig, features, false);
    }

    private void writeMerged(final Context geogig, Iterator<FeatureInfo> features,
            boolean alreadyInArray) throws StreamWriterException {
        Iterator<GeometryChange> changeIterator = Iterators.transform(features,
                new Function<FeatureInfo, GeometryChange>() {

                    private Map<ObjectId, RevFeatureType> typeCache = new HashMap<>();

                    @Override
                    public GeometryChange apply(FeatureInfo input) {
                        GeometryChange change = null;
                        RevFeature revFeature = input.getFeature();
                        ObjectId typeId = input.getFeatureTypeId();
                        RevFeatureType featureType = typeCache.get(typeId);
                        if (null == featureType) {
                            featureType = geogig.objectDatabase().getFeatureType(typeId);
                            typeCache.put(typeId, featureType);
                        }
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
                        SimpleFeature simpleFeature = (SimpleFeature) builder
                                .build(revFeature.getId().toString(), revFeature);
                        change = new GeometryChange(simpleFeature, ChangeType.MODIFIED,
                                input.getPath(), crsCode);
                        return change;
                    }
                });

        if (!alreadyInArray) {
            out.writeStartArray("Feature");
        }
        while (changeIterator.hasNext()) {
            GeometryChange next = changeIterator.next();
            if (next != null) {
                SimpleFeature feature = next.getFeature();
                out.writeStartArrayElement("Feature");
                writeElement("change", "MERGED");
                writeElement("id", next.getPath());
                List<Object> attributes = feature.getAttributes();
                out.writeStartArray("geometry");
                for (Object attribute : attributes) {
                    if (attribute instanceof Geometry) {
                        out.writeArrayElement("geometry", ((Geometry) attribute).toText());
                        break;
                    }
                }
                out.writeEndArray();
                if (next.getCRS() != null) {
                    writeElement("crs", next.getCRS());
                }
                out.writeEndArrayElement();
            }
        }
        if (!alreadyInArray) {
            out.writeEndArray();
        }
    }

    /**
     * Writes the response for a merge.
     * 
     * @param mergeCommit - the merge commit, if the merge was successful
     * @param report - the MergeScenarioReport containing a summary of the merge results
     * @param ours - our commit id
     * @param theirs - their commit id
     * @param ancestor - the ancestor commit id
     * @throws StreamWriterException
     */
    public void writeMergeResponse(Optional<RevCommit> mergeCommit, MergeScenarioReport report,
            ObjectId ours, ObjectId theirs, ObjectId ancestor) throws StreamWriterException {
        out.writeStartElement("Merge");
        writeElement("ours", ours.toString());
        writeElement("theirs", theirs.toString());
        writeElement("ancestor", ancestor.toString());
        if (mergeCommit.isPresent()) {
            writeElement("mergedCommit", mergeCommit.get().getId().toString());
        }
        if (report.getConflicts() > 0) {
            writeElement("conflicts", Long.toString(report.getConflicts()));
        }
        out.writeEndElement();
    }

    /**
     * Writes the response for a merge that includes the first page of features from the merge
     * scenario.
     * 
     * @param mergeCommit - the merge commit, if the merge was successful
     * @param report - the MergeScenarioReport containing a summary of the merge results
     * @param context - the context that the merge was run on
     * @param ours - our commit id
     * @param theirs - their commit id
     * @param ancestor - the ancestor commit id
     * @param consumer - the page of features
     * @throws StreamWriterException
     */
    public void writeMergeConflictsResponse(Optional<RevCommit> mergeCommit,
            MergeScenarioReport report, Context context, ObjectId ours, ObjectId theirs,
            ObjectId ancestor, PagedMergeScenarioConsumer consumer) throws StreamWriterException {
        out.writeStartElement("Merge");
        writeElement("ours", ours.toString());
        writeElement("theirs", theirs.toString());
        writeElement("ancestor", ancestor.toString());
        if (mergeCommit.isPresent()) {
            writeElement("mergedCommit", mergeCommit.get().getId().toString());
        }
        if (report.getConflicts() > 0) {
            writeElement("conflicts", Long.toString(report.getConflicts()));
        }
        // start the Feature array
        out.writeStartArray("Feature");
        writeGeometryChanges(context, consumer.getUnconflicted(), 0, 0, true);
        writeConflicts(context, consumer.getConflicted(), ours, theirs, true);
        writeMerged(context, consumer.getMerged(), true);
        out.writeEndArray();
        if (!consumer.didFinish()) {
            writeElement("additionalChanges", Boolean.toString(true));
        }
        out.writeEndElement();
    }

    /**
     * Writes a page of features from a merge scenario.
     * 
     * @param context the context that the merge scenario was run on
     * @param ours our commit id
     * @param theirs their commit id
     * @param consumer the page of features
     * @throws StreamWriterException
     */
    public void writeReportMergeScenarioResponse(Context context, ObjectId ours, ObjectId theirs,
            PagedMergeScenarioConsumer consumer) throws StreamWriterException {
        out.writeStartElement("Merge");
        // start the Feature array
        out.writeStartArray("Feature");
        writeGeometryChanges(context, consumer.getUnconflicted(), 0, 0, true);
        writeConflicts(context, consumer.getConflicted(), ours, theirs, true);
        writeMerged(context, consumer.getMerged(), true);
        out.writeEndArray();
        if (!consumer.didFinish()) {
            writeElement("additionalChanges", Boolean.toString(true));
        }
        out.writeEndElement();
    }

    /**
     * Writes the id of the transaction created or nothing if it was ended successfully.
     * 
     * @param transactionId - the id of the transaction or null if the transaction was closed
     *        successfully
     * @throws StreamWriterException
     */
    public void writeTransactionId(UUID transactionId) throws StreamWriterException {
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
     * @throws StreamWriterException
     */
    public void writeBlameReport(BlameReport report) throws StreamWriterException {
        out.writeStartElement("Blame");
        Map<String, ValueAndCommit> changes = report.getChanges();
        Iterator<String> iter = changes.keySet().iterator();
        out.writeStartArray("Attribute");
        while (iter.hasNext()) {
            String attrib = iter.next();
            ValueAndCommit valueAndCommit = changes.get(attrib);
            RevCommit commit = valueAndCommit.commit;
            Optional<?> value = valueAndCommit.value;
            out.writeStartArrayElement("Attribute");
            writeElement("name", attrib);
            writeElement("value",
                    TextValueSerializer.asString(Optional.fromNullable((Object) value.orNull())));
            writeCommit(commit, "commit", null, null, null);
            out.writeEndArrayElement();
        }
        out.writeEndArray();
        out.writeEndElement();
    }

    public void writeStatistics(List<Statistics.FeatureTypeStats> stats, RevCommit firstCommit,
            RevCommit lastCommit, int totalCommits, List<RevPerson> authors, int totalAdded,
            int totalModified, int totalRemoved) throws StreamWriterException {
        out.writeStartElement("Statistics");
        int numFeatureTypes = 0;
        int totalNumFeatures = 0;
        if (!stats.isEmpty()) {
            out.writeStartElement("FeatureTypes");
            out.writeStartArray("FeatureType");
            for (Statistics.FeatureTypeStats stat : stats) {
                numFeatureTypes++;
                out.writeStartArrayElement("FeatureType");
                writeElement("name", stat.getName());
                writeElement("numFeatures", Long.toString(stat.getNumFeatures()));
                totalNumFeatures += stat.getNumFeatures();
                out.writeEndArrayElement();
            }
            out.writeEndArray();
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
            out.writeStartArray("Author");
            for (RevPerson author : authors) {
                if (author.getName().isPresent() || author.getEmail().isPresent()) {
                    out.writeStartArrayElement("Author");
                    if (author.getName().isPresent()) {
                        writeElement("name", author.getName().get());
                    }
                    if (author.getEmail().isPresent()) {
                        writeElement("email", author.getEmail().get());
                    }
                    out.writeEndArrayElement();
                }
            }
            out.writeEndArray();
            writeElement("totalAuthors", Integer.toString(authors.size()));
            out.writeEndElement();
        }
        out.writeEndElement();

    }

    public void writeConfigList(Iterator<Map.Entry<String, String>> configListIterator) {
        out.writeStartArray("config");
        while (configListIterator.hasNext()) {
            Map.Entry<String, String> pairs = (Map.Entry<String, String>) configListIterator.next();
            out.writeStartArrayElement("config");
            out.writeElement("name", pairs.getKey());
            out.writeElement("value", pairs.getValue());
            out.writeEndArrayElement();
        }
        out.writeEndArray();
    }

    public void writeRepoInitResponse(String repositoryName, String baseUrl, String link) {
        out.writeStartElement("repo");
        out.writeElement("name", repositoryName);
        encodeAlternateAtomLink(baseUrl, link);
        out.writeEndElement();
    }

    private class GeometryChange {
        private SimpleFeature feature;

        private ChangeType changeType;

        private String path;

        private String crs;

        public GeometryChange(SimpleFeature feature, ChangeType changeType, String path,
                String crs) {
            this.feature = feature;
            this.changeType = changeType;
            this.path = path;
            this.crs = crs;
        }

        public SimpleFeature getFeature() {
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
