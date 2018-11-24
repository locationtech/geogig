/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql.config;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class TableNames implements Cloneable {

    public static final String DEFAULT_SCHEMA = "public";

    public static final String DEFAULT_TABLE_PREFIX = "geogig_";

    private final String schema;

    private final String prefix;

    public TableNames() {
        this(DEFAULT_SCHEMA);
    }

    public TableNames(String schema) {
        this(schema, DEFAULT_TABLE_PREFIX);
    }

    public TableNames(String schema, String prefix) {
        Preconditions.checkNotNull(schema);
        Preconditions.checkNotNull(prefix);
        this.schema = schema;
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }

    public String metadata() {
        return name("medatada");
    }

    public String repositories() {
        return name("repository");
    }

    public String repositoryNamesView() {
        return repositories() + "_name";
    }

    private String name(String name) {
        return new StringBuilder(schema).append('.').append(prefix).append(name).toString();
    }

    public String refs() {
        return name("ref");
    }

    public String objects() {
        return name("object");
    }

    public String index() {
        return name("index");
    }

    public String indexMappings() {
        return name("index_mappings");
    }

    public String indexObjects() {
        return name("index_object");
    }

    private int featuresTablePartitionCount = 16;

    void setFeaturesPartitions(int featuresTablePartitionCount) {
        Preconditions.checkArgument(
                featuresTablePartitionCount >= 0 && featuresTablePartitionCount <= 256);
        this.featuresTablePartitionCount = featuresTablePartitionCount;
    }

    public String features(final int hash) {
        if (0 == featuresTablePartitionCount) {
            return features();
        }
        final int min = Integer.MIN_VALUE;
        final long max = (long) Integer.MAX_VALUE + 1;
        final int numTables = featuresTablePartitionCount;
        final int step = (int) (((long) max - (long) min) / numTables);

        int index = 0;
        long curr = min;
        for (int i = 0; i < numTables; i++) {
            long next = curr + step;
            if (hash >= curr && hash < next) {
                index = i;
                break;
            }
            curr = next;
        }
        return String.format("%s_%d", features(), index);
    }

    public String features() {
        return name("object_feature");
    }

    public String featureTypes() {
        return name("object_featuretype");
    }

    public String tags() {
        return name("object_tag");
    }

    public String trees() {
        return name("object_tree");
    }

    public String commits() {
        return name("object_commit");
    }

    public String config() {
        return name("config");
    }

    public String conflicts() {
        return name("conflict");
    }

    public String graphEdges() {
        return name("graph_edge");
    }

    public String graphProperties() {
        return name("graph_property");
    }

    public String graphMappings() {
        return name("graph_mapping");
    }

    public String blobs() {
        return name("blob");
    }

    public List<String> all() {
        return ImmutableList.of(metadata(), repositories(), config(), refs(), conflicts(),
                objects(), index(), indexMappings(), indexObjects(), commits(), features(),
                featureTypes(), trees(), graphEdges(), graphMappings(), graphProperties(), blobs());
    }

}
