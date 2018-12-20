/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.plumbing;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.model.FieldType;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.plumbing.ResolveFeatureType;
import org.locationtech.geogig.repository.DefaultProgressListener;
import org.locationtech.geogig.repository.FeatureInfo;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.storage.text.TextValueSerializer;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.FeatureType;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

@Parameters(commandNames = "insert", commandDescription = "Inserts features in the repository")
public class Insert extends AbstractCommand implements CLICommand {

    @Parameter(description = "<features_definition>")
    private List<String> inputs = new ArrayList<String>();

    @Parameter(names = "-f", description = "File with definition of features to insert")
    private String filepath;

    private GeoGIG geogig;

    @Override
    public void runInternal(GeogigCLI cli) throws IOException {

        Console console = cli.getConsole();
        geogig = cli.getGeogig();

        Iterable<String> lines = null;
        if (filepath != null) {
            File file = new File(filepath);
            checkParameter(file.exists(), "Insert file cannot be found");
            lines = Files.readLines(file, Charsets.UTF_8);
        } else {
            String featuresText = Joiner.on("\n").join(inputs);
            lines = Splitter.on("\n").split(featuresText);
        }
        Map<String, List<Feature>> features = readFeatures(lines);

        Repository repository = geogig.getRepository();
        WorkingTree workingTree = repository.workingTree();

        long count = 0;
        for (String parentPath : features.keySet()) {
            List<Feature> treeFeatures = features.get(parentPath);
            Map<FeatureType, RevFeatureType> types = new HashMap<>();
            Iterator<FeatureInfo> finfos = Iterators.transform(treeFeatures.iterator(), (f) -> {
                FeatureType ft = f.getType();
                RevFeatureType rft = types.get(ft);
                if (rft == null) {
                    rft = RevFeatureType.builder().type(ft).build();
                    types.put(ft, rft);
                    repository.objectDatabase().put(rft);
                }
                String path = NodeRef.appendChild(parentPath, f.getIdentifier().getID());
                FeatureInfo fi = FeatureInfo.insert(RevFeature.builder().build(f), rft.getId(), path);
                return fi;
            });

            workingTree.insert(finfos, DefaultProgressListener.NULL);
            count += treeFeatures.size();
        }

        console.print(Long.toString(count) + " features successfully inserted.");
    }

    public Map<String, List<Feature>> readFeatures(Iterable<String> lines) {

        Map<String, List<Feature>> features = Maps.newHashMap();
        List<String> featureChanges = Lists.newArrayList();
        Map<String, SimpleFeatureBuilder> featureTypes = Maps.newHashMap(); //
        String line;
        Iterator<String> iter = lines.iterator();
        while (iter.hasNext()) {
            line = iter.next().trim();
            if (line.isEmpty() && !featureChanges.isEmpty()) {
                String path = featureChanges.get(0);
                String tree = NodeRef.parentPath(path);
                if (!features.containsKey(tree)) {
                    features.put(tree, new ArrayList<Feature>());
                }
                features.get(tree).add(createFeature(featureChanges, featureTypes));
                featureChanges.clear();
            } else if (!line.isEmpty()) {
                featureChanges.add(line);
            }
        }
        if (!featureChanges.isEmpty()) {
            String path = featureChanges.get(0);
            String tree = NodeRef.parentPath(path);
            if (!features.containsKey(tree)) {
                features.put(tree, new ArrayList<Feature>());
            }
            features.get(tree).add(createFeature(featureChanges, featureTypes));
            featureChanges.clear();
        }

        return features;

    }

    private Feature createFeature(List<String> featureChanges,
            Map<String, SimpleFeatureBuilder> featureTypes) {
        String path = featureChanges.get(0);
        String tree = NodeRef.parentPath(path);
        String featureId = NodeRef.nodeFromPath(path);
        if (!featureTypes.containsKey(tree)) {
            Optional<RevFeatureType> opt = geogig.command(ResolveFeatureType.class)
                    .setRefSpec("WORK_HEAD:" + tree).call();
            checkParameter(opt.isPresent(), "The parent tree does not exist: " + tree);
            SimpleFeatureBuilder builder = new SimpleFeatureBuilder(
                    (SimpleFeatureType) opt.get().type());
            featureTypes.put(tree, builder);
        }
        SimpleFeatureBuilder ftb = featureTypes.get(tree);
        SimpleFeatureType ft = ftb.getFeatureType();
        for (int i = 1; i < featureChanges.size(); i++) {
            String[] tokens = featureChanges.get(i).split("\t");
            Preconditions.checkArgument(tokens.length == 2,
                    "Wrong attribute definition: " + featureChanges.get(i));
            String fieldName = tokens[0];
            AttributeDescriptor desc = ft.getDescriptor(fieldName);
            Preconditions.checkNotNull(desc, "Wrong attribute in feature description");
            FieldType type = FieldType.forBinding(desc.getType().getBinding());
            Object value = TextValueSerializer.fromString(type, tokens[1]);
            ftb.set(tokens[0], value);
        }
        return ftb.buildFeature(featureId);
    }
}
