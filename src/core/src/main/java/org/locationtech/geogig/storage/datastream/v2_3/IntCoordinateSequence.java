/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.datastream.v2_3;

import java.io.Serializable;
import java.util.List;

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
class IntCoordinateSequence implements CoordinateSequence, Serializable {

    private static final long serialVersionUID = 8104884314425638232L;

    private static final int PRECISION = 7;

    private static final double MULTIPLICATION_FACTOR = Math.pow(10, PRECISION);

    public static final CoordinateSequence EMPTY_2D = new IntCoordinateSequence(2, 0);

    private long[][] coords;

    public IntCoordinateSequence(final int dimensions, final int initialSize) {
        this.coords = new long[dimensions][initialSize];
    }

    public IntCoordinateSequence(long[][] coords) {
        this.coords = coords;
    }

    public IntCoordinateSequence(final int dimensions, List<Coordinate> coords) {
        this(dimensions, coords.size());
        for (int i = 0; i < coords.size(); i++) {
            Coordinate coordinate = coords.get(i);
            for (int d = 0; d < dimensions; d++) {
                double ordinate = coordinate.getOrdinate(d);
                setOrdinate(i, d, ordinate);
            }
        }
    }

    @Override
    public double getOrdinate(int index, int ordinateIndex) {
        double value = toDouble(coords[ordinateIndex][index]);
        return value;
    }

    @Override
    public void setOrdinate(int index, int ordinateIndex, double value) {
        setOrdinate(index, ordinateIndex, toFixed(value));
    }

    public void setOrdinate(int index, int ordinateIndex, long fixedPrecisionValue) {
        coords[ordinateIndex][index] = fixedPrecisionValue;
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
        return coords[0].length;
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
    public IntCoordinateSequence clone() {
        return new IntCoordinateSequence(coords.clone());
    }

    /**
     * Access to internal state, use carefully
     */
    public long[][] ordinates() {
        return coords;
    }

    public static long toFixed(double ordinate) {
        return (long) Math.round(ordinate * MULTIPLICATION_FACTOR);
    }

    public static double toDouble(long ordinate) {
        return ordinate / MULTIPLICATION_FACTOR;
    }
}
