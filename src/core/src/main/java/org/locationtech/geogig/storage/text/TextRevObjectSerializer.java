/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.text;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.base.Preconditions;
import org.locationtech.geogig.crs.CoordinateReferenceSystem;
import org.locationtech.geogig.feature.FeatureType;
import org.locationtech.geogig.feature.FeatureType.FeatureTypeBuilder;
import org.locationtech.geogig.feature.Name;
import org.locationtech.geogig.feature.PropertyDescriptor;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.FieldType;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevCommitBuilder;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureBuilder;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevPerson;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.RevObjectSerializer;
import org.locationtech.geogig.storage.impl.ObjectReader;
import org.locationtech.geogig.storage.impl.ObjectWriter;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

import com.google.common.base.Splitter;

/**
 * An {@link ObjectSerialisingFactory} for the {@link RevObject}s text format.
 * <p>
 * The following formats are used to interchange {@link RevObject} instances in a text format:
 * <H3>Commit:</H3>
 * 
 * <pre>
 * {@code COMMIT\n}
 * {@code "tree" + "\t" +  <tree id> + "\n"}
 * {@code "parents" + "\t" +  <parent id> [+ " " + <parent id>...]  + "\n"}
 * {@code "author" + "\t" +  <author name>  + " " + <author email>  + "\t" + <author_timestamp> + "\t" + <author_timezone_offset> + "\n"}
 * {@code "committer" + "\t" +  <committer name>  + " " + <committer email>  + "\t" + <committer_timestamp> + "\t" + <committer_timezone_offset> + "\n"}     * 
 * {@code "message" + "\t" +  <message> + "\n"}
 * </pre>
 * 
 * <H3>Tree:</H3>
 * 
 * <pre>
 * {@code TREE\n} 
 * {@code "size" + "\t" +  <size> + "\n"}
 * {@code "numtrees" + "\t" +  <numtrees> + "\n"}
 * 
 * {@code "BUCKET" + "\t" +  <bucket_idx> + "\t" + <ObjectId> + "\t" + <bounds> + "\n"}
 * or 
 * {@code "REF" + "\t" +  <ref_type> + "\t" + <ref_name> + "\t" + <ObjectId> + "\t" + <MetadataId> + "\t" + <bounds>"\n"}
 * .
 * .
 * .
 * </pre>
 * 
 * <H3>Feature:</H3>
 * 
 * <pre>
 * {@code FEATURE\n}
 * {@code "<attribute_class_1>" + "\t" +  <attribute_value_1> + "\n"}
 * .
 * .
 * .     
 * {@code "<attribute_class_n>" + "\t" +  <attribute_value_n> + "\n"}
 * For array types, values are written as a space-separated list of single values, enclosed between square brackets
 * </pre>
 * 
 * <H3>FeatureType:</H3>
 * 
 * <pre>
 * {@code FEATURE_TYPE\n} 
 * {@code "name" + "\t" +  <name> + "\n"}
 * {@code "<attribute_name>" + "\t" +  <attribute_type> + "\t" + <min_occur> + "\t" + <max_occur> +  "\t" + <nillable <True|False>> + "\n"}
 * {@code "<attribute_name>" + "\t" +  <attribute_type> + "\t" + <min_occur> + "\t" + <max_occur> +  "\t" + <nillable <True|False>> + "\n"}
 * .
 * .
 * .
 * 
 * </pre>
 * 
 * <H3>Tag:</H3>
 * 
 * <pre>
 * {@code "id" + "\t" +  <id> + "\n"}
 * ...
 * </pre>
 */
public class TextRevObjectSerializer implements RevObjectSerializer {

    public static final TextRevObjectSerializer INSTANCE = new TextRevObjectSerializer();

    /**
     * @return {@code false}, this serializer does not support reading back multiple objects from a
     *         single stream since the parser uses a {@link BufferedReader} and the encoded format
     *         does not define a trailing marker on each object to unambiguously determine the end
     *         of one object and the start of the next object.
     */
    public @Override boolean supportsStreaming() {
        return false;
    }

    /**
     * Abstract text writer that provides print methods on a {@link Writer} to consistently write
     * newlines as {@code \n} instead of using the platform's line separator as in
     * {@link PrintWriter}. It also provides some common method used by different writers.
     */
    private static abstract class TextWriter<T extends RevObject> implements ObjectWriter<T> {

        public static final String NULL_BOUNDING_BOX = "null";

        /**
         * Different types of tree nodes.
         */
        public enum TreeNode {
            REF, BUCKET;
        }

        public @Override void write(T object, OutputStream out) throws IOException {
            OutputStreamWriter writer = new OutputStreamWriter(out, "UTF-8");
            println(writer, object.getType().name());
            print(object, writer);
            writer.flush();
        }

        protected abstract void print(T object, Writer w) throws IOException;

        protected void print(Writer w, CharSequence... s) throws IOException {
            if (s == null) {
                return;
            }
            for (CharSequence c : s) {
                w.write(String.valueOf(c));
            }
        }

        protected void println(Writer w, CharSequence... s) throws IOException {
            if (s != null) {
                print(w, s);
            }
            w.write('\n');
        }

        protected void writeNode(Writer w, Node node) throws IOException {
            print(w, TreeNode.REF.name());
            print(w, "\t");
            print(w, node.getType().name());
            print(w, "\t");
            print(w, node.getName());
            print(w, "\t");
            print(w, node.getObjectId().toString());
            print(w, "\t");
            print(w, node.getMetadataId().orElse(ObjectId.NULL).toString());
            print(w, "\t");
            Envelope envHelper = new Envelope();
            writeBBox(w, node, envHelper);
            Map<String, Object> extraData = node.getExtraData();
            if (!extraData.isEmpty()) {
                String extraDataAsString = Marshallers.marshall(extraData);
                print(w, "\t");
                print(w, extraDataAsString);
            }
            println(w);
        }

        protected void writeBBox(Writer w, Node node, Envelope envHelper) throws IOException {
            envHelper.setToNull();
            node.expand(envHelper);
            writeEnvelope(w, envHelper);

        }

        protected void writeEnvelope(Writer w, Envelope envHelper) throws IOException {
            if (envHelper.isNull()) {
                print(w, TextWriter.NULL_BOUNDING_BOX);
                return;
            }

            print(w, Double.toString(envHelper.getMinX()));
            print(w, ";");
            print(w, Double.toString(envHelper.getMaxX()));
            print(w, ";");
            print(w, Double.toString(envHelper.getMinY()));
            print(w, ";");
            print(w, Double.toString(envHelper.getMaxY()));
        }

    }

    /**
     * Commit writer.
     * <p>
     * Output format:
     * 
     * <pre>
     * {@code COMMIT\n}
     * {@code "tree" + "\t" +  <tree id> + "\n"}
     * {@code "parents" + "\t" +  <parent id> [+ " " + <parent id>...]  + "\n"}
     * {@code "author" + "\t" +  <author name>  + " " + <author email>  + "\t" + <author_timestamp> + "\t" + <author_timezone_offset> + "\n"}
     * {@code "committer" + "\t" +  <committer name>  + " " + <committer email>  + "\t" + <committer_timestamp> + "\t" + <committer_timezone_offset> + "\n"}     * 
     * {@code "message" + "\t" +  <message> + "\n"}
     * </pre>
     * 
     */
    private static final TextWriter<RevCommit> COMMIT_WRITER = new TextWriter<RevCommit>() {

        protected @Override void print(RevCommit commit, Writer w) throws IOException {
            println(w, "tree\t", commit.getTreeId().toString());
            print(w, "parents\t");
            for (Iterator<ObjectId> it = commit.getParentIds().iterator(); it.hasNext();) {
                print(w, it.next().toString());
                if (it.hasNext()) {
                    print(w, " ");
                }
            }
            println(w);
            printPerson(w, "author", commit.getAuthor());
            printPerson(w, "committer", commit.getCommitter());
            println(w, "message\t", Optional.ofNullable(commit.getMessage()).orElse(""));
            w.flush();
        }

        private void printPerson(Writer w, String name, RevPerson person) throws IOException {
            print(w, name);
            print(w, "\t");
            print(w, person.getName().orElse(" "));
            print(w, "\t");
            print(w, person.getEmail().orElse(" "));
            print(w, "\t");
            print(w, Long.toString(person.getTimestamp()));
            print(w, "\t");
            print(w, Long.toString(person.getTimeZoneOffset()));
            println(w);
        }
    };

    /**
     * Feature writer.
     * <p>
     * Output format:
     * 
     * <pre>
     * {@code FEATURE\n}
     * {@code "<attribute_class_1>" + "\t" +  <attribute_value_1> + "\n"}
     * .
     * .
     * .     
     * {@code "<attribute_class_n>" + "\t" +  <attribute_value_n> + "\n"}
     * For array types, values are written as a space-separated list of single values, enclosed between square brackets
     * </pre>
     * 
     */
    private static final TextWriter<RevFeature> FEATURE_WRITER = new TextWriter<RevFeature>() {

        protected @Override void print(RevFeature feature, Writer w) throws IOException {
            for (int i = 0; i < feature.size(); i++) {
                Optional<Object> opt = feature.get(i);
                final FieldType type = FieldType.forValue(opt);
                String valueString = TextValueSerializer.asString(opt);
                println(w, type.toString() + "\t" + valueString);

            }
            w.flush();
        }

    };

    /**
     * Feature type writer.
     * <p>
     * Output format:
     * 
     * <pre>
     * {@code FEATURE_TYPE\n} 
     * {@code "name" + "\t" +  <name> + "\n"}
     * {@code "<attribute_name>" + "\t" +  <attribute_type> + "\t" + <min_occur> + "\t" + <max_occur> +  "\t" + <nillable <True|False>> + "\n"}
     * {@code "<attribute_name>" + "\t" +  <attribute_type> + "\t" + <min_occur> + "\t" + <max_occur> +  "\t" + <nillable <True|False>> + "\n"}
     * .
     * .
     * .
     * 
     * </pre>
     * 
     * Geometry attributes have an extra token per line representing the crs
     * 
     */
    private static final TextWriter<RevFeatureType> FEATURETYPE_WRITER = new TextWriter<RevFeatureType>() {

        protected @Override void print(RevFeatureType featureType, Writer w) throws IOException {
            println(w, "name\t", featureType.getName().toString());
            Collection<PropertyDescriptor> attribs = featureType.type().getDescriptors();
            for (PropertyDescriptor attrib : attribs) {
                printAttributeDescriptor(w, attrib);
            }
            w.flush();
        }

        private void printAttributeDescriptor(Writer w, PropertyDescriptor attrib)
                throws IOException {

            String name = attrib.getName().toString();

            print(w, name);
            print(w, "\t");
            print(w, FieldType.forBinding(attrib.getBinding()).name());
            print(w, "\t");
            print(w, Integer.toString(attrib.getMinOccurs()));
            print(w, "\t");
            print(w, Integer.toString(attrib.getMaxOccurs()));
            print(w, "\t");
            print(w, Boolean.toString(attrib.isNillable()));
            if (attrib.isGeometryDescriptor()) {
                String crsText = CrsTextSerializer.serialize(attrib.coordinateReferenceSystem());
                print(w, "\t");
                println(w, crsText);
            } else {
                println(w, "");
            }

        }

    };

    /**
     * Tree writer.
     * <p>
     * Output format:
     * 
     * <pre>
     * {@code TREE\n} 
     * {@code "size" + "\t" +  <size> + "\n"}
     * {@code "numtrees" + "\t" +  <numtrees> + "\n"}
     * 
     * {@code "BUCKET" + "\t" +  <bucket_idx> + "\t" + <ObjectId> + "\t" + <bounds> + "\n"}
     * or 
     * {@code "REF" + "\t" +  <ref_type> + "\t" + <ref_name> + "\t" + <ObjectId> + "\t" + <MetadataId> + "\t" + <bounds>"\n"}
     * .
     * .
     * .
     * </pre>
     */
    private static final TextWriter<RevTree> TREE_WRITER = new TextWriter<RevTree>() {

        protected @Override void print(RevTree revTree, Writer w) throws IOException {
            println(w, "size\t", Long.toString(revTree.size()));
            println(w, "numtrees\t", Integer.toString(revTree.numTrees()));

            writeChildren(w, revTree.trees());
            writeChildren(w, revTree.features());

            writeBuckets(w, revTree.getBuckets());
        }

        private void writeChildren(Writer w, Collection<Node> children) throws IOException {
            for (Node ref : children) {
                writeNode(w, ref);
            }
        }

        private void writeBuckets(Writer w, Iterable<Bucket> buckets) throws IOException {

            for (Bucket bucket : buckets) {
                print(w, TreeNode.BUCKET.name());
                print(w, "\t");
                print(w, String.valueOf(bucket.getIndex()));
                print(w, "\t");
                print(w, bucket.getObjectId().toString());
                print(w, "\t");
                Envelope env = new Envelope();
                env.setToNull();
                bucket.expand(env);
                writeEnvelope(w, env);
                println(w);
            }
        }

    };

    /**
     * Tag writer.
     * <p>
     * Output format:
     * 
     * <pre>
     * {@code TAG\n}
     * {@code "name" + "\t" +  <tagname> + "\n"}
     * {@code "commitid" + "\t" +  <comitid> + "\n"}  
     * {@code "message" + "\t" +  <message> + "\n"}
     *  {@code "tagger" + "\t" +  <tagger name>  + " " + <tagger email>  + "\t" + <tagger> + "\t" + <tagger_timezone_offset> + "\n"}
     * </pre>
     * 
     */
    private static final TextWriter<RevTag> TAG_WRITER = new TextWriter<RevTag>() {

        protected @Override void print(RevTag tag, Writer w) throws IOException {
            println(w, "name\t", tag.getName());
            println(w, "commitid\t", tag.getCommitId().toString());
            println(w, "message\t", tag.getMessage());
            print(w, "tagger");
            print(w, "\t");
            print(w, tag.getTagger().getName().orElse(" "));
            print(w, "\t");
            print(w, tag.getTagger().getEmail().orElse(" "));
            print(w, "\t");
            print(w, Long.toString(tag.getTagger().getTimestamp()));
            print(w, "\t");
            print(w, Long.toString(tag.getTagger().getTimeZoneOffset()));
            println(w);
            w.flush();
        }

    };

    private abstract static class TextReader<T extends RevObject> implements ObjectReader<T> {

        public @Override T read(ObjectId id, InputStream rawData) throws IllegalArgumentException {
            try {
                BufferedReader reader;
                reader = new BufferedReader(new InputStreamReader(rawData, "UTF-8"));
                TYPE type = RevObject.TYPE.valueOf(requireLine(reader).trim());
                T parsed = read(id, reader, type);
                Preconditions.checkState(parsed != null, "parsed to null");
                if (id != null) {
                    Preconditions.checkState(id.equals(parsed.getId()),
                            "Expected and parsed object ids don't match: %s %s", id,
                            parsed.getId());
                }
                return parsed;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        protected String parseLine(String line, String expectedHeader) throws IOException {
            List<String> fields = Splitter.on('\t').splitToList(line);
            Preconditions.checkArgument(fields.size() == 2, "Expected %s\\t<...>, got '%s'",
                    expectedHeader, line);
            Preconditions.checkArgument(expectedHeader.equals(fields.get(0)),
                    "Expected field %s, got '%s'", expectedHeader, fields.get(0));
            String value = fields.get(1);
            return value;
        }

        protected abstract T read(ObjectId id, BufferedReader reader, TYPE type) throws IOException;

        protected Node parseNodeLine(String line) {
            List<String> tokens = Splitter.on('\t').splitToList(line);
            final int numTokens = tokens.size();
            Preconditions.checkArgument(numTokens == 6 || numTokens == 7,
                    "Wrong tree element definition: %s", line);
            TYPE type = TYPE.valueOf(tokens.get(1));
            String name = tokens.get(2);
            ObjectId id = ObjectId.valueOf(tokens.get(3));
            ObjectId metadataId = ObjectId.valueOf(tokens.get(4));
            Envelope bbox = parseBBox(tokens.get(5));

            Map<String, Object> extraData = null;
            if (numTokens == 7) {
                String extraDataAsString = tokens.get(6);
                extraData = Marshallers.unmarshall(extraDataAsString, FieldType.MAP);
            }

            return org.locationtech.geogig.model.RevObjectFactory.defaultInstance().createNode(name,
                    id, metadataId, type, bbox, extraData);
        }

        protected Envelope parseBBox(String s) {
            if (s.equals(TextWriter.NULL_BOUNDING_BOX)) {
                return new Envelope();
            }
            List<String> tokens = Splitter.on(';').splitToList(s);
            Preconditions.checkArgument(tokens.size() == 4, "Wrong bounding box definition: %s", s);

            double minx = Double.parseDouble(tokens.get(0));
            double maxx = Double.parseDouble(tokens.get(1));
            double miny = Double.parseDouble(tokens.get(2));
            double maxy = Double.parseDouble(tokens.get(3));

            Envelope bbox = new Envelope(minx, maxx, miny, maxy);
            return bbox;
        }

    }

    private static final TextReader<RevObject> OBJECT_READER = new TextReader<RevObject>() {

        protected @Override RevObject read(ObjectId id, BufferedReader read, TYPE type)
                throws IOException {
            switch (type) {
            case COMMIT:
                return COMMIT_READER.read(id, read, type);
            case FEATURE:
                return FEATURE_READER.read(id, read, type);
            case TREE:
                return TREE_READER.read(id, read, type);
            case FEATURETYPE:
                return FEATURETYPE_READER.read(id, read, type);
            case TAG:
                return TAG_READER.read(id, read, type);
            default:
                throw new IllegalArgumentException("Unknown object type " + type);
            }
        }

    };

    /**
     * Commit reader.
     * <p>
     * Parses a commit of the format:
     * 
     * <pre>
     * {@code COMMIT\n}
     * {@code "tree" + "\t" +  <tree id> + "\n"}
     * {@code "parents" + "\t" +  <parent id> [+ " " + <parent id>...]  + "\n"}
     * {@code "author" + "\t" +  <author name>  + " " + <author email>  + "\t" + <author_timestamp> + "\t" + <author_timezone_offset> + "\n"}
     * {@code "committer" + "\t" +  <committer name>  + " " + <committer email>  + "\t" + <committer_timestamp> + "\t" + <committer_timezone_offset> + "\n"}     * 
     * {@code "message" + "\t" +  <message> + "\n"}
     * </pre>
     * 
     */
    private static final TextReader<RevCommit> COMMIT_READER = new TextReader<RevCommit>() {

        protected @Override RevCommit read(ObjectId id, BufferedReader reader, TYPE type)
                throws IOException {
            Preconditions.checkArgument(TYPE.COMMIT.equals(type), "Wrong type: %s", type.name());
            String tree = parseLine(requireLine(reader), "tree");
            List<String> parents = Splitter.on(' ').omitEmptyStrings()
                    .splitToList(parseLine(requireLine(reader), "parents"));
            RevPerson author = parsePerson(requireLine(reader), "author");
            RevPerson committer = parsePerson(requireLine(reader), "committer");
            String message = parseMessage(reader);

            RevCommitBuilder builder = RevCommit.builder();
            builder.author(author.getName().orElse(null));
            builder.authorEmail(author.getEmail().orElse(null));
            builder.authorTimestamp(author.getTimestamp());
            builder.authorTimeZoneOffset(author.getTimeZoneOffset());
            builder.committer(committer.getName().orElse(null));
            builder.committerEmail(committer.getEmail().orElse(null));
            builder.committerTimestamp(committer.getTimestamp());
            builder.committerTimeZoneOffset(committer.getTimeZoneOffset());
            builder.message(message);
            List<ObjectId> parentIds = parents.stream().map(ObjectId::valueOf)
                    .collect(Collectors.toList());
            builder.parentIds(parentIds);
            builder.treeId(ObjectId.valueOf(tree));
            RevCommit commit = builder.build();
            return commit;
        }

        private RevPerson parsePerson(String line, String expectedHeader) throws IOException {
            String[] tokens = line.split("\t");
            Preconditions.checkArgument(expectedHeader.equals(tokens[0]),
                    "Expected field %s, got '%s'", expectedHeader, tokens[0]);
            String name = tokens[1].trim().isEmpty() ? null : tokens[1];
            String email = tokens[2].trim().isEmpty() ? null : tokens[2];
            long timestamp = Long.parseLong(tokens[3]);
            int offset = Integer.parseInt(tokens[4]);
            return RevPerson.builder().build(name, email, timestamp, offset);
        }

        private String parseMessage(BufferedReader reader) throws IOException {
            StringBuilder msg = new StringBuilder(parseLine(requireLine(reader), "message"));
            String extraLine;
            while ((extraLine = reader.readLine()) != null) {
                msg.append('\n').append(extraLine);
            }
            return msg.toString();
        }

    };

    /**
     * Feature reader.
     * <p>
     * Parses a feature in the format:
     * 
     * <pre>
     * {@code "<attribute class_1>" + "\t" +  <attribute_value_1> + "\n"}
     * .
     * .
     * .     
     * {@code "<attribute class_n>" + "\t" +  <attribute_value_n> + "\n"}
     * 
     * Array values are written as a space-separated list of single values, enclosed between square brackets
     * </pre>
     * 
     */
    private static final TextReader<RevFeature> FEATURE_READER = new TextReader<RevFeature>() {

        protected @Override RevFeature read(ObjectId id, BufferedReader reader, TYPE type)
                throws IOException {
            Preconditions.checkArgument(TYPE.FEATURE.equals(type), "Wrong type: %s", type.name());

            RevFeatureBuilder builder = RevFeature.builder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.addValue(parseAttribute(line));
            }
            return builder.id(id).build();
        }

        private Object parseAttribute(String line) {
            List<String> tokens = Splitter.on('\t').splitToList(line);
            Preconditions.checkArgument(tokens.size() == 2, "Wrong attribute definition: %s", line);
            String typeName = tokens.get(0);
            String value = tokens.get(1);
            FieldType type;
            try {
                type = FieldType.valueOf(typeName);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Wrong type name: " + typeName);
            }
            return TextValueSerializer.fromString(type, value);
        }

    };

    /**
     * Feature type reader.
     * <p>
     * Parses a feature type in the format:
     * 
     * <pre>
     * {@code "id" + "\t" +  <id> + "\n"}
     * {@code "name" + "\t" +  <name> + "\n"}
     * {@code "<attribute_name1>" + "\t" +  <attribute_class1> + "\t" + <min_occur> + "\t" + <max_occur> +  "\n" + <nillable <True|False>>}
     * .
     * .
     * .
     * 
     * </pre>
     * 
     * Geometry attributes have an extra token per line representing the crs
     * 
     */
    private static final TextReader<RevFeatureType> FEATURETYPE_READER = new TextReader<RevFeatureType>() {
        protected @Override RevFeatureType read(@Nullable ObjectId id, BufferedReader reader,
                TYPE type) throws IOException {
            Preconditions.checkArgument(TYPE.FEATURETYPE.equals(type), "Wrong type: %s",
                    type.name());
            FeatureTypeBuilder builder = FeatureType.builder();
            String name = parseLine(requireLine(reader), "name");
            builder.name(Name.valueOf(name));

            String line;
            List<PropertyDescriptor> props = new ArrayList<>();
            while ((line = reader.readLine()) != null) {
                props.add(parseAttributeDescriptor(line));
            }
            builder.descriptors(props);
            FeatureType sft = builder.build();
            return RevFeatureType.builder().id(id).type(sft).build();
        }

        private PropertyDescriptor parseAttributeDescriptor(String line) {
            List<String> tokens = Splitter.on('\t').splitToList(line);
            Preconditions.checkArgument(tokens.size() == 5 || tokens.size() == 6,
                    "Wrong attribute definition: %s", line);

            String namestr = tokens.get(0);
            Name name = Name.valueOf(namestr);
            Class<?> type;
            try {
                type = FieldType.valueOf(tokens.get(1)).getBinding();
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Wrong type: " + tokens.get(1));
            }
            int min = Integer.parseInt(tokens.get(2));
            int max = Integer.parseInt(tokens.get(3));
            boolean nillable = Boolean.parseBoolean(tokens.get(4));
            CoordinateReferenceSystem crs = null;

            if (Geometry.class.isAssignableFrom(type)) {
                String crsText = tokens.get(5);
                crs = CrsTextSerializer.deserialize(crsText);
            }
            return PropertyDescriptor.builder().name(name).typeName(name).binding(type)
                    .minOccurs(min).maxOccurs(max).nillable(nillable).coordinateReferenceSystem(crs)
                    .build();
        }
    };

    /**
     * Tree reader.
     * <p>
     * Parses a tree in the format:
     * 
     * <pre>
     * {@code "id" + "\t" +  <id> + "\n"}
     * 
     * {@code "BUCKET" + "\t" +  <bucket_idx> + "\t" + <ObjectId> +"\t" + <bounds> "\n"}
     * or 
     * {@code "REF" + "\t" +  <ref_type> + "\t" + <ref_name> + "\t" + <ObjectId> + "\t" + <MetadataId> + "\t" + <bounds> + "\n"}
     * .
     * .
     * .
     * </pre>
     * 
     */
    private static final TextReader<RevTree> TREE_READER = new TextReader<RevTree>() {

        protected @Override RevTree read(ObjectId id, BufferedReader reader, TYPE type)
                throws IOException {
            Preconditions.checkArgument(TYPE.TREE.equals(type), "Wrong type: %s", type.name());
            List<Node> features = new ArrayList<>();
            List<Node> trees = new ArrayList<>();
            TreeSet<Bucket> subtrees = new TreeSet<>();
            long size = Long.parseLong(parseLine(requireLine(reader), "size"));
            int numTrees = Integer.parseInt(parseLine(requireLine(reader), "numtrees"));
            String line;
            while ((line = reader.readLine()) != null) {
                Preconditions.checkArgument(!line.isEmpty(), "Empty tree element definition");
                List<String> tokens = Splitter.on('\t').splitToList(line);
                String nodeType = tokens.get(0);
                if (nodeType.equals(TextWriter.TreeNode.REF.name())) {
                    Node entryRef = parseNodeLine(line);
                    if (entryRef.getType().equals(TYPE.TREE)) {
                        trees.add(entryRef);
                    } else {
                        features.add(entryRef);
                    }
                } else if (nodeType.equals(TextWriter.TreeNode.BUCKET.name())) {
                    Preconditions.checkArgument(tokens.size() == 4, "Wrong bucket definition: %s",
                            line);
                    Integer idx = Integer.parseInt(tokens.get(1));
                    ObjectId bucketId = ObjectId.valueOf(tokens.get(2));
                    Envelope bounds = parseBBox(tokens.get(3));
                    Bucket bucket = RevObjectFactory.defaultInstance().createBucket(bucketId,
                            idx.intValue(), bounds);
                    subtrees.add(bucket);
                } else {
                    throw new IllegalArgumentException("Wrong tree element definition: " + line);
                }

            }
            if (subtrees.isEmpty()) {
                return RevObjectFactory.defaultInstance().createTree(id, size, trees, features);
            }
            return RevObjectFactory.defaultInstance().createTree(id, size, numTrees, subtrees);
        }
    };

    /**
     * Tag reader.
     * <p>
     * Parses a tag of the format:
     * 
     * <pre>
     * {@code TAG\n}
     * {@code "name" + "\t" +  <tagname> + "\n"}
     * {@code "commitid" + "\t" +  <comitid> + "\n"}  
     * {@code "message" + "\t" +  <message> + "\n"}
     *  {@code "tagger" + "\t" +  <tagger name>  + " " + <tagger email>  + "\t" + <tagger> + "\t" + <tagger_timezone_offset> + "\n"}
     * </pre>
     * 
     */
    private static final TextReader<RevTag> TAG_READER = new TextReader<RevTag>() {

        protected @Override RevTag read(@Nullable ObjectId id, BufferedReader reader, TYPE type)
                throws IOException {
            Preconditions.checkArgument(TYPE.TAG.equals(type), "Wrong type: %s", type.name());
            String name = parseLine(requireLine(reader), "name");
            String commitId = parseLine(requireLine(reader), "commitid");
            String message = parseLine(requireLine(reader), "message");
            RevPerson tagger = parsePerson(requireLine(reader));
            return RevTag.builder().build(id, name, ObjectId.valueOf(commitId), message, tagger);
        }

        private RevPerson parsePerson(String line) {
            String[] tokens = line.split("\t");
            String header = "tagger";
            Preconditions.checkArgument(header.equals(tokens[0]), "Expected field %s, got '%s'",
                    header, tokens[0]);
            String name = tokens[1].trim().isEmpty() ? null : tokens[1];
            String email = tokens[2].trim().isEmpty() ? null : tokens[2];
            long timestamp = Long.parseLong(tokens[3]);
            int offset = Integer.parseInt(tokens[4]);
            return RevPerson.builder().build(name, email, timestamp, offset);
        }

    };

    private static String requireLine(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) {
            throw new IllegalStateException("Expected line bug got EOF");
        }
        return line;
    }

    private static final EnumMap<RevObject.TYPE, TextWriter<? extends RevObject>> serializers = new EnumMap<>(
            RevObject.TYPE.class);
    static {
        serializers.put(TYPE.COMMIT, COMMIT_WRITER);
        serializers.put(TYPE.FEATURE, FEATURE_WRITER);
        serializers.put(TYPE.FEATURETYPE, FEATURETYPE_WRITER);
        serializers.put(TYPE.TAG, TAG_WRITER);
        serializers.put(TYPE.TREE, TREE_WRITER);
    }

    public @Override RevObject read(@Nullable ObjectId id, InputStream in) throws IOException {
        return OBJECT_READER.read(id, in);
    }

    public @Override RevObject read(@Nullable ObjectId id, byte[] data, int offset, int length)
            throws IOException {
        return read(id, new ByteArrayInputStream(data, offset, length));
    }

    public @Override void write(RevObject o, OutputStream out) throws IOException {
        writer(o.getType()).write(o, out);
    }

    @SuppressWarnings("unchecked")
    private static <T extends RevObject> TextWriter<T> writer(TYPE type) {
        TextWriter<? extends RevObject> serializer = serializers.get(type);
        if (serializer == null) {
            throw new UnsupportedOperationException("No serializer for " + type);
        }
        return (TextWriter<T>) serializer;
    }

    public @Override String getDisplayName() {
        return "Text 1.0";
    }
}
