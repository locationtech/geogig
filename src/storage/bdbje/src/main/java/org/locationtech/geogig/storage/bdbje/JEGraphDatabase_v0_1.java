/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.bdbje;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.SynchronizedGraphDatabase;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;

/**
 * Implementation of {@link GraphDatabase} backed by a BerkeleyDB Java Edition database.
 * <p>
 * Implementation note: Since this is the only kind of mutable state we maintain, this
 * implementation extends {@link SynchronizedGraphDatabase} to avoid concurrent threads stepping
 * over each other's feet and overriding graph relations. An alternate solution would be to
 * serialize writes and have free threaded reads.
 * </p>
 */
public class JEGraphDatabase_v0_1 extends JEGraphDatabase {

    private static final TupleBinding<NodeData> BINDING_V1 = new GraphNodeBinding();

    @Inject
    public JEGraphDatabase_v0_1(final ConfigDatabase config, final EnvironmentBuilder envProvider,
            final Hints hints) {
        super(config, envProvider, BINDING_V1, "0.1", hints);
    }

    private static class GraphNodeBinding extends TupleBinding<NodeData> {

        private static final ObjectIdBinding OID = new ObjectIdBinding();

        private static final OidListBinding OIDLIST = new OidListBinding();

        private static final PropertiesBinding PROPS = new PropertiesBinding();

        @Override
        public NodeData entryToObject(TupleInput input) {
            ObjectId id = OID.entryToObject(input);
            ObjectId mappedTo = OID.entryToObject(input);
            List<ObjectId> outgoing = OIDLIST.entryToObject(input);
            List<ObjectId> incoming = OIDLIST.entryToObject(input);
            Map<String, String> properties = PROPS.entryToObject(input);

            NodeData nodeData = new NodeData(id, mappedTo, outgoing, incoming, properties);
            return nodeData;
        }

        @Override
        public void objectToEntry(NodeData node, TupleOutput output) {
            OID.objectToEntry(node.id, output);
            OID.objectToEntry(node.mappedTo, output);
            OIDLIST.objectToEntry(node.outgoing, output);
            OIDLIST.objectToEntry(node.incoming, output);
            PROPS.objectToEntry(node.properties, output);
        }

        private static class ObjectIdBinding extends TupleBinding<ObjectId> {

            @Nullable
            @Override
            public ObjectId entryToObject(TupleInput input) {
                int size = input.read();
                if (size == 0) {
                    return ObjectId.NULL;
                }
                Preconditions.checkState(ObjectId.NUM_BYTES == size);
                byte[] hash = new byte[size];
                Preconditions.checkState(size == input.read(hash));
                return ObjectId.createNoClone(hash);
            }

            @Override
            public void objectToEntry(@Nullable ObjectId object, TupleOutput output) {
                if (null == object || object.isNull()) {
                    output.write(0);
                } else {
                    output.write(ObjectId.NUM_BYTES);
                    output.write(object.getRawValue());
                }
            }
        }

        private static class OidListBinding extends TupleBinding<List<ObjectId>> {
            private static final ObjectIdBinding OID = new ObjectIdBinding();

            @Override
            public List<ObjectId> entryToObject(TupleInput input) {
                int len = input.readInt();
                List<ObjectId> list = new ArrayList<ObjectId>((int) (1.5 * len));
                for (int i = 0; i < len; i++) {
                    list.add(OID.entryToObject(input));
                }
                return list;
            }

            @Override
            public void objectToEntry(List<ObjectId> list, TupleOutput output) {
                int len = list.size();
                output.writeInt(len);
                for (int i = 0; i < len; i++) {
                    OID.objectToEntry(list.get(i), output);
                }
            }

        }

        private static class PropertiesBinding extends TupleBinding<Map<String, String>> {

            @Override
            public Map<String, String> entryToObject(TupleInput input) {
                int len = input.readInt();
                Map<String, String> props = new HashMap<String, String>();
                for (int i = 0; i < len; i++) {
                    String k = input.readString();
                    String v = input.readString();
                    props.put(k, v);
                }
                return props;
            }

            @Override
            public void objectToEntry(Map<String, String> props, TupleOutput output) {
                output.writeInt(props.size());
                for (Map.Entry<String, String> e : props.entrySet()) {
                    output.writeString(e.getKey());
                    output.writeString(e.getValue());
                }
            }
        }
    }
}
