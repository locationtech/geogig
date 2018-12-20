/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.porcelain;

import static org.fusesource.jansi.Ansi.Color.BLUE;
import static org.fusesource.jansi.Ansi.Color.GREEN;
import static org.fusesource.jansi.Ansi.Color.RED;
import static org.fusesource.jansi.Ansi.Color.YELLOW;
import static org.locationtech.geogig.model.DiffEntry.ChangeType.ADDED;
import static org.locationtech.geogig.model.DiffEntry.ChangeType.MODIFIED;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.fusesource.jansi.Ansi;
import org.locationtech.geogig.cli.AnsiDecorator;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.DiffEntry.ChangeType;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.plumbing.DiffFeature;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.plumbing.diff.AttributeDiff;
import org.locationtech.geogig.plumbing.diff.FeatureDiff;
import org.locationtech.geogig.plumbing.diff.GeometryAttributeDiff;
import org.locationtech.geogig.plumbing.diff.LCSGeometryDiffImpl;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.storage.text.TextValueSerializer;
import org.opengis.feature.type.PropertyDescriptor;

import com.google.common.base.Optional;
import com.google.common.base.Suppliers;

interface DiffPrinter {

    /**
     * @param geogig
     * @param console
     * @param entry
     * @throws IOException
     */
    void print(GeoGIG geogig, Console console, DiffEntry entry) throws IOException;

}

class SummaryDiffPrinter implements DiffPrinter {

    @Override
    public void print(GeoGIG geogig, Console console, DiffEntry entry) throws IOException {

        Ansi ansi = AnsiDecorator.newAnsi(console.isAnsiSupported());

        final NodeRef newObject = entry.getNewObject();
        final NodeRef oldObject = entry.getOldObject();

        String oldMode = shortOid(oldObject == null ? ObjectId.NULL : oldObject.getMetadataId());
        String newMode = shortOid(newObject == null ? ObjectId.NULL : newObject.getMetadataId());

        String oldId = shortOid(oldObject == null ? ObjectId.NULL : oldObject.getObjectId());
        String newId = shortOid(newObject == null ? ObjectId.NULL : newObject.getObjectId());

        ansi.a(oldMode).a(" ");
        ansi.a(newMode).a(" ");

        ansi.a(oldId).a(" ");
        ansi.a(newId).a(" ");

        ansi.fg(entry.changeType() == ADDED ? GREEN
                : (entry.changeType() == MODIFIED ? YELLOW : RED));
        char type = entry.changeType().toString().charAt(0);
        ansi.a("  ").a(type).reset();
        ansi.a("  ").a(formatPath(entry));

        console.println(ansi.toString());

    }

    private static String shortOid(ObjectId oid) {
        return RevObjects.toString(oid, 4, new StringBuilder(19)).append("...").toString();
    }

    private static String formatPath(DiffEntry entry) {
        String path;
        NodeRef oldObject = entry.getOldObject();
        NodeRef newObject = entry.getNewObject();
        if (oldObject == null) {
            path = newObject.path();
        } else if (newObject == null) {
            path = oldObject.path();
        } else {
            if (oldObject.path().equals(newObject.path())) {
                path = oldObject.path();
            } else {
                path = oldObject.path() + " -> " + newObject.path();
            }
        }
        return path;
    }

}

class FullDiffPrinter implements DiffPrinter {

    SummaryDiffPrinter summaryPrinter = new SummaryDiffPrinter();

    private boolean noGeom;

    private boolean noHeader;

    public FullDiffPrinter(boolean noGeom, boolean noHeader) {
        this.noGeom = noGeom;
        this.noHeader = noHeader;
    }

    @Override
    public void print(GeoGIG geogig, Console console, DiffEntry diffEntry) throws IOException {

        if (!noHeader) {
            summaryPrinter.print(geogig, console, diffEntry);
        }

        if (diffEntry.changeType() == ChangeType.MODIFIED) {
            FeatureDiff diff = geogig.command(DiffFeature.class)
                    .setNewVersion(Suppliers.ofInstance(diffEntry.getNewObject()))
                    .setOldVersion(Suppliers.ofInstance(diffEntry.getOldObject())).call();

            Map<PropertyDescriptor, AttributeDiff> diffs = diff.getDiffs();

            Ansi ansi = AnsiDecorator.newAnsi(console.isAnsiSupported());
            Set<Entry<PropertyDescriptor, AttributeDiff>> entries = diffs.entrySet();
            Iterator<Entry<PropertyDescriptor, AttributeDiff>> iter = entries.iterator();
            while (iter.hasNext()) {
                Entry<PropertyDescriptor, AttributeDiff> entry = iter.next();
                PropertyDescriptor pd = entry.getKey();
                AttributeDiff ad = entry.getValue();
                if (ad instanceof GeometryAttributeDiff
                        && ad.getType() == org.locationtech.geogig.plumbing.diff.AttributeDiff.TYPE.MODIFIED
                        && !noGeom) {
                    GeometryAttributeDiff gd = (GeometryAttributeDiff) ad;
                    ansi.fg(YELLOW);
                    ansi.a(pd.getName()).a(": ");
                    ansi.reset();
                    String text = gd.getDiff().getDiffCoordsString();
                    for (int i = 0; i < text.length(); i++) {
                        if (text.charAt(i) == '(') {
                            ansi.fg(GREEN);
                            ansi.a(text.charAt(i));
                        } else if (text.charAt(i) == '[') {
                            ansi.fg(RED);
                            ansi.a(text.charAt(i));
                        } else if (text.charAt(i) == ']' || text.charAt(i) == ')') {
                            ansi.a(text.charAt(i));
                            ansi.reset();
                        } else if (text.charAt(i) == LCSGeometryDiffImpl.INNER_RING_SEPARATOR
                                .charAt(0)
                                || text.charAt(i) == LCSGeometryDiffImpl.SUBGEOM_SEPARATOR
                                        .charAt(0)) {
                            ansi.fg(BLUE);
                            ansi.a(text.charAt(i));
                            ansi.reset();
                        } else {
                            ansi.a(text.charAt(i));
                        }
                    }
                    ansi.reset();
                    ansi.newline();
                } else {
                    ansi.fg(ad
                            .getType() == org.locationtech.geogig.plumbing.diff.AttributeDiff.TYPE.ADDED
                                    ? GREEN
                                    : (ad.getType() == org.locationtech.geogig.plumbing.diff.AttributeDiff.TYPE.REMOVED
                                            ? RED : YELLOW));
                    ansi.a(pd.getName()).a(": ").a(ad.toString());
                    ansi.reset();
                    ansi.newline();
                }
            }
            console.println(ansi.toString());
        } else if (diffEntry.changeType() == ChangeType.ADDED) {
            NodeRef noderef = diffEntry.getNewObject();
            RevFeatureType featureType = geogig.command(RevObjectParse.class)
                    .setObjectId(noderef.getMetadataId()).call(RevFeatureType.class).get();
            Optional<RevObject> obj = geogig.command(RevObjectParse.class)
                    .setObjectId(noderef.getObjectId()).call();
            RevFeature feature = (RevFeature) obj.get();
            for (int i = 0; i < feature.size(); i++) {
                Optional<Object> value = feature.get(i);
                console.println(featureType.descriptors().get(i).getName() + "\t"
                        + TextValueSerializer.asString(value));

            }
            console.println();
        }

    }
}
