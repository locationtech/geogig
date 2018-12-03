/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing.diff;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.FieldType;
import org.locationtech.geogig.plumbing.diff.DiffMatchPatch.Diff;
import org.locationtech.geogig.plumbing.diff.DiffMatchPatch.LinesToCharsResult;
import org.locationtech.geogig.plumbing.diff.DiffMatchPatch.Operation;
import org.locationtech.geogig.plumbing.diff.DiffMatchPatch.Patch;
import org.locationtech.geogig.storage.text.TextValueSerializer;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterators;

/**
 * An class that computes differences between geometries using a Longest-Common-Subsequence
 * algorithm on string representations of them
 * 
 */
public class LCSGeometryDiffImpl {

    public static final String SUBGEOM_SEPARATOR = "/";

    public static final String INNER_RING_SEPARATOR = "@";

    private LinkedList<Patch> patches;

    private DiffMatchPatch diffMatchPatch;

    private int totalInsertions;

    private int totalDeletions;

    private int replacings;

    private String diffText;

    public LCSGeometryDiffImpl(@Nullable Geometry oldGeom, @Nullable Geometry newGeom) {
        String oldText = oldGeom == null ? "" : oldGeom.toText();
        String newText = newGeom == null ? "" : newGeom.toText();
        diffMatchPatch = new DiffMatchPatch();
        LinkedList<Diff> diffs = diffMatchPatch.diff_main(oldText, newText);
        patches = diffMatchPatch.patch_make(diffs);

        // to calculate number of edits in the geometry, we do a diffing based on a string
        // representation of the coordinates of the geometry, instead of the WKT.
        // This is more more practical for counting added/removed/edited points and generating a
        // human-readable and easy-to-parse string representation of the diff.
        // NOTE! This is limited to geometries with less than 65535 different points, and might
        // yield wrong results for geometries over that limit.
        // This is a temporary hack, until a better solution is developed.

        oldText = geomToStringOfCoordinates(oldGeom);
        newText = geomToStringOfCoordinates(newGeom);
        LinesToCharsResult chars = coordsToChars(oldText, newText);
        diffs = diffMatchPatch.diff_main(chars.chars1, chars.chars2);
        charsToCoords(diffs, chars.lineArray);

        processDiffs(diffs);

    }

    private LCSGeometryDiffImpl(LinkedList<Patch> patches) {
        diffMatchPatch = new DiffMatchPatch();
        this.patches = patches;
    }

    public LCSGeometryDiffImpl(String s) {
        // Use Splitter because String.split would return a 1 element array if the second token is
        // empty, but Splitter returns an empty string
        final List<String> tokens = Splitter.on('\t').splitToList(s);
        Preconditions.checkArgument(tokens.size() == 2);
        final String deletesInsertsUpdates = tokens.get(0); // format: <deletes>/<inserts>/<updates>
        final String patch = tokens.get(1);
        String[] countings = deletesInsertsUpdates.split("/");
        Preconditions.checkArgument(countings.length == 3);
        totalDeletions = Integer.parseInt(countings[0]);
        totalInsertions = Integer.parseInt(countings[1]);
        replacings = Integer.parseInt(countings[2]);
        diffMatchPatch = new DiffMatchPatch();
        String unescaped = patch.replace("\\n", "\n");
        patches = (LinkedList<Patch>) diffMatchPatch.patch_fromText(unescaped);
    }

    private void processDiffs(List<Diff> diffs) {
        totalInsertions = 0;
        totalDeletions = 0;
        replacings = 0;
        int insertions = 0;
        int deletions = 0;
        StringBuilder sb = new StringBuilder();
        for (Diff diff : diffs) {
            String text = diff.text;
            int nCoords = 0;
            String[] tokens = diff.text.split(" ");
            for (String token : tokens) {
                if (token.contains(",")) {
                    nCoords++;
                }
            }
            switch (diff.operation) {
            case INSERT:
                text = text.replace(" " + SUBGEOM_SEPARATOR, ")" + SUBGEOM_SEPARATOR + "(");
                sb.append('(');
                sb.append(text);
                sb.append(") ");
                insertions += nCoords;
                break;
            case DELETE:
                sb.append('[');
                sb.append(text);
                sb.append("] ");
                deletions += nCoords;
                break;
            case EQUAL:
                sb.append(text.trim());
                sb.append(' ');
                replacings += Math.min(deletions, insertions);
                totalDeletions += Math.max(deletions - insertions, 0);
                totalInsertions += Math.max(insertions - deletions, 0);
                insertions = 0;
                deletions = 0;
                break;
            }
        }
        replacings += Math.min(deletions, insertions);
        totalDeletions += Math.max(deletions - insertions, 0);
        totalInsertions += Math.max(insertions - deletions, 0);

        diffText = sb.toString();
        // some final dirty minor corrections
        diffText = diffText.replace("(" + SUBGEOM_SEPARATOR, SUBGEOM_SEPARATOR + "(");
        diffText = diffText.replace("(" + INNER_RING_SEPARATOR, INNER_RING_SEPARATOR + "(");
        diffText = diffText.replace("[" + SUBGEOM_SEPARATOR, SUBGEOM_SEPARATOR + "[");
        diffText = diffText.replace("[" + INNER_RING_SEPARATOR, INNER_RING_SEPARATOR + "[");
        diffText = diffText.replace(" )", ")");
        diffText = diffText.replace(" ]", "]");
    }

    private String geomToStringOfCoordinates(@Nullable Geometry geom) {
        if (null == geom) {
            return "";
        }
        final Function<Coordinate, String> printCoords = (c) -> Double.toString(c.x) + ","
                + Double.toString(c.y);

        StringBuilder sb = new StringBuilder();
        sb.append(geom.getGeometryType() + " ");
        int n = geom.getNumGeometries();
        for (int i = 0; i < n; i++) {
            Geometry subgeom = geom.getGeometryN(i);
            if (subgeom instanceof Polygon) {
                Polygon polyg = (Polygon) subgeom;
                Coordinate[] coords = polyg.getExteriorRing().getCoordinates();
                Iterator<String> iter = Iterators.transform(Iterators.forArray(coords),
                        printCoords);
                sb.append(Joiner.on(' ').join(iter));
                for (int j = 0; j < polyg.getNumInteriorRing(); j++) {
                    coords = polyg.getInteriorRingN(j).getCoordinates();
                    iter = Iterators.transform(Iterators.forArray(coords), printCoords);
                    sb.append(" " + INNER_RING_SEPARATOR + " ");
                    sb.append(Joiner.on(' ').join(iter));
                }
                if (i < n - 1) {
                    sb.append(" " + SUBGEOM_SEPARATOR + " ");
                }
            } else {
                Coordinate[] coords = subgeom.getCoordinates();
                Iterator<String> iter = Iterators.transform(Iterators.forArray(coords),
                        printCoords);
                sb.append(Joiner.on(' ').join(iter));
                sb.append(" " + SUBGEOM_SEPARATOR + " ");
            }
        }

        String s = sb.toString().trim();
        return s;

    }

    public LCSGeometryDiffImpl reversed() {
        LinkedList<Patch> reversedPatches = diffMatchPatch.patch_deepCopy(patches);
        for (Patch patch : reversedPatches) {
            LinkedList<Diff> diffs = patch.diffs;
            for (Diff diff : diffs) {
                if (diff.operation == Operation.DELETE) {
                    diff.operation = Operation.INSERT;
                } else if (diff.operation == Operation.INSERT) {
                    diff.operation = Operation.DELETE;
                }
            }
        }
        return new LCSGeometryDiffImpl(reversedPatches);
    }

    public boolean canBeAppliedOn(@Nullable Geometry obj) {
        String wkt = obj == null ? "" : obj.toText();
        Object[] res = diffMatchPatch.patch_apply(patches, wkt);
        boolean[] bool = (boolean[]) res[1];
        for (int i = 0; i < bool.length; i++) {
            if (!bool[i]) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    public Geometry applyOn(@Nullable Geometry obj) {
        Preconditions.checkState(canBeAppliedOn(obj));
        String wkt = obj == null ? "" : obj.toText();
        String res = (String) diffMatchPatch.patch_apply(patches, wkt)[0];
        if (!res.isEmpty()) {
            return (Geometry) TextValueSerializer.fromString(FieldType.forBinding(Geometry.class),
                    res);
        }
        return null;
    }

    /**
     * Returns a human-readable representation of the difference
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(Integer.toString(totalDeletions) + " point(s) deleted, ");
        sb.append(Integer.toString(totalInsertions) + " new point(s) added, ");
        sb.append(Integer.toString(replacings) + " point(s) moved");

        return sb.toString();
    }

    /**
     * Returns a serialized text version of the difference
     */
    public String asText() {
        StringBuilder sb = new StringBuilder();
        sb.append(Integer.toString(totalDeletions));
        sb.append('/');
        sb.append(Integer.toString(totalInsertions));
        sb.append('/');
        sb.append(Integer.toString(replacings));
        sb.append('\t');
        sb.append(diffMatchPatch.patch_toText(patches).replace("\n", "\\n"));
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof LCSGeometryDiffImpl)) {
            return false;
        }
        LCSGeometryDiffImpl d = (LCSGeometryDiffImpl) o;
        if (this.patches.size() != d.patches.size()) {
            return false;
        }
        for (int i = 0; i < d.patches.size(); i++) {
            Patch patchA = patches.get(i);
            Patch patchB = d.patches.get(i);
            if (!patchA.equals(patchB)) {
                return false;
            }
        }
        return true;
    }

    // ========================================================================================
    // These 2 methods are meant to be used to split a text containing a representation of
    // coordinate in a geometry into chunks representing points that can be then hashed
    // as characters.
    // They are just an adapted version of the diff_lineToChars method in diff_match_patch, using a
    // different split character and some extra behaviour

    protected LinesToCharsResult coordsToChars(String text1, String text2) {
        List<String> lineArray = new ArrayList<String>();
        Map<String, Integer> lineHash = new HashMap<String, Integer>();
        lineArray.add("");

        String chars1 = splitAndHash(text1, lineArray, lineHash, ' ');
        String chars2 = splitAndHash(text2, lineArray, lineHash, ' ');
        return new LinesToCharsResult(chars1, chars2, lineArray);
    }

    private String splitAndHash(String text, List<String> lineArray, Map<String, Integer> lineHash,
            char splitChar) {
        StringBuilder chars = new StringBuilder();
        Iterable<String> tokens = Splitter.on(" ").split(text);
        for (String token : tokens) {
            if (lineHash.containsKey(token)) {
                chars.append(String.valueOf((char) (int) lineHash.get(token)));
            } else {
                lineArray.add(token);
                lineHash.put(token, lineArray.size() - 1);
                chars.append(String.valueOf((char) (lineArray.size() - 1)));
            }
        }
        return chars.toString();
    }

    protected void charsToCoords(LinkedList<Diff> diffs, List<String> lineArray) {
        StringBuilder text;
        for (Diff diff : diffs) {
            text = new StringBuilder();
            for (int y = 0; y < diff.text.length(); y++) {
                String coordText = lineArray.get(diff.text.charAt(y));
                text.append(coordText);
                if (coordText.length() > 2) {
                    text.append(' ');
                }
            }
            diff.text = text.toString();
        }
    }

    /**
     * Returns a string with a human-readable version of this geometry diff. It is basically a
     * collection of coordinates, using the following syntax:
     * 
     * - Coordinates added are shown between brackets, while removed coordinates are shown between
     * square brackets.
     * 
     * - The structure of the text representing the geometry is as follows:
     * 
     * - It starts with the type name of the geometry, followed by the list of coordinates -
     * Coordinates are x,y pairs, separated by a whitespace - In the case of multi-geometries,
     * sub-geometries are separated by the slash (`/`) sign. For instance, `MultiLineString 0,10
     * 0,20 0,30 / 10,10 50,65`` represents a multi-line with two lines
     * 
     * - In the case of polygons, the first string of coordinates represents the outer ring, and
     * inner rings are added next, delimited by the ``@`` sign. For instance, ``MultiPolygon
     * 40.0,40.0 20.0,45.0 45.0,30.0 40.0,40.0 / 20.0,35.0 45.0,20.0 30.0,5.0 10.0,10.0 10.0,30.0
     * 20.0,35.0 @ 30.0,20.0 20.0,25.0 20.0,15.0 30.0,20.0`` represents a geometry with two
     * polygons, the last one of them with an inner ring.
     * 
     * @return
     */
    public String getDiffCoordsString() {
        return diffText;
    }

}
