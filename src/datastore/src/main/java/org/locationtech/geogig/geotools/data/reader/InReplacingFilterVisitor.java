/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Blasby (Boundless) - initial implementation
 */

package org.locationtech.geogig.geotools.data.reader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.geotools.filter.function.InFunction;
import org.geotools.filter.visitor.DuplicatingFilterVisitor;
import org.opengis.filter.Filter;
import org.opengis.filter.MultiValuedFilter;
import org.opengis.filter.Or;
import org.opengis.filter.PropertyIsEqualTo;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.expression.Literal;
import org.opengis.filter.expression.PropertyName;

/***
 * This speeds up filter evaluation by converting filters of the form;
 *       "property" = 'a' OR "property" = 'b' OR "property" = 'c'
 *                      TO
 *       IN("property", 'a','b','c') = true
 *
 *       This is much faster to evaluate in geogig because a property extraction is very expensive.
 */
public class InReplacingFilterVisitor extends DuplicatingFilterVisitor {

        @Override public Object visit(Or filter, Object extraData) {
                List<Filter> complex = new ArrayList<>();
                List<Filter> simple = new ArrayList<>();
                for (Filter f : filter.getChildren()) {
                        if (isSimpleEquals(f))
                                simple.add(f);
                        else
                                complex.add(f); // these aren't ones we can do anthing about
                }
                List<Filter> simplified = simplify(simple, extraData);
                simplified.addAll(complex);
                return getFactory(extraData).or(simplified);
        }

        // given a list of the "simple" items (ie. "property" = 'a')
        // simplify them to the IN function
        public List<Filter> simplify(List<Filter> filters, Object extraData) {
                List<Filter> inMemmbers = new ArrayList<>();
                Map<String, List<Object>> grouped = group(filters);
                for (Map.Entry<String, List<Object>> entry : grouped.entrySet()) {
                        if (entry.getValue().size() == 1) {
                                inMemmbers.add(createEquals(entry.getKey(), entry.getValue().get(0),
                                        extraData));
                        } else {
                                inMemmbers.add(createInFunction(entry.getKey(), entry.getValue(),
                                        extraData));
                        }

                }
                return inMemmbers;
        }

        // given a property and values, create the
        // IN ("property", v1,v2,v3,v4...) = TRUE FILTER
        // NOTE: IN is a function (expression), so it need the "= TRUE" to make it a filter
        private Filter createInFunction(String pname, List<Object> values, Object extraData) {
                InFunction f = new InFunction();
                Expression[] params = new Expression[values.size() + 1];
                int indx = 0;
                params[indx++] = getFactory(extraData).property(pname);
                for (Object v : values) {
                        params[indx++] = getFactory(extraData).literal(v);
                }
                //f.setParameters(params);
                Expression function = getFactory(extraData).function(f.getName(), params);
                Expression TRUE = getFactory(extraData).literal(true);
                return getFactory(extraData).equals(function, TRUE);
        }

        // creates a simple equals
        // used for properties that have a single "=" (no point in the extra work of the IN function)
        public PropertyIsEqualTo createEquals(String pname, Object literal, Object extraData) {
                PropertyName pnameFilter = getFactory(extraData).property(pname);
                Literal literal1 = getFactory(extraData).literal(literal);
                return getFactory(extraData)
                        .equal(pnameFilter, literal1, true, MultiValuedFilter.MatchAction.ANY);
        }

        // groups a list of the "isSimpleEquals" filters by their property names
        // result is like
        //  {
        //      "property" -> ['a','b','c']
        //  }
        public Map<String, List<Object>> group(List<Filter> filters) {
                Map<String, List<Object>> grouped = new HashMap<>();
                for (Filter fequals : filters) {
                        PropertyIsEqualTo equals = (PropertyIsEqualTo) fequals;
                        PropertyName pnameFilter = (PropertyName) equals.getExpression1();
                        String pname = pnameFilter.getPropertyName();
                        Literal valFilter = (Literal) equals.getExpression2();
                        Object val = valFilter.getValue();
                        List<Object> vals = grouped.getOrDefault(pname, new ArrayList<>());
                        vals.add(val);
                        grouped.put(pname, vals);
                }
                return grouped;
        }

        //check to see if it look like    "property" = value
        // we also make sure its case-sensitive
        public boolean isSimpleEquals(Filter expression) {
                if (expression instanceof PropertyIsEqualTo) {
                        PropertyIsEqualTo equals = (PropertyIsEqualTo) expression;
                        if ((equals.getExpression1() instanceof PropertyName) && (equals
                                .getExpression2() instanceof Literal) && (equals.isMatchingCase()))
                                return true;
                }
                return false;
        }
}
