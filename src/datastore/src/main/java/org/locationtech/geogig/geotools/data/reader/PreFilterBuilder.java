/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.data.reader;

import static org.opengis.filter.Filter.INCLUDE;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import org.geotools.factory.CommonFactoryFinder;
import org.geotools.filter.visitor.SimplifyingFilterVisitor;
import org.geotools.geometry.jts.JTS;
import org.locationtech.geogig.model.Bounded;
import org.opengis.filter.And;
import org.opengis.filter.BinaryComparisonOperator;
import org.opengis.filter.ExcludeFilter;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.FilterVisitor;
import org.opengis.filter.Id;
import org.opengis.filter.IncludeFilter;
import org.opengis.filter.Not;
import org.opengis.filter.Or;
import org.opengis.filter.PropertyIsBetween;
import org.opengis.filter.PropertyIsEqualTo;
import org.opengis.filter.PropertyIsGreaterThan;
import org.opengis.filter.PropertyIsGreaterThanOrEqualTo;
import org.opengis.filter.PropertyIsLessThan;
import org.opengis.filter.PropertyIsLessThanOrEqualTo;
import org.opengis.filter.PropertyIsLike;
import org.opengis.filter.PropertyIsNil;
import org.opengis.filter.PropertyIsNotEqualTo;
import org.opengis.filter.PropertyIsNull;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.expression.Literal;
import org.opengis.filter.expression.PropertyName;
import org.opengis.filter.spatial.BBOX;
import org.opengis.filter.spatial.Beyond;
import org.opengis.filter.spatial.BinarySpatialOperator;
import org.opengis.filter.spatial.Contains;
import org.opengis.filter.spatial.Crosses;
import org.opengis.filter.spatial.DWithin;
import org.opengis.filter.spatial.Disjoint;
import org.opengis.filter.spatial.Equals;
import org.opengis.filter.spatial.Intersects;
import org.opengis.filter.spatial.Overlaps;
import org.opengis.filter.spatial.Touches;
import org.opengis.filter.spatial.Within;
import org.opengis.filter.temporal.After;
import org.opengis.filter.temporal.AnyInteracts;
import org.opengis.filter.temporal.Before;
import org.opengis.filter.temporal.Begins;
import org.opengis.filter.temporal.BegunBy;
import org.opengis.filter.temporal.BinaryTemporalOperator;
import org.opengis.filter.temporal.During;
import org.opengis.filter.temporal.EndedBy;
import org.opengis.filter.temporal.Ends;
import org.opengis.filter.temporal.Meets;
import org.opengis.filter.temporal.MetBy;
import org.opengis.filter.temporal.OverlappedBy;
import org.opengis.filter.temporal.TContains;
import org.opengis.filter.temporal.TEquals;
import org.opengis.filter.temporal.TOverlaps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.vividsolutions.jts.geom.Geometry;

final class PreFilterBuilder implements FilterVisitor {

    private static final Logger LOG = LoggerFactory.getLogger(PreFilterBuilder.class);

    private static final FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();

    private final Set<String> extraAttributes;

    public PreFilterBuilder(Set<String> extraAttributes) {
        this.extraAttributes = extraAttributes;
    }

    public Predicate<Bounded> build(Filter filter) {
        Filter preFilter = (Filter) filter.accept(this, null);
        preFilter = SimplifyingFilterVisitor.simplify(preFilter);
        Predicate<Bounded> predicate;
        if (preFilter instanceof IncludeFilter) {
            predicate = Predicates.alwaysTrue();
        } else if (preFilter instanceof ExcludeFilter) {
            predicate = Predicates.alwaysFalse();
        } else {
            predicate = new PreFilter(preFilter);
        }
        // System.err.printf("Filter %s optimized as %s\n", filter, preFilter);
        if (LOG.isTraceEnabled()) {
            LOG.trace("Filter [{}] optimized as pre filter [{}]", filter, preFilter);
        }
        return predicate;
    }

    boolean isMaterialized(Expression expression) {
        if (expression instanceof PropertyName) {
            final String propertyName = ((PropertyName) expression).getPropertyName();
            if (extraAttributes.contains(propertyName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Filter visit(ExcludeFilter filter, Object extraData) {
        return filter;
    }

    @Override
    public Filter visit(IncludeFilter filter, Object extraData) {
        return filter;
    }

    @Override
    public Filter visit(And filter, Object extraData) {
        List<Filter> children = filter.getChildren();
        List<Filter> pre = new ArrayList<>(children.size());
        children.forEach((f) -> pre.add((Filter) f.accept(this, extraData)));
        return ff.and(pre);
    }

    @Override
    public Filter visit(Or filter, Object extraData) {
        List<Filter> children = filter.getChildren();
        List<Filter> pre = new ArrayList<>(children.size());
        children.forEach((f) -> pre.add((Filter) f.accept(this, extraData)));
        if (children.contains(Filter.INCLUDE)) {
            // if any filter isn't fully supported, we need to short-circuit to INCLUDE
            return Filter.INCLUDE;
        }
        return ff.or(pre);
    }

    @Override
    public Filter visit(Id filter, Object extraData) {
        // Id filters are optimized already
        return INCLUDE;
    }

    @Override
    public Filter visit(Not filter, Object extraData) {
        Filter negated = (Filter) filter.getFilter().accept(this, extraData);
        return ff.not(negated);
    }

    @Override
    public Filter visit(PropertyIsBetween filter, Object extraData) {
        final Expression expression = filter.getExpression();
        final Expression lowerBoundary = filter.getLowerBoundary();
        final Expression upperBoundary = filter.getUpperBoundary();

        if (isMaterialized(expression) && lowerBoundary instanceof Literal
                && upperBoundary instanceof Literal) {
            return filter;
        }
        return INCLUDE;
    }

    private Filter visitBinaryComparisonOperator(BinaryComparisonOperator filter) {
        Expression expression1 = filter.getExpression1();
        Expression expression2 = filter.getExpression2();

        if (isMaterialized(expression1) && expression2 instanceof Literal) {
            return filter;
        }
        return INCLUDE;
    }

    @Override
    public Filter visit(PropertyIsEqualTo filter, Object extraData) {
        return visitBinaryComparisonOperator(filter);
    }

    @Override
    public Filter visit(PropertyIsNotEqualTo filter, Object extraData) {
        return visitBinaryComparisonOperator(filter);
    }

    @Override
    public Filter visit(PropertyIsGreaterThan filter, Object extraData) {
        return visitBinaryComparisonOperator(filter);
    }

    @Override
    public Filter visit(PropertyIsGreaterThanOrEqualTo filter, Object extraData) {
        return visitBinaryComparisonOperator(filter);
    }

    @Override
    public Filter visit(PropertyIsLessThan filter, Object extraData) {
        return visitBinaryComparisonOperator(filter);
    }

    @Override
    public Filter visit(PropertyIsLessThanOrEqualTo filter, Object extraData) {
        return visitBinaryComparisonOperator(filter);
    }

    @Override
    public Filter visit(PropertyIsLike filter, Object extraData) {
        if (isMaterialized(filter.getExpression())) {
            return filter;
        }
        return INCLUDE;
    }

    @Override
    public Filter visit(PropertyIsNull filter, Object extraData) {
        if (isMaterialized(filter.getExpression())) {
            return filter;
        }
        return INCLUDE;
    }

    @Override
    public Filter visit(PropertyIsNil filter, Object extraData) {
        if (isMaterialized(filter.getExpression())) {
            return filter;
        }
        return INCLUDE;
    }

    @Override
    public Filter visit(BBOX filter, Object extraData) {
        // bbox filters are optimized already
        return INCLUDE;
    }

    private Expression toBoundsExpression(Expression expression) {
        if (expression instanceof PropertyName) {
            return ff.property("@bounds");
        }
        if (expression instanceof Literal) {
            Geometry geometry = expression.evaluate(null, Geometry.class);
            Geometry envelopeGeom;
            if (geometry == null) {
                envelopeGeom = null;
            } else {
                envelopeGeom = JTS.toGeometry(geometry.getEnvelopeInternal());
            }
            return ff.literal(envelopeGeom);
        }
        return expression;
    }

    private BinarySpatialOperator boundedOp(BinarySpatialOperator orig,
            BiFunction<Expression, Expression, BinarySpatialOperator> builder) {

        Expression geometry1 = toBoundsExpression(orig.getExpression1());
        Expression geometry2 = toBoundsExpression(orig.getExpression2());
        BinarySpatialOperator preFilter = builder.apply(geometry1, geometry2);
        return preFilter;
    }

    @Override
    public Filter visit(Contains filter, Object extraData) {
        BinarySpatialOperator preFilter = boundedOp(filter, (g1, g2) -> ff.contains(g1, g2));
        return preFilter;
    }

    @Override
    public Filter visit(Crosses filter, Object extraData) {
        // simplify to a bounds intersects filter, the post-filter shall do the rest
        BinarySpatialOperator preFilter = boundedOp(filter, (g1, g2) -> ff.intersects(g1, g2));
        return preFilter;
    }

    @Override
    public Filter visit(Equals filter, Object extraData) {
        BinarySpatialOperator preFilter = boundedOp(filter, (g1, g2) -> ff.equal(g1, g2));
        return preFilter;
    }

    @Override
    public Filter visit(Intersects filter, Object extraData) {
        BinarySpatialOperator preFilter = boundedOp(filter, (g1, g2) -> ff.intersects(g1, g2));
        return preFilter;
    }

    @Override
    public Filter visit(Overlaps filter, Object extraData) {
        // simplify to a bounds intersects filter, the post-filter shall do the rest
        BinarySpatialOperator preFilter = boundedOp(filter, (g1, g2) -> ff.intersects(g1, g2));
        return preFilter;
    }

    @Override
    public Filter visit(Touches filter, Object extraData) {
        BinarySpatialOperator preFilter = boundedOp(filter, (g1, g2) -> ff.touches(g1, g2));
        return preFilter;
    }

    @Override
    public Filter visit(Within filter, Object extraData) {
        // simplify to a bounds intersects filter, the post-filter shall do the rest
        BinarySpatialOperator preFilter = boundedOp(filter, (g1, g2) -> ff.intersects(g1, g2));
        return preFilter;
    }

    @Override
    public Filter visit(Disjoint filter, Object extraData) {
        // simplify to a bounds intersects filter, the post-filter shall do the rest
        BinarySpatialOperator preFilter = boundedOp(filter, (g1, g2) -> ff.intersects(g1, g2));
        return preFilter;
    }

    @Override
    public Filter visit(DWithin filter, Object extraData) {
        BinarySpatialOperator preFilter = boundedOp(filter,
                (g1, g2) -> ff.dwithin(g1, g2, filter.getDistance(), filter.getDistanceUnits()));
        return preFilter;
    }

    @Override
    public Filter visit(Beyond filter, Object extraData) {
        BinarySpatialOperator preFilter = boundedOp(filter,
                (g1, g2) -> ff.beyond(g1, g2, filter.getDistance(), filter.getDistanceUnits()));
        return preFilter;
    }

    private Filter visitBinaryTemporalOperator(BinaryTemporalOperator filter) {
        if (isMaterialized(filter.getExpression1())) {
            return filter;
        }
        return INCLUDE;
    }

    @Override
    public Filter visit(After after, Object extraData) {
        return visitBinaryTemporalOperator(after);
    }

    @Override
    public Filter visit(AnyInteracts anyInteracts, Object extraData) {
        return visitBinaryTemporalOperator(anyInteracts);
    }

    @Override
    public Filter visit(Before before, Object extraData) {
        return visitBinaryTemporalOperator(before);
    }

    @Override
    public Filter visit(Begins begins, Object extraData) {
        return visitBinaryTemporalOperator(begins);
    }

    @Override
    public Filter visit(BegunBy begunBy, Object extraData) {
        return visitBinaryTemporalOperator(begunBy);
    }

    @Override
    public Filter visit(During during, Object extraData) {
        return visitBinaryTemporalOperator(during);
    }

    @Override
    public Filter visit(EndedBy endedBy, Object extraData) {
        return visitBinaryTemporalOperator(endedBy);
    }

    @Override
    public Filter visit(Ends ends, Object extraData) {
        return visitBinaryTemporalOperator(ends);
    }

    @Override
    public Filter visit(Meets meets, Object extraData) {
        return visitBinaryTemporalOperator(meets);
    }

    @Override
    public Filter visit(MetBy metBy, Object extraData) {
        return visitBinaryTemporalOperator(metBy);
    }

    @Override
    public Filter visit(OverlappedBy overlappedBy, Object extraData) {
        return visitBinaryTemporalOperator(overlappedBy);
    }

    @Override
    public Filter visit(TContains contains, Object extraData) {
        return visitBinaryTemporalOperator(contains);
    }

    @Override
    public Filter visit(TEquals equals, Object extraData) {
        return visitBinaryTemporalOperator(equals);
    }

    @Override
    public Filter visit(TOverlaps overlaps, Object extraData) {
        return visitBinaryTemporalOperator(overlaps);
    }

    @Override
    public Filter visitNullFilter(Object extraData) {
        throw new UnsupportedOperationException();
    }
}
