/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.osm.internal;

import static org.openstreetmap.osmosis.core.util.FixedPrecisionCoordinateConvertor.convertToDouble;
import static org.openstreetmap.osmosis.core.util.FixedPrecisionCoordinateConvertor.convertToFixed;

import java.io.Serializable;

import org.openstreetmap.osmosis.core.util.FixedPrecisionCoordinateConvertor;

import com.google.common.base.Preconditions;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.CoordinateSequence;
import com.vividsolutions.jts.geom.Envelope;

/**
 * A {@link CoordinateSequence} implementation that packs ordinates in int arrays, following the
 * math used by the OSM {@link FixedPrecisionCoordinateConvertor}. For instance, transforming
 * {@code double} ordinates (in decimal degrees) to {@code int} values by keeping truncating the
 * original ordinate to seven decimal places.
 * 
 */
public class OSMCoordinateSequence implements CoordinateSequence, Serializable {

    private static final long serialVersionUID = 8104884314425638232L;

    private int[] coords;

    public OSMCoordinateSequence(int[] coords) {
        Preconditions.checkArgument(coords.length % 2 == 0);
        this.coords = coords;
    }

    public OSMCoordinateSequence(Coordinate[] coords) {
        int[] c = new int[2 * coords.length];
        for (int i = 0; i < coords.length; i++) {
            c[2 * i] = convertToFixed(coords[i].x);
            c[2 * i + 1] = convertToFixed(coords[i].y);
        }
        this.coords = c;
    }

    public OSMCoordinateSequence(int size) {
        this.coords = new int[2 * size];
    }

    @Override
    public double getOrdinate(int index, int ordinateIndex) {
        int truncatedOrdinate = coords[2 * index + ordinateIndex];
        double value = convertToDouble(truncatedOrdinate);
        return value;
    }

    @Override
    public void setOrdinate(int index, int ordinateIndex, double value) {
        setOrdinate(index, ordinateIndex, convertToFixed(value));
    }

    public void setOrdinate(int index, int ordinateIndex, int fixedPrecisionValue) {
        coords[2 * index + ordinateIndex] = fixedPrecisionValue;
    }

    /**
     * @return {@code 2}
     */
    @Override
    public int getDimension() {
        return 2;
    }

    @Override
    public Coordinate getCoordinate(int i) {
        return new Coordinate(getOrdinate(i, 0), getOrdinate(i, 1));
    }

    @Override
    public Coordinate getCoordinateCopy(int i) {
        return getCoordinate(i);
    }

    @Override
    public void getCoordinate(int index, Coordinate coord) {
        coord.x = getOrdinate(index, 0);
        coord.y = getOrdinate(index, 1);
    }

    @Override
    public double getX(int index) {
        return getOrdinate(index, 0);
    }

    @Override
    public double getY(int index) {
        return getOrdinate(index, 1);
    }

    @Override
    public int size() {
        return coords.length / 2;
    }

    @Override
    public Coordinate[] toCoordinateArray() {
        int size = size();
        Coordinate[] arr = new Coordinate[size];
        for (int i = 0; i < size; i++) {
            arr[i] = getCoordinate(i);
        }
        return arr;
    }

    @Override
    public Envelope expandEnvelope(Envelope env) {
        int size = size();
        for (int i = 0; i < size; i++) {
            env.expandToInclude(getOrdinate(i, 0), getOrdinate(i, 1));
        }
        return env;
    }

    @Override
    public OSMCoordinateSequence clone() {
        return new OSMCoordinateSequence(coords.clone());
    }

    /**
     * Access to internal state, use carefully
     */
    public int[] ordinates() {
        return coords;
    }

}
