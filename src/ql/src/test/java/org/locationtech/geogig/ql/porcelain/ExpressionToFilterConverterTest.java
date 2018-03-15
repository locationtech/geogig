/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.ql.porcelain;

import static org.junit.Assert.assertEquals;

import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.opengis.filter.Filter;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;

public class ExpressionToFilterConverterTest {

    private ExpressionToFilterConverter converter;

    @Rule
    public TestName testName = new TestName();

    @Before
    public void before() {
        converter = new ExpressionToFilterConverter();
    }

    private Expression parse(final String whereClauseBody) {
        Expression stmt;
        try {
            // parseCondExpression() parses an expression like the one after a where clause
            stmt = CCJSqlParserUtil.parseCondExpression(whereClauseBody);
        } catch (JSQLParserException e) {
            throw new IllegalArgumentException("Unable to parse query: " + e.getMessage(), e);
        }
        return stmt;
    }

    private Filter convert(String whereClauseBody) {
        Expression expression = parse(whereClauseBody);
        System.out.println("\tParsed sql: " + expression);
        Filter filter = converter.convert(expression);
        return filter;
    }

    private Filter fromCql(String cql) {
        try {
            return ECQL.toFilter(cql);
        } catch (CQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void testEquality(String expectedCQL, String argumentSQL) {
        System.out.println("--- " + testName.getMethodName() + " ----");
        Filter expected = fromCql(expectedCQL);
        Filter result = convert(argumentSQL);
        System.out.println("\tExpected Filter: " + expected);
        assertEquals(expected, result);
    }

    @Test
    public void singleFidFilter() {
        String expected = "IN ('Points.1')";
        String expression = "@id = 'Points.1'";
        testEquality(expected, expression);
    }

    @Test
    public void multipleFidFilter() {
        String expected = "IN ('Points.1', '2', '3', 'abcdef-1234')";
        String expression = "@id IN ('Points.1', '2', '3', 'abcdef-1234')";
        testEquality(expected, expression);
    }

    @Test
    public void singleAttributeEqualsLiteral() {
        String expected = "attribute = 'Points.1'";
        String expression = "attribute = 'Points.1'";
        testEquality(expected, expression);
    }
}
