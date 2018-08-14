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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.data.store.ReTypingFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;
import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.geotools.data.GeoGigDataStore;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.DiffCount;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.DiffObjectCount;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.ItemsList;
import net.sf.jsqlparser.expression.operators.relational.ItemsListVisitor;
import net.sf.jsqlparser.expression.operators.relational.MultiExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.insert.InsertModifierPriority;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SubSelect;

/**
 * 
 */
public @CanRunDuringConflict class QLInsert extends AbstractGeoGigOp<Supplier<DiffObjectCount>> {

    public String statement;

    public QLInsert setStatement(final String statement) {
        checkNotNull(statement, "statement is null");
        this.statement = statement;
        return this;
    }

    @Override
    protected Supplier<DiffObjectCount> _call() {
        final Insert insert = parse(statement);
        insert.accept(new InsertVisitor());

        final String treeish = findTreeIsh(insert);
        final String headRefSpec = parseHeadRefSpec(treeish);
        final String treePath = parseTreePath(treeish);

        final ObjectId initialFeatureTreeId;
        {
            String treeishRefSpec = Ref.WORK_HEAD + ":" + treePath;
            Optional<ObjectId> initialOid = command(ResolveTreeish.class).setTreeish(treeishRefSpec)
                    .call();
            checkArgument(initialOid.isPresent(), "%s does not resolve to a feature tree", treeish);
            initialFeatureTreeId = initialOid.get();
        }

        GeoGigDataStore dataStore = new GeoGigDataStore(repository());
        dataStore.setHead(headRefSpec);

        SimpleFeatureStore store;
        try {
            store = (SimpleFeatureStore) dataStore.getFeatureSource(treePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        SimpleFeatureCollection sourceData = resolveSourceData(insert, store.getSchema());

        final SimpleFeatureType sourceType = sourceData.getSchema();
        final SimpleFeatureType targetType = store.getSchema();
        if (!targetType.equals(sourceType)) {
            sourceData = new ReTypingFeatureCollection(sourceData, targetType);
        }

        sourceData = new SetUseProvidedFidSimpleFeatureCollection(sourceData);
        // Transaction gttx = new DefaultTransaction();
        try {
            // store.setTransaction(gttx);
            store.addFeatures(sourceData);
            // gttx.commit();
        } catch (IOException e) {
            // try {
            // gttx.rollback();
            // } catch (IOException re) {
            // re.printStackTrace();
            // }
            Throwable rootCause = Throwables.getRootCause(e);
            Throwables.propagateIfInstanceOf(rootCause, IllegalArgumentException.class);
            throw new RuntimeException(e);
        } finally {
            // try {
            // gttx.close();
            // } catch (IOException e) {
            // e.printStackTrace();
            // }
        }

        final ObjectId resultFeatureTreeId;
        {
            String spec = Ref.WORK_HEAD + ":" + treePath;
            Optional<ObjectId> oid = command(ResolveTreeish.class).setTreeish(spec).call();
            checkArgument(oid.isPresent(), "%s does not resolve to a feature tree", spec);
            resultFeatureTreeId = oid.get();
        }

        final DiffCount count = command(DiffCount.class).setOldTree(initialFeatureTreeId)
                .setNewTree(resultFeatureTreeId);

        return Suppliers.memoize(() -> count.call());
    }

    private SimpleFeatureCollection resolveSourceData(final Insert insert,
            final SimpleFeatureType targetSchema) {

        if (insert.isUseValues()) {
            return buildFeaturesFromItemsList(insert, targetSchema);
        }
        Select select = insert.getSelect();
        checkNotNull(select, "either values or select must be provided: " + insert);
        SimpleFeatureCollection source = command(QLSelect.class).setStatement(select).call();
        return source;
    }

    private SimpleFeatureCollection buildFeaturesFromItemsList(Insert insert,
            SimpleFeatureType targetSchema) {

        List<String> targetAttributeNames = resolveTargetAttributes(insert);
        List<List<org.opengis.filter.expression.Expression>> valueExpressions = parseValueExpressions(
                insert);

        List<SimpleFeature> features = new ArrayList<>(valueExpressions.size());

        for (List<org.opengis.filter.expression.Expression> values : valueExpressions) {

            checkArgument(targetAttributeNames.size() == values.size(),
                    "Provided $d target attributes but %d value expressions",
                    targetAttributeNames.size(), valueExpressions.size());

            SimpleFeatureBuilder b = new SimpleFeatureBuilder(targetSchema);

            String fid = null;

            for (int i = 0; i < targetAttributeNames.size(); i++) {
                String att = targetAttributeNames.get(i);
                org.opengis.filter.expression.Expression ex = values.get(i);
                Object value = ex.evaluate(null);

                if ("@ID".equalsIgnoreCase(att)) {
                    fid = String.valueOf(value);
                } else {
                    b.set(att, value);
                }
            }
            SimpleFeature feature = b.buildFeature(fid);
            features.add(feature);
        }

        return DataUtilities.collection(features);
    }

    private List<String> resolveTargetAttributes(Insert insert) {
        List<Column> columns = insert.getColumns();
        checkArgument(columns != null && !columns.isEmpty(), "no target attributes were indicated");
        return Lists.transform(columns, (c) -> c.getColumnName().replaceAll("\"", ""));
    }

    private List<List<org.opengis.filter.expression.Expression>> parseValueExpressions(
            Insert insert) {

        ItemsList itemsList = insert.getItemsList();// the values (as VALUES (...) or SELECT)

        final List<List<org.opengis.filter.expression.Expression>> valueExpressions = new ArrayList<>();

        itemsList.accept(new ItemsListVisitor() {

            @Override
            public void visit(MultiExpressionList multiExprList) {
                for (ExpressionList el : multiExprList.getExprList()) {
                    el.accept(this);
                }
            }

            @Override
            public void visit(ExpressionList expressionList) {
                List<Expression> expressions = expressionList.getExpressions();
                checkArgument(expressions != null && !expressions.isEmpty(),
                        "ExpressionList not provided");

                List<org.opengis.filter.expression.Expression> values = new ArrayList<>();
                for (Expression e : expressions) {
                    String ecqlExpression = e.toString();
                    org.opengis.filter.expression.Expression expression;
                    try {
                        expression = ECQL.toExpression(ecqlExpression);
                    } catch (CQLException ex) {
                        throw new IllegalArgumentException(
                                "Unable to parse expression '" + ecqlExpression + "'", ex);
                    }
                    values.add(expression);
                }
                valueExpressions.add(values);
            }

            @Override
            public void visit(SubSelect subSelect) {
                throw new IllegalArgumentException("SubSelect not supported: " + subSelect);
            }
        });

        return valueExpressions;
    }

    private String parseTreePath(String treeish) {
        int idx = treeish.indexOf(':');
        return idx == -1 ? treeish : treeish.substring(idx + 1);
    }

    private String parseHeadRefSpec(String treeish) {
        int idx = treeish.indexOf(':');
        return idx == -1 ? Ref.WORK_HEAD : treeish.substring(0, idx);
    }

    private String findTreeIsh(Insert insert) {
        Table table = insert.getTable();
        checkArgument(table != null, "Target feature tree not specified");
        String treeRef = table.getName();
        treeRef = treeRef.replace("\"", "");
        return treeRef;
    }

    @VisibleForTesting
    public static Insert parse(final String statement) {
        Insert insert;
        try {
            Statement stmt;
            stmt = CCJSqlParserUtil.parse(statement);
            checkArgument(stmt instanceof Insert, "Expected INSERT statement: %s", statement);
            insert = (Insert) stmt;
        } catch (JSQLParserException e) {
            Throwables.propagateIfInstanceOf(Throwables.getRootCause(e),
                    IllegalArgumentException.class);
            throw new IllegalArgumentException("Unable to parse query: " + e.getMessage(), e);
        }
        // TODO: validate allowed structure?
        return insert;
    }

    private FeatureCollection<SimpleFeatureType, SimpleFeature> parseFeatures(Insert insert) {
        InsertVisitor insertVisitor = new InsertVisitor();
        insert.accept(insertVisitor);
        return null;
    }

    private static class InsertVisitor extends StatementVisitorAdapter {
        @Override
        public void visit(Insert insert) {
            boolean modifierIgnore = insert.isModifierIgnore();
            boolean returningAllColumns = insert.isReturningAllColumns();
            boolean useDuplicate = insert.isUseDuplicate();
            boolean useSelectBrackets = insert.isUseSelectBrackets();
            boolean useValues = insert.isUseValues();

            List<Column> columns = insert.getColumns();
            List<Column> duplicateUpdateColumns = insert.getDuplicateUpdateColumns();
            List<Expression> duplicateUpdateExpressionList = insert
                    .getDuplicateUpdateExpressionList();
            ItemsList itemsList = insert.getItemsList();
            InsertModifierPriority modifierPriority = insert.getModifierPriority();
            List<SelectExpressionItem> returningExpressionList = insert
                    .getReturningExpressionList();
            Select select = insert.getSelect();
            Table table = insert.getTable();
            System.err.println(table);
        }
    }
}
