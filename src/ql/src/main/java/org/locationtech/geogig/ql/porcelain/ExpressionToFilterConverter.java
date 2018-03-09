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

import java.util.Iterator;

import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;
import org.opengis.filter.Filter;

import net.sf.jsqlparser.expression.AllComparisonExpression;
import net.sf.jsqlparser.expression.AnalyticExpression;
import net.sf.jsqlparser.expression.AnyComparisonExpression;
import net.sf.jsqlparser.expression.BinaryExpression;
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
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.expression.operators.relational.ItemsList;
import net.sf.jsqlparser.expression.operators.relational.ItemsListVisitor;
import net.sf.jsqlparser.expression.operators.relational.ItemsListVisitorAdapter;
import net.sf.jsqlparser.expression.operators.relational.LikeExpression;
import net.sf.jsqlparser.expression.operators.relational.Matches;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.expression.operators.relational.RegExpMatchOperator;
import net.sf.jsqlparser.expression.operators.relational.RegExpMySQLOperator;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.SubSelect;

class ExpressionToFilterConverter implements ExpressionVisitor {

    private StringBuilder cql = new StringBuilder();

    public Filter convert(Expression exp) {
        exp.accept(this);
        Filter filter = Filter.INCLUDE;
        if (cql.length() > 0) {
            try {
                filter = ECQL.toFilter(cql.toString());
            } catch (CQLException e) {
                throw new RuntimeException(e);
            }
        }
        return filter;
    }

    @Override
    public void visit(NullValue nullValue) {
        cql.append("IS NULL");
    }

    @Override
    public void visit(Function function) {
        String name = function.getName();
        String attribute = function.getAttribute();
        ExpressionList parameters = function.getParameters();
        KeepExpression keep = function.getKeep();
        // cql.append(name).append("(");
        // cql.append(")");
        cql.append(function.toString());
    }

    @Override
    public void visit(SignedExpression signedExpression) {
        cql.append(signedExpression.getSign());
        signedExpression.accept(this);
    }

    @Override
    public void visit(DoubleValue doubleValue) {
        cql.append(doubleValue.getValue());
    }

    @Override
    public void visit(LongValue longValue) {
        cql.append(longValue.getValue());
    }

    @Override
    public void visit(Parenthesis parenthesis) {
        cql.append(parenthesis.isNot() ? "NOT (" : "(");
        parenthesis.getExpression().accept(this);
        cql.append(")");
    }

    @Override
    public void visit(StringValue stringValue) {
        cql.append("'").append(stringValue.getValue()).append("'");
    }

    ///////////////// Binary expressions //////////////////////////

    private void visitBinaryExpression(BinaryExpression expression, String operand) {
        if (expression.isNot()) {
            cql.append("NOT (");
        }
        expression.getLeftExpression().accept(this);
        cql.append(' ').append(operand).append(' ');
        expression.getRightExpression().accept(this);
        if (expression.isNot()) {
            cql.append(')');
        }
    }

    @Override
    public void visit(Addition addition) {
        visitBinaryExpression(addition, "+");
    }

    @Override
    public void visit(Division division) {
        visitBinaryExpression(division, "/");
    }

    @Override
    public void visit(Multiplication multiplication) {
        visitBinaryExpression(multiplication, "*");
    }

    @Override
    public void visit(Subtraction subtraction) {
        visitBinaryExpression(subtraction, "-");
    }

    public void visit(Modulo modulo) {
        visitBinaryExpression(modulo, "%");
    }

    @Override
    public void visit(AndExpression andExpression) {
        visitBinaryExpression(andExpression, "AND");
    }

    @Override
    public void visit(OrExpression orExpression) {
        visitBinaryExpression(orExpression, "OR");
    }

    @Override
    public void visit(EqualsTo equalsTo) {
        Expression leftExpression = equalsTo.getLeftExpression();

        if (leftExpression instanceof UserVariable
                && "ID".equalsIgnoreCase(((UserVariable) leftExpression).getName())) {
            // FID filter, sql of the form @id = <expression>
            cql.append("IN (");
            equalsTo.getRightExpression().accept(this);
            cql.append(')');
        } else {
            visitBinaryExpression(equalsTo, "=");
        }
    }

    @Override
    public void visit(GreaterThan greaterThan) {
        visitBinaryExpression(greaterThan, ">");
    }

    @Override
    public void visit(GreaterThanEquals greaterThanEquals) {
        visitBinaryExpression(greaterThanEquals, ">=");
    }

    @Override
    public void visit(MinorThan minorThan) {
        visitBinaryExpression(minorThan, "<");
    }

    @Override
    public void visit(MinorThanEquals minorThanEquals) {
        visitBinaryExpression(minorThanEquals, "<=");
    }

    @Override
    public void visit(NotEqualsTo notEqualsTo) {
        visitBinaryExpression(notEqualsTo, "<>");
    }

    @Override
    public void visit(LikeExpression likeExpression) {
        visitBinaryExpression(likeExpression, "LIKE");
    }

    @Override
    public void visit(Between between) {
        between.getLeftExpression().accept(this);
        cql.append(between.isNot() ? " NOT" : "").append(" BETWEEN ");
        between.getBetweenExpressionStart().accept(this);
        cql.append(" AND ");
        between.getBetweenExpressionEnd().accept(this);
    }

    @Override
    public void visit(InExpression inExpression) {
        Expression leftExpression = inExpression.getLeftExpression();
        ItemsList itemsList = inExpression.getRightItemsList();

        // check if it's a FID filter, sql of the form @id = <expression>
        final boolean isFidFilter = leftExpression instanceof UserVariable
                && "ID".equalsIgnoreCase(((UserVariable) leftExpression).getName());

        if (!isFidFilter) {
            leftExpression.accept(this);
        }

        ItemsListVisitor itemsListVisitor = new ItemsListVisitorAdapter() {
            @Override
            public void visit(ExpressionList expressionList) {
                Iterator<Expression> it = expressionList.getExpressions().iterator();
                while (it.hasNext()) {
                    Expression expression = it.next();
                    expression.accept(ExpressionToFilterConverter.this);
                    if (it.hasNext()) {
                        cql.append(", ");
                    }
                }
            }
        };

        if (inExpression.isNot()) {
            cql.append(" NOT");
        }
        cql.append(" IN (");
        itemsList.accept(itemsListVisitor);
        cql.append(')');

    }

    @Override
    public void visit(IsNullExpression isNullExpression) {
        isNullExpression.getLeftExpression().accept(this);
        cql.append(" IS ");
        if (isNullExpression.isNot()) {
            cql.append("NOT ");
        }
        cql.append("NULL");
    }

    @Override
    public void visit(Column tableColumn) {
        String columnName = tableColumn.getColumnName();
        cql.append(columnName);
    }

    //////////////////////////// UNSUPPORTED EXPRESSIONS /////////////////////

    private void unsupported(Expression e) {
        throw new IllegalArgumentException("not supported: " + e);
    }

    @Override
    public void visit(JdbcParameter jdbcParameter) {
        unsupported(jdbcParameter);
    }

    @Override
    public void visit(JdbcNamedParameter jdbcNamedParameter) {
        unsupported(jdbcNamedParameter);
    }

    @Override
    public void visit(HexValue hexValue) {
        unsupported(hexValue);
    }

    @Override
    public void visit(DateValue dateValue) {
        unsupported(dateValue);
    }

    @Override
    public void visit(TimeValue timeValue) {
        unsupported(timeValue);
    }

    @Override
    public void visit(TimestampValue timestampValue) {
        unsupported(timestampValue);
    }

    @Override
    public void visit(SubSelect subSelect) {
        unsupported(subSelect);
    }

    @Override
    public void visit(CaseExpression caseExpression) {
        unsupported(caseExpression);
    }

    @Override
    public void visit(WhenClause whenClause) {
        unsupported(whenClause);
    }

    @Override
    public void visit(ExistsExpression existsExpression) {
        unsupported(existsExpression);
    }

    @Override
    public void visit(AllComparisonExpression allComparisonExpression) {
        unsupported(allComparisonExpression);
    }

    @Override
    public void visit(AnyComparisonExpression anyComparisonExpression) {
        unsupported(anyComparisonExpression);
    }

    @Override
    public void visit(Concat concat) {
        unsupported(concat);
    }

    @Override
    public void visit(Matches matches) {
        unsupported(matches);
    }

    @Override
    public void visit(BitwiseAnd bitwiseAnd) {
        unsupported(bitwiseAnd);
    }

    @Override
    public void visit(BitwiseOr bitwiseOr) {
        unsupported(bitwiseOr);
    }

    @Override
    public void visit(BitwiseXor bitwiseXor) {
        unsupported(bitwiseXor);
    }

    @Override
    public void visit(CastExpression cast) {
        unsupported(cast);
    }

    @Override
    public void visit(AnalyticExpression aexpr) {
        unsupported(aexpr);
    }

    @Override
    public void visit(WithinGroupExpression wgexpr) {
        unsupported(wgexpr);
    }

    @Override
    public void visit(ExtractExpression eexpr) {
        unsupported(eexpr);
    }

    @Override
    public void visit(IntervalExpression iexpr) {
        unsupported(iexpr);
    }

    @Override
    public void visit(OracleHierarchicalExpression oexpr) {
        unsupported(oexpr);
    }

    @Override
    public void visit(RegExpMatchOperator rexpr) {
        unsupported(rexpr);
    }

    @Override
    public void visit(JsonExpression jsonExpr) {
        unsupported(jsonExpr);
    }

    @Override
    public void visit(RegExpMySQLOperator regExpMySQLOperator) {
        unsupported(regExpMySQLOperator);
    }

    @Override
    public void visit(UserVariable var) {
        unsupported(var);
    }

    @Override
    public void visit(NumericBind bind) {
        unsupported(bind);
    }

    @Override
    public void visit(KeepExpression aexpr) {
        unsupported(aexpr);
    }

    @Override
    public void visit(MySQLGroupConcat groupConcat) {
        unsupported(groupConcat);
    }

    @Override
    public void visit(RowConstructor rowConstructor) {
        unsupported(rowConstructor);
    }

    @Override
    public void visit(OracleHint hint) {
        unsupported(hint);
    }

}