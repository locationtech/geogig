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

import static com.google.common.base.Preconditions.checkState;
import static org.opengis.filter.Filter.INCLUDE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

import org.geotools.factory.CommonFactoryFinder;
import org.geotools.filter.visitor.SimplifyingFilterVisitor;
import org.geotools.geometry.jts.JTS;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.DiffTree;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.Feature;
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
import org.opengis.filter.expression.Add;
import org.opengis.filter.expression.Divide;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.expression.ExpressionVisitor;
import org.opengis.filter.expression.Function;
import org.opengis.filter.expression.Literal;
import org.opengis.filter.expression.Multiply;
import org.opengis.filter.expression.NilExpression;
import org.opengis.filter.expression.PropertyName;
import org.opengis.filter.expression.Subtract;
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

/**
 * Utility class to decompose a {@link Filter} in two, complementary ones, the
 * {@link #getPreFilter() first} used for pre filtering tree {@link Node nodes} based on it's
 * spatial bounds and the set of {@link #isMaterialized(PropertyName) materialized} properties, the
 * second used for post filtering of {@link Feature} instances.
 * <p>
 * If given, the list of materialized properties in an {@link IndexInfo index} will be used to
 * determine which parts of the given {@link #filter} can be evaluated during the {@link DiffTree}
 * traversal directly from the {@link Node node's} extra attributes.
 * <p>
 * In most cases, a filter is wether supported or unsupported for pre-filtering. Filters that
 * reference a property that's materialized is pre-filtering supported, and the post-filter is the
 * {@link Filter#INCLUDE INCLUDE} filter. Conversely, filters that reference a propery that's not
 * provided in the node's extra data can't be pre-filtered and hence are decomposed as
 * {@code INCLUDE} for pre and the original filter for post. *
 * <p>
 * Some pre-filters may be partially supported (that's the case of most geometry filters), in which
 * the pre-filtering, even if the geometry attribute is not materialized, can be partially
 * implemented using a more relaxed constraint based on the {@link Node#bounds() node envelope}
 * (e.g. an overlaps filter is decomposed into a bounds intersects pre filter, and the actual
 * overlaps filter for post filtering). In those cases, the original geometry property name will be
 * replaced for the pre-filter by the {@code @bounds} meta-property, which will be evaluated as a
 * {@link Polygon} by convering the Node bounds to a geometry by
 * {@link ExtraDataPropertyAccessorFactory}.
 * 
 */
final class PrePostFilterSplitter {

    private static final Logger LOG = LoggerFactory.getLogger(PrePostFilterSplitter.class);

    public static final String BOUNDS_META_PROPERTY = "@bounds";

    private static final FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();

    private Set<String> extraAttributes = Collections.emptySet();

    private Filter filter;

    private Filter pre, post;

    public Filter getPreFilter() {
        checkState(pre != null, "build() was not called");
        return pre;
    }

    public Filter getPostFilter() {
        checkState(post != null, "build() was not called");
        return post;
    }

    public PrePostFilterSplitter extraAttributes(final Set<String> extraAttributes) {
        this.extraAttributes = new HashSet<>(extraAttributes);
        return this;
    }

    public PrePostFilterSplitter filter(Filter filter) {
        this.filter = filter;
        return this;
    }

    public PrePostFilterSplitter build() {
        checkState(filter != null, "filter was not set");
        PrePostFilterBuilder decomposer = new PrePostFilterBuilder();
        Filter[] prePostFilters = decomposer.visit(filter);

        pre = SimplifyingFilterVisitor.simplify(prePostFilters[0]);
        post = SimplifyingFilterVisitor.simplify(prePostFilters[1]);

        if (LOG.isTraceEnabled()) {
            LOG.trace("Original: {}, Pre filter: {}, Post filter: {}", filter, pre, post);
        }
        return this;
    }

    boolean isMaterialized(PropertyName expression) {
        final String propertyName = expression.getPropertyName();
        if (extraAttributes.contains(propertyName)) {
            return true;
        }

        return false;
    }

    /**
     * All visit(Filter) methods return a two-elements array for Filter, the first for the
     * pre-filter and the second for the post-filter.
     * <p>
     * All visit(Expression) methods return a two-elements array of Expressions, the first for the
     * pre-filter and the second for the post-filter.
     * <p>
     * The expression at index zero (for pre filter) will be equal to the argument expression if
     * such is supported by the materialized properties, or {@code null} otherwise.
     * <p>
     * The expression at index one (for post filter) will be equal to the argument expression if
     * such is NOT supported by the materialized properties, or {@code null} otherwise.
     * <p>
     * The filter for which an expression is decomposed as {@code null} shall be decomposed as
     * {@link Filter#INCLUDE}. For a pre-filter, this has a pass-through effect meaning the
     * pre-filter can't be applied to the {@link RevTree} nodes and hence the post-filter shall
     * evaluate once the {@link RevFeature} is fetched from the database. For a post-filter it means
     * the pre-filter takes care of the full filter.
     * <p>
     * Some pre-filters may be partially supported (that's the case of most geometry filters), in
     * which the pre-filtering, even if the geometry attribute is not materialized, can be partially
     * implemented using a more relaxed constraint (e.g. an overlaps filter is decomposed into a
     * bounds intersects pre filter, and the actual overlaps filter for post filtering)
     */
    private class PrePostFilterBuilder implements FilterVisitor, ExpressionVisitor {
        public Filter[] visit(Filter filter) {
            Filter[] prePostFilters = (Filter[]) filter.accept(this, null);
            Filter pre = prePostFilters[0];
            Filter post = prePostFilters[1];
            return tuple(pre, post);
        }

        @Override
        public Filter[] visit(ExcludeFilter filter, Object extraData) {
            return tuple(filter, filter);
        }

        @Override
        public Filter[] visit(IncludeFilter filter, Object extraData) {
            return tuple(filter, filter);
        }

        @Override
        public Filter[] visit(And filter, Object extraData) {
            List<Filter> children = filter.getChildren();
            List<Filter> pre = new ArrayList<>(children.size());
            List<Filter> post = new ArrayList<>(children.size());

            for (Filter child : children) {
                Filter[] prepost = (Filter[]) child.accept(this, null);
                pre.add(prepost[0]);
                post.add(prepost[1]);
            }
            pre.remove(INCLUDE);
            post.remove(INCLUDE);
            Filter prefilter = pre.isEmpty() ? INCLUDE
                    : (pre.size() == 1 ? pre.get(0) : ff.and(pre));
            Filter postfilter = post.isEmpty() ? INCLUDE
                    : (post.size() == 1 ? post.get(0) : ff.and(post));
            return tuple(prefilter, postfilter);
        }

        @Override
        public Filter[] visit(Or filter, Object extraData) {
            List<Filter> children = filter.getChildren();
            List<Filter> pre = new ArrayList<>(children.size());
            List<Filter> post = new ArrayList<>(children.size());

            for (Filter child : children) {
                Filter[] prepost = (Filter[]) child.accept(this, null);
                if (INCLUDE.equals(prepost[0])) {
                    pre = Collections.singletonList(INCLUDE);
                    post = children;
                    break;
                }
                pre.add(prepost[0]);
                post.add(prepost[1]);
            }
            Filter preFilter = pre.size() == 1 ? pre.get(0) : ff.or(pre);
            Filter postFilter = post.size() == 1 ? post.get(0) : ff.or(post);
            return tuple(preFilter, postFilter);
        }

        @Override
        public Filter[] visit(Id filter, Object extraData) {
            return tuple(filter, INCLUDE);
        }

        @Override
        public Filter[] visit(Not filter, Object extraData) {
            Filter negated = filter.getFilter();

            Filter[] tuple = (Filter[]) negated.accept(this, null);
            Filter pre = tuple[0];
            Filter post = tuple[1];
            if (INCLUDE != pre) {
                pre = ff.not(pre);
            }
            if (INCLUDE != post) {
                post = ff.not(post);
            }
            return tuple(pre, post);
        }

        @Override
        public Filter[] visit(PropertyIsBetween filter, Object extraData) {
            final Expression[] expression = visit(filter.getExpression());
            final Expression[] lowerBoundary = visit(filter.getLowerBoundary());
            final Expression[] upperBoundary = visit(filter.getUpperBoundary());

            final boolean supported = isPreSupported(expression, lowerBoundary, upperBoundary);

            Filter pre = supported ? filter : INCLUDE;
            Filter post = supported ? INCLUDE : filter;
            return tuple(pre, post);
        }

        @Override
        public Filter[] visit(PropertyIsEqualTo filter, Object extraData) {
            return visitBinaryComparisonOperator(filter);
        }

        @Override
        public Filter[] visit(PropertyIsNotEqualTo filter, Object extraData) {
            return visitBinaryComparisonOperator(filter);
        }

        @Override
        public Filter[] visit(PropertyIsGreaterThan filter, Object extraData) {
            return visitBinaryComparisonOperator(filter);
        }

        @Override
        public Filter[] visit(PropertyIsGreaterThanOrEqualTo filter, Object extraData) {
            return visitBinaryComparisonOperator(filter);
        }

        @Override
        public Filter[] visit(PropertyIsLessThan filter, Object extraData) {
            return visitBinaryComparisonOperator(filter);
        }

        @Override
        public Filter[] visit(PropertyIsLessThanOrEqualTo filter, Object extraData) {
            return visitBinaryComparisonOperator(filter);
        }

        @Override
        public Filter[] visit(PropertyIsLike filter, Object extraData) {
            Expression[] expression = visit(filter.getExpression());
            final boolean supported = isPreSupported(expression);

            Filter pre = supported ? filter : INCLUDE;
            Filter post = supported ? INCLUDE : filter;
            return tuple(pre, post);
        }

        @Override
        public Filter[] visit(PropertyIsNull filter, Object extraData) {
            Expression[] expression = visit(filter.getExpression());
            final boolean supported = isPreSupported(expression);

            Filter pre = supported ? filter : INCLUDE;
            Filter post = supported ? INCLUDE : filter;
            return tuple(pre, post);
        }

        @Override
        public Filter[] visit(PropertyIsNil filter, Object extraData) {
            Expression[] expression = visit(filter.getExpression());
            final boolean supported = isPreSupported(expression);

            Filter pre = supported ? filter : INCLUDE;
            Filter post = supported ? INCLUDE : filter;
            return tuple(pre, post);
        }

        @Override
        public Filter[] visit(BBOX filter, Object extraData) {
            Expression metaProperty = toBoundsExpression(filter.getExpression1(), false);
            BBOX bbox = ff.bbox(metaProperty, filter.getBounds());
            return tuple(bbox, INCLUDE);
        }

        private Expression toBoundsExpression(Expression expression,
                boolean convertToBoundsPolygon) {
            if (expression instanceof PropertyName) {
                return ff.property(BOUNDS_META_PROPERTY);
            }
            if (expression instanceof Literal) {
                Geometry geometry = expression.evaluate(null, Geometry.class);
                Geometry envelopeGeom;
                if (geometry == null) {
                    envelopeGeom = null;
                } else if (convertToBoundsPolygon) {
                    envelopeGeom = JTS.toGeometry(geometry.getEnvelopeInternal());
                } else {
                    envelopeGeom = geometry;
                }

                return ff.literal(envelopeGeom);
            }
            return expression;
        }

        @Override
        public Filter[] visit(Contains filter, Object extraData) {
            return boundedOp(filter, (g1, g2) -> ff.contains(g1, g2), false);
        }

        @Override
        public Filter[] visit(Crosses filter, Object extraData) {
            // simplify to a bounds intersects filter, the post-filter shall do the rest
            return boundedOp(filter, (g1, g2) -> ff.intersects(g1, g2), false);
        }

        @Override
        public Filter[] visit(Equals filter, Object extraData) {
            // pre filter checks are a downgrade to an envelope intersects, to account for possibly
            // floating point rounding errors in the bounds saved on the tree Nodes
            return boundedOp(filter, (g1, g2) -> ff.intersects(g1, g2), true);
        }

        @Override
        public Filter[] visit(Intersects filter, Object extraData) {
            return boundedOp(filter, (g1, g2) -> ff.intersects(g1, g2), false);
        }

        @Override
        public Filter[] visit(Overlaps filter, Object extraData) {
            // simplify to a bounds intersects filter, the post-filter shall do the rest
            return boundedOp(filter, (g1, g2) -> ff.intersects(g1, g2), false);
        }

        @Override
        public Filter[] visit(Touches filter, Object extraData) {
            return boundedOp(filter, (g1, g2) -> ff.intersects(g1, g2), false);
        }

        @Override
        public Filter[] visit(Within filter, Object extraData) {
            // simplify to a nodebounds.within(within) filter, the post-filter shall do the rest
            return boundedOp(filter, (g1, g2) -> ff.within(g1, g2), false);
        }

        @Override
        public Filter[] visit(Disjoint filter, Object extraData) {
            // simplify to a bounds intersects filter, the post-filter shall do the rest
            return boundedOp(filter, (g1, g2) -> ff.intersects(g1, g2), true);
        }

        @Override
        public Filter[] visit(DWithin filter, Object extraData) {
            return boundedOp(filter,
                    (g1, g2) -> ff.dwithin(g1, g2, filter.getDistance(), filter.getDistanceUnits()),
                    false);
        }

        @Override
        public Filter[] visit(Beyond filter, Object extraData) {
            return boundedOp(filter,
                    (g1, g2) -> ff.beyond(g1, g2, filter.getDistance(), filter.getDistanceUnits()),
                    false);
        }

        @Override
        public Filter[] visit(After after, Object extraData) {
            return visitBinaryTemporalOperator(after);
        }

        @Override
        public Filter[] visit(AnyInteracts anyInteracts, Object extraData) {
            return visitBinaryTemporalOperator(anyInteracts);
        }

        @Override
        public Filter[] visit(Before before, Object extraData) {
            return visitBinaryTemporalOperator(before);
        }

        @Override
        public Filter[] visit(Begins begins, Object extraData) {
            return visitBinaryTemporalOperator(begins);
        }

        @Override
        public Filter[] visit(BegunBy begunBy, Object extraData) {
            return visitBinaryTemporalOperator(begunBy);
        }

        @Override
        public Filter[] visit(During during, Object extraData) {
            return visitBinaryTemporalOperator(during);
        }

        @Override
        public Filter[] visit(EndedBy endedBy, Object extraData) {
            return visitBinaryTemporalOperator(endedBy);
        }

        @Override
        public Filter[] visit(Ends ends, Object extraData) {
            return visitBinaryTemporalOperator(ends);
        }

        @Override
        public Filter[] visit(Meets meets, Object extraData) {
            return visitBinaryTemporalOperator(meets);
        }

        @Override
        public Filter[] visit(MetBy metBy, Object extraData) {
            return visitBinaryTemporalOperator(metBy);
        }

        @Override
        public Filter[] visit(OverlappedBy overlappedBy, Object extraData) {
            return visitBinaryTemporalOperator(overlappedBy);
        }

        @Override
        public Filter[] visit(TContains contains, Object extraData) {
            return visitBinaryTemporalOperator(contains);
        }

        @Override
        public Filter[] visit(TEquals equals, Object extraData) {
            return visitBinaryTemporalOperator(equals);
        }

        @Override
        public Filter[] visit(TOverlaps overlaps, Object extraData) {
            return visitBinaryTemporalOperator(overlaps);
        }

        @Override
        public Object visitNullFilter(Object extraData) {
            throw new UnsupportedOperationException();
        }

        //////////////// ExpressionVisitor implementation //////////////////

        public Expression[] visit(PropertyName expression, Object extraData) {
            final boolean preFilterSupported = isMaterialized(expression);
            Expression pre = preFilterSupported ? expression : null;
            Expression post = preFilterSupported ? null : expression;
            return new Expression[] { pre, post };
        }

        public Expression[] visit(NilExpression expression, Object extraData) {
            return new Expression[] { expression, expression };
        }

        public Expression[] visit(Add expression, Object extraData) {
            Expression[] e1 = (Expression[]) expression.getExpression1().accept(this, null);
            Expression[] e2 = (Expression[]) expression.getExpression1().accept(this, null);

            Expression pre = e1[0] == null || e2[0] == null ? null : ff.add(e1[0], e2[0]);
            Expression post = e1[1] == null || e2[1] == null ? null : ff.add(e1[1], e2[1]);

            return new Expression[] { pre, post };
        }

        public Expression[] visit(Divide expression, Object extraData) {
            Expression[] e1 = (Expression[]) expression.getExpression1().accept(this, null);
            Expression[] e2 = (Expression[]) expression.getExpression1().accept(this, null);

            Expression pre = e1[0] == null || e2[0] == null ? null : ff.divide(e1[0], e2[0]);
            Expression post = e1[1] == null || e2[1] == null ? null : ff.divide(e1[1], e2[1]);

            return new Expression[] { pre, post };
        }

        public Expression[] visit(Multiply expression, Object extraData) {
            Expression[] e1 = (Expression[]) expression.getExpression1().accept(this, null);
            Expression[] e2 = (Expression[]) expression.getExpression1().accept(this, null);

            Expression pre = e1[0] == null || e2[0] == null ? null : ff.multiply(e1[0], e2[0]);
            Expression post = e1[1] == null || e2[1] == null ? null : ff.multiply(e1[1], e2[1]);

            return new Expression[] { pre, post };
        }

        public Expression[] visit(Subtract expression, Object extraData) {
            Expression[] e1 = (Expression[]) expression.getExpression1().accept(this, null);
            Expression[] e2 = (Expression[]) expression.getExpression1().accept(this, null);

            Expression pre = e1[0] == null || e2[0] == null ? null : ff.subtract(e1[0], e2[0]);
            Expression post = e1[1] == null || e2[1] == null ? null : ff.subtract(e1[1], e2[1]);

            return new Expression[] { pre, post };
        }

        public Expression[] visit(Function expression, Object extraData) {
            List<Expression> parameters = expression.getParameters();

            Function pre = expression;
            Function post = null;
            for (Expression e : parameters) {
                Expression[] prepost = (Expression[]) e.accept(this, null);
                if (prepost[0] == null) {
                    pre = null;
                    break;
                }
            }
            if (pre == null) {
                post = expression;
            }
            return new Expression[] { pre, post };
        }

        public Expression[] visit(Literal expression, Object extraData) {
            return new Expression[] { expression, expression };
        }

        ///////////////////////////////////////////////
        ///// Expression related support functions
        ///////////////////////////////////////////////

        /**
         * @return true if all the expressions at index zero (i.e. pre-filter expressions) are
         *         supported. Unsupported expressions are {@code null} in the arrays, as returned by
         *         all the method implementations for {@link ExpressionVisitor}
         */
        private boolean isPreSupported(Expression[]... prePostExpressions) {
            for (Expression[] prepost : prePostExpressions) {
                if (prepost[0] == null) {
                    return false;
                }
            }
            return true;
        }

        Expression[] visit(Expression expression) {
            return (Expression[]) expression.accept(this, null);
        }

        ///////////////////////////////////////////////
        ///// Filter related support functions
        ///////////////////////////////////////////////

        private Filter[] tuple(Filter pre, Filter post) {
            return new Filter[] { pre, post };
        }

        private Filter[] visitBinaryComparisonOperator(BinaryComparisonOperator filter) {
            Expression expression1[] = visit(filter.getExpression1());
            Expression expression2[] = visit(filter.getExpression2());

            final boolean supported = isPreSupported(expression1, expression2);
            Filter pre;
            Filter post;
            if (supported) {
                pre = filter;
                post = INCLUDE;
            } else {
                pre = INCLUDE;
                post = filter;
            }
            return tuple(pre, post);
        }

        private Filter[] boundedOp(BinarySpatialOperator orig,
                BiFunction<Expression, Expression, BinarySpatialOperator> builder,
                boolean convertLiteralToBounds) {

            Expression[] e1 = visit(orig.getExpression1());
            Expression[] e2 = visit(orig.getExpression2());

            final boolean supportedByMaterializedProperties;
            supportedByMaterializedProperties = isPreSupported(e1, e2);

            Filter pre, post;

            if (supportedByMaterializedProperties) {
                post = INCLUDE;
                pre = orig;
            } else {
                post = orig;
                Expression geometry1 = toBoundsExpression(orig.getExpression1(),
                        convertLiteralToBounds);
                Expression geometry2 = toBoundsExpression(orig.getExpression2(),
                        convertLiteralToBounds);
                pre = builder.apply(geometry1, geometry2);
            }

            return tuple(pre, post);
        }

        private Filter[] visitBinaryTemporalOperator(BinaryTemporalOperator filter) {
            Expression[] e1 = visit(filter.getExpression1());
            Expression[] e2 = visit(filter.getExpression2());

            final boolean supportedByIndex = isPreSupported(e1, e2);
            Filter pre = supportedByIndex ? filter : INCLUDE;
            Filter post = supportedByIndex ? INCLUDE : filter;
            return tuple(pre, post);
        }

    }
}
