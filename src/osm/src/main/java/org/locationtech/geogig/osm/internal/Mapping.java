/* Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.osm.internal;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.Expose;

/**
 * A function that transforms a feature representing an OSM entity into a feature with a given
 * feature type, according to a set of mapping rules.
 * 
 */
public class Mapping {

    @Expose
    private List<MappingRule> rules;

    public Mapping(List<MappingRule> rules) {
        this.rules = rules;
    }

    /**
     * Transforms the passed feature according to the mapping rules of this mapping. If several
     * rules can be applied, only the first one found is used.
     * 
     * If no rule can be applied, Optional.absent is returned
     * 
     * @param feature the feature to transform
     * @return
     */
    public List<MappedFeature> map(Feature feature) {
        if (feature == null) {
            return ImmutableList.of();
        }
        @SuppressWarnings("unchecked")
        Map<String, String> tagsMap = (Map<String, String>) ((SimpleFeature) feature)
                .getAttribute("tags");
        Collection<Tag> tags = OSMUtils.buildTagsCollection(tagsMap);
        ImmutableList.Builder<MappedFeature> builder = ImmutableList.<MappedFeature> builder();
        for (MappingRule rule : rules) {
            Optional<Feature> newFeature = rule.apply(feature, tags);
            if (newFeature.isPresent()) {
                builder.add(new MappedFeature(rule.getName(), newFeature.get()));
            }
        }
        return builder.build();
    }

    /**
     * Returns true if this any of the rules in this mapping can be used to convert the passed
     * feature
     * 
     * @param feature
     * @return
     */
    public boolean canBeApplied(Feature feature) {
        @SuppressWarnings("unchecked")
        Map<String, String> tagsMap = (Map<String, String>) ((SimpleFeature) feature)
                .getAttribute("tags");
        Collection<Tag> tags = OSMUtils.buildTagsCollection(tagsMap);
        if (tags.isEmpty()) {
            return false;
        }
        for (MappingRule rule : rules) {
            if (rule.canBeApplied(feature, tags)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Loads a mapping from a file that contains a JSON representation of it
     * 
     * @param filepath the path of the mapping file
     * @return
     */
    public static Mapping fromFile(String filepath) {
        File mappingFile = new File(filepath);

        Preconditions.checkArgument(mappingFile.exists(),
                "The specified mapping file does not exist");

        String mappingJson;
        try {
            mappingJson = readFile(mappingFile);
        } catch (IOException e) {
            throw new IllegalArgumentException("Error reading mapping file:" + e.getMessage(), e);
        }

        return fromString(mappingJson);

    }

    /**
     * Creates a Mapping object from its JSON representation
     * 
     * @param s the JSON representation
     * @return the created mapping
     */
    public static Mapping fromString(String s) {
        GsonBuilder gsonBuilder = new GsonBuilder().excludeFieldsWithoutExposeAnnotation();
        Gson gson = gsonBuilder.create();
        Mapping mapping;
        try {
            mapping = gson.fromJson(s, Mapping.class);
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Error parsing mapping definition: "
                    + e.getMessage(), e);
        }

        return mapping;
    }

    /**
     * Returns the JSON representation of this mapping
     */
    public String toString() {
        GsonBuilder gsonBuilder = new GsonBuilder().excludeFieldsWithoutExposeAnnotation();
        Gson gson = gsonBuilder.create();
        return gson.toJson(this);
    }

    private static String readFile(File file) throws IOException {
        List<String> lines = Files.readLines(file, Charsets.UTF_8);
        return Joiner.on("\n").join(lines);
    }

    public List<MappingRule> getRules() {
        return rules;
    }

    /**
     * Returns true if any of the rules in this mapping generates lines or polygons, so it needs
     * ways as inputs
     * 
     * @return
     */
    public boolean canUseWays() {
        for (MappingRule rule : rules) {
            if (rule.canUseWays()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if any of the rules in this mapping generates points, so it nodes ways as inputs
     * 
     * @return
     */
    public boolean canUseNodes() {
        for (MappingRule rule : rules) {
            if (rule.canUseNodes()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves the original tag name based on the name of a field in a mapped path (an alias for a
     * tag name)
     * 
     * @param path the path to the mapped data
     * @param alias the name of the field
     * @return the name of the tag from which the passed field was created in the specified mapped
     *         tree. If the current mapping does not generate the given mapped tree or the alias
     *         cannot be resolved, that passed alias itself is returned
     */
    public String getTagNameFromAlias(String path, String alias) {
        for (MappingRule rule : rules) {
            if (rule.getName().equals(path)) {
                return rule.getTagNameFromAlias(alias);
            }
        }
        return alias;

    }

    public boolean equals(Object o) {
        if (o instanceof Mapping) {
            Mapping m = (Mapping) o;
            return rules.equals(m.rules);
        } else {
            return false;
        }
    }

}
