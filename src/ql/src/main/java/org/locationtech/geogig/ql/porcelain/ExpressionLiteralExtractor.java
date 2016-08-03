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

import net.sf.jsqlparser.expression.AllComparisonExpression;
import net.sf.jsqlparser.expression.AnalyticExpression;
import net.sf.jsqlparser.expression.AnyComparisonExpression;
import net.sf.jsqlparser.expression.CaseExpression;
import net.sf.jsqlparser.expression.CastExpression;
import net.sf.jsqlparser.expression.DateValue;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitor;
import net.sf.jsqlparser.expression.ExtractExpression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.HexValue;
import net.sf.jsqlparser.expression.IntervalExpression;
import net.sf.jsqlparser.expression.JdbcNamedParameter;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.JsonExpression;
import net.sf.jsqlparser.expression.KeepExpression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.MySQLGroupConcat;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.NumericBind;
import net.sf.jsqlparser.expression.OracleHierarchicalExpression;
import net.sf.jsqlparser.expression.OracleHint;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.RowConstructor;
import net.sf.jsqlparser.expression.SignedExpression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.TimeValue;
import net.sf.jsqlparser.expression.TimestampValue;
import net.sf.jsqlparser.expression.UserVariable;
import net.sf.jsqlparser.expression.WhenClause;
import net.sf.jsqlparser.expression.WithinGroupExpression;
import net.sf.jsqlparser.expression.operators.arithmetic.Addition;
import net.sf.jsqlparser.expression.operators.arithmetic.BitwiseAnd;
import net.sf.jsqlparser.expression.operators.arithmetic.BitwiseOr;
import net.sf.jsqlparser.expression.operators.arithmetic.BitwiseXor;
import net.sf.jsqlparser.expression.operators.arithmetic.Concat;
import net.sf.jsqlparser.expression.operators.arithmetic.Division;
import net.sf.jsqlparser.expression.operators.arithmetic.Modulo;
import net.sf.jsqlparser.expression.operators.arithmetic.Multiplication;
import net.sf.jsqlparser.expression.operators.arithmetic.Subtraction;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExistsExpression;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.expression.operators.relational.LikeExpression;
import net.sf.jsqlparser.expression.operators.relational.Matches;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.expression.operators.relational.RegExpMatchOperator;
import net.sf.jsqlparser.expression.operators.relational.RegExpMySQLOperator;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.SubSelect;

public class ExpressionLiteralExtractor implements ExpressionVisitor {

    private Object value;

    public static Object getValue(Expression literal) {
        ExpressionLiteralExtractor extractor = new ExpressionLiteralExtractor();
        literal.accept(extractor);
        return extractor.value;
    }

    @Override
    public void visit(NullValue nullValue) {
        value = null;
    }

    @Override
    public void visit(StringValue stringValue) {
        value = stringValue.getValue();
    }

    @Override
    public void visit(DoubleValue doubleValue) {
        value = doubleValue.getValue();
    }

    @Override
    public void visit(LongValue longValue) {
        value = longValue.getValue();
    }

    @Override
    public void visit(DateValue dateValue) {
        value = dateValue.getValue();
    }

    @Override
    public void visit(TimeValue timeValue) {
        value = timeValue.getValue();
    }

    @Override
    public void visit(TimestampValue timestampValue) {
        value = timestampValue.getValue();
    }

    @Override
    public void visit(EqualsTo equalsTo) {
        throw new UnsupportedOperationException("only literal values are allowed");
    }

    @Override
    public void visit(Function function) {
        throw new UnsupportedOperationException("only literal values are allowed");
    }

    @Override
    public void visit(SignedExpression signedExpression) {
        throw new UnsupportedOperationException("only literal values are allowed");
    }

    @Override
    public void visit(JdbcParameter jdbcParameter) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(JdbcNamedParameter jdbcNamedParameter) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(HexValue hexValue) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(Parenthesis parenthesis) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(Addition addition) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(Division division) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(Multiplication multiplication) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(Subtraction subtraction) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(AndExpression andExpression) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(OrExpression orExpression) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(Between between) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(GreaterThan greaterThan) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(GreaterThanEquals greaterThanEquals) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(InExpression inExpression) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(IsNullExpression isNullExpression) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(LikeExpression likeExpression) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(MinorThan minorThan) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(MinorThanEquals minorThanEquals) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(NotEqualsTo notEqualsTo) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(Column tableColumn) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(SubSelect subSelect) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(CaseExpression caseExpression) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(WhenClause whenClause) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(ExistsExpression existsExpression) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(AllComparisonExpression allComparisonExpression) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(AnyComparisonExpression anyComparisonExpression) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(Concat concat) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(Matches matches) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(BitwiseAnd bitwiseAnd) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(BitwiseOr bitwiseOr) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(BitwiseXor bitwiseXor) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(CastExpression cast) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(Modulo modulo) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(AnalyticExpression aexpr) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(WithinGroupExpression wgexpr) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(ExtractExpression eexpr) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(IntervalExpression iexpr) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(OracleHierarchicalExpression oexpr) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(RegExpMatchOperator rexpr) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(JsonExpression jsonExpr) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(RegExpMySQLOperator regExpMySQLOperator) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(UserVariable var) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(NumericBind bind) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(KeepExpression aexpr) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(MySQLGroupConcat groupConcat) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(RowConstructor rowConstructor) {
        throw new UnsupportedOperationException("only literal values are allowed");

    }

    @Override
    public void visit(OracleHint hint) {
        throw new UnsupportedOperationException("only literal values are allowed");
    }

}
