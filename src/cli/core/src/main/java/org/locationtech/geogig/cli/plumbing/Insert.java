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
import java.util.Optional;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.feature.Feature;
import org.locationtech.geogig.feature.FeatureType;
import org.locationtech.geogig.feature.PropertyDescriptor;
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

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterators;
import com.google.common.io.Files;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "insert", description = "Inserts features in the repository")
public class Insert extends AbstractCommand implements CLICommand {

    @Parameters(description = "<features_definition>")
    private List<String> inputs = new ArrayList<String>();

    @Option(names = "-f", description = "File with definition of features to insert")
    private String filepath;

    private GeoGIG geogig;

    public @Override void runInternal(GeogigCLI cli) throws IOException {

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
        WorkingTree workingTree = repository.context().workingTree();

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
                    repository.context().objectDatabase().put(rft);
                }
                String path = NodeRef.appendChild(parentPath, f.getId());
                FeatureInfo fi = FeatureInfo.insert(RevFeature.builder().build(f), rft.getId(),
                        path);
                return fi;
            });

            workingTree.insert(finfos, DefaultProgressListener.NULL);
            count += treeFeatures.size();
        }

        console.print(Long.toString(count) + " features successfully inserted.");
    }

    public Map<String, List<Feature>> readFeatures(Iterable<String> lines) {

        Map<String, List<Feature>> features = new HashMap<>();
        List<String> featureChanges = new ArrayList<>();
        Map<String, FeatureType> featureTypes = new HashMap<>(); //
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
            Map<String, FeatureType> featureTypes) {
        String path = featureChanges.get(0);
        String tree = NodeRef.parentPath(path);
        String featureId = NodeRef.nodeFromPath(path);
        if (!featureTypes.containsKey(tree)) {
            Optional<RevFeatureType> opt = geogig.command(ResolveFeatureType.class)
                    .setRefSpec("WORK_HEAD:" + tree).call();
            checkParameter(opt.isPresent(), "The parent tree does not exist: " + tree);
            featureTypes.put(tree, opt.get().type());
        }
        FeatureType ft = featureTypes.get(tree);
        Feature f = Feature.build(featureId, ft);
        for (int i = 1; i < featureChanges.size(); i++) {
            String[] tokens = featureChanges.get(i).split("\t");
            Preconditions.checkArgument(tokens.length == 2,
                    "Wrong attribute definition: " + featureChanges.get(i));
            String fieldName = tokens[0];
            PropertyDescriptor desc = ft.getDescriptor(fieldName);
            Preconditions.checkNotNull(desc, "Wrong attribute in feature description");
            FieldType type = FieldType.forBinding(desc.getBinding());
            Object value = TextValueSerializer.fromString(type, tokens[1]);
            f.setAttribute(tokens[0], value);
        }
        return f;
    }
}
