/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.plumbing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.dsl.Geogig;
import org.locationtech.geogig.feature.PropertyDescriptor;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.DiffEntry.ChangeType;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.plumbing.DiffFeature;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.plumbing.diff.AttributeDiff;
import org.locationtech.geogig.plumbing.diff.AttributeDiff.TYPE;
import org.locationtech.geogig.plumbing.diff.FeatureDiff;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.text.TextValueSerializer;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Plumbing command to shows changes between trees
 * 
 * @see DiffTree
 */
@ReadOnly
@Command(name = "diff-tree", description = "Show changes between trees")
public class DiffTree extends AbstractCommand implements CLICommand {

    private static final String LINE_BREAK = "\n";

    @Parameters(description = "[<treeish> [<treeish>]]")
    private List<String> refSpec = new ArrayList<>();

    @Option(names = { "--path", "-p" }, description = "List of tree/feature paths to filter by")
    private List<String> paths = new ArrayList<>();

    @Option(names = "--describe", description = "add description of versions for each modified element")
    private boolean describe;

    @Option(names = "--tree-stats", description = "shows only statistics of modified elements in each changed tree")
    private boolean treeStats;

    /**
     * Executes the diff-tree command with the specified options.
     */
    protected @Override void runInternal(GeogigCLI cli) throws IOException {
        if (refSpec.size() > 2) {
            throw new InvalidParameterException("Tree refspecs list is too long :" + refSpec);
        }

        if (treeStats && describe) {
            throw new InvalidParameterException(
                    "Cannot use --describe and --tree-stats simultaneously");
        }

        Geogig geogig = cli.getGeogig();

        HashMap<String, Long[]> stats = new HashMap<>();
        try (AutoCloseableIterator<DiffEntry> diffEntries = buildDiffEntries(cli)) {
            while (diffEntries.hasNext()) {
                final DiffEntry diffEntry = diffEntries.next();
                StringBuilder sb = new StringBuilder();
                String path = diffEntry.path();

                if (describe) {
                    sb.append(diffEntry.changeType().toString().charAt(0)).append(' ').append(path)
                            .append(LINE_BREAK);

                    if (diffEntry.changeType() == ChangeType.MODIFIED) {
                        FeatureDiff featureDiff = geogig.command(DiffFeature.class)
                                .setNewVersion(diffEntry.getNewObject())
                                .setOldVersion(diffEntry.getOldObject()).call();
                        Map<PropertyDescriptor, AttributeDiff> diffs = featureDiff.getDiffs();
                        Set<PropertyDescriptor> diffDescriptors = new HashSet<>(diffs.keySet());
                        NodeRef noderef = diffEntry.changeType() != ChangeType.REMOVED
                                ? diffEntry.getNewObject()
                                : diffEntry.getOldObject();
                        RevFeatureType featureType = geogig.command(RevObjectParse.class)
                                .setObjectId(noderef.getMetadataId()).call(RevFeatureType.class)
                                .get();
                        Optional<RevObject> obj = geogig.command(RevObjectParse.class)
                                .setObjectId(noderef.getObjectId()).call();
                        RevFeature feature = (RevFeature) obj.get();
                        List<PropertyDescriptor> descriptors = featureType.descriptors();
                        int idx = 0;
                        for (PropertyDescriptor descriptor : descriptors) {
                            if (diffs.containsKey(descriptor)) {
                                AttributeDiff ad = diffs.get(descriptor);
                                sb.append(ad.getType().toString().charAt(0) + " "
                                        + descriptor.getName().toString() + LINE_BREAK);
                                if (!ad.getType().equals(TYPE.ADDED)) {
                                    Object value = ad.getOldValue();
                                    sb.append(TextValueSerializer.asString(value));
                                    sb.append(LINE_BREAK);
                                }
                                if (!ad.getType().equals(TYPE.REMOVED)) {
                                    Object value = ad.getNewValue();
                                    sb.append(TextValueSerializer.asString(value));
                                    sb.append(LINE_BREAK);
                                }
                                diffDescriptors.remove(descriptor);
                            } else {
                                sb.append("U ").append(descriptor.getName().toString())
                                        .append(LINE_BREAK);
                                sb.append(TextValueSerializer.asString(feature.get(idx)))
                                        .append(LINE_BREAK);
                            }
                            idx++;
                        }
                        for (PropertyDescriptor descriptor : diffDescriptors) {
                            AttributeDiff ad = diffs.get(descriptor);
                            sb.append(ad.getType().toString().charAt(0) + " "
                                    + descriptor.getName().toString() + LINE_BREAK);
                            if (!ad.getType().equals(TYPE.ADDED)) {
                                Object value = ad.getOldValue();
                                sb.append(TextValueSerializer.asString(value));
                                sb.append(LINE_BREAK);
                            }
                            if (!ad.getType().equals(TYPE.REMOVED)) {
                                Object value = ad.getNewValue();
                                sb.append(TextValueSerializer.asString(value));
                                sb.append(LINE_BREAK);
                            }
                        }
                    } else {
                        NodeRef noderef = diffEntry.changeType() == ChangeType.ADDED
                                ? diffEntry.getNewObject()
                                : diffEntry.getOldObject();
                        RevFeatureType featureType = geogig.command(RevObjectParse.class)
                                .setObjectId(noderef.getMetadataId()).call(RevFeatureType.class)
                                .get();
                        Optional<RevObject> obj = geogig.command(RevObjectParse.class)
                                .setObjectId(noderef.getObjectId()).call();
                        RevFeature feature = (RevFeature) obj.get();
                        for (int i = 0; i < feature.size(); i++) {
                            Optional<Object> value = feature.get(i);
                            sb.append(diffEntry.changeType().toString().charAt(0));
                            sb.append(' ');
                            sb.append(featureType.descriptors().get(i).getName().toString());
                            sb.append(LINE_BREAK);
                            sb.append(TextValueSerializer.asString(value));
                            sb.append(LINE_BREAK);
                        }
                        sb.append(LINE_BREAK);
                    }
                    sb.append(LINE_BREAK);
                    cli.getConsole().println(sb.toString());
                } else if (treeStats) {
                    String parent = NodeRef.parentPath(path);
                    if (!stats.containsKey(parent)) {
                        stats.put(parent, new Long[] { 0l, 0l, 0l });
                    }
                    Long[] counts = stats.get(parent);
                    if (diffEntry.changeType() == ChangeType.ADDED) {
                        counts[0]++;
                    } else if (diffEntry.changeType() == ChangeType.REMOVED) {
                        counts[1]++;
                    } else if (diffEntry.changeType() == ChangeType.MODIFIED) {
                        counts[2]++;
                    }
                }

                else {
                    sb.append(path).append(' ');
                    sb.append(diffEntry.oldObjectId().toString());
                    sb.append(' ');
                    sb.append(diffEntry.newObjectId().toString());
                    cli.getConsole().println(sb.toString());
                }

            }
        }
        if (treeStats) {
            for (String path : stats.keySet()) {
                StringBuffer sb = new StringBuffer();
                sb.append(path);
                Long[] counts = stats.get(path);
                for (int i = 0; i < counts.length; i++) {
                    sb.append(" " + counts[i].toString());
                }
                cli.getConsole().println(sb.toString());
            }
        }
    }

    private AutoCloseableIterator<DiffEntry> buildDiffEntries(GeogigCLI cli) {
        org.locationtech.geogig.plumbing.DiffTree diff = cli.getGeogig()
                .command(org.locationtech.geogig.plumbing.DiffTree.class);

        String oldVersion = resolveOldVersion();
        String newVersion = resolveNewVersion();

        diff.setOldVersion(oldVersion).setNewVersion(newVersion);

        AutoCloseableIterator<DiffEntry> diffEntries;
        if (paths.isEmpty()) {
            diffEntries = diff.setProgressListener(cli.getProgressListener()).call();
        } else {
            diffEntries = AutoCloseableIterator.emptyIterator();
            for (String path : paths) {
                AutoCloseableIterator<DiffEntry> moreEntries = diff.setPathFilter(path)
                        .setProgressListener(cli.getProgressListener()).call();
                diffEntries = AutoCloseableIterator.concat(diffEntries, moreEntries);
            }
        }
        return diffEntries;
    }

    private String resolveOldVersion() {
        return refSpec.size() > 0 ? refSpec.get(0) : "WORK_HEAD";
    }

    private String resolveNewVersion() {
        return refSpec.size() > 1 ? refSpec.get(1) : "STAGE_HEAD";
    }

}
