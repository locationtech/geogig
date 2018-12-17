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
import java.util.List;

import org.geotools.data.simple.SimpleFeatureStore;
import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.geotools.data.GeoGigDataStore;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.DiffCount;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.DiffObjectCount;
import org.opengis.filter.Filter;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.util.TablesNamesFinder;

/**
 * 
 */
public @CanRunDuringConflict class QLUpdate extends AbstractGeoGigOp<Supplier<DiffObjectCount>> {

    public String statement;

    public QLUpdate setStatement(final String statement) {
        checkNotNull(statement, "statement is null");
        this.statement = statement;
        return this;
    }

    @Override
    protected Supplier<DiffObjectCount> _call() {
        final Update update = parse(statement);

        final String treeish = findTreeIsh(update);
        final String headRefSpec = parseHeadRefSpec(treeish);
        final String treePath = parseTreePath(treeish);

        final ObjectId initialFeatureTreeId;
        {
            Optional<ObjectId> initialOid = command(ResolveTreeish.class)
                    .setTreeish(Ref.WORK_HEAD + ":" + treePath).call();
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

        Filter filter = parseFilter(update);

        // query.setPropertyNames(parsePropertyNames(update));

        String[] attributeNames = parsePropertyNames(update);
        Object[] attributeValues = parsePropertyValues(update);

        // Transaction gttx = new DefaultTransaction();
        try {
            // store.setTransaction(gttx);
            store.modifyFeatures(attributeNames, attributeValues, filter);
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

    private Object[] parsePropertyValues(Update update) {
        List<Expression> expressions = update.getExpressions();
        Object[] values = new Object[expressions.size()];
        for (int i = 0; i < expressions.size(); i++) {
            Expression expression = expressions.get(i);
            Object value = ExpressionLiteralExtractor.getValue(expression);
            values[i] = value;
        }
        return values;
    }

    private String[] parsePropertyNames(Update update) {
        List<Column> columns = update.getColumns();
        return Lists.transform(columns, (c) -> c.getColumnName())
                .toArray(new String[columns.size()]);
    }

    private String parseTreePath(String treeish) {
        int idx = treeish.indexOf(':');
        return idx == -1 ? treeish : treeish.substring(idx + 1);
    }

    private String parseHeadRefSpec(String treeish) {
        int idx = treeish.indexOf(':');
        return idx == -1 ? Ref.HEAD : treeish.substring(0, idx);
    }

    private Filter parseFilter(Update update) {
        Expression where = update.getWhere();
        Filter filter = Filter.INCLUDE;
        if (where != null) {
            filter = new ExpressionToFilterConverter().convert(where);
        }
        return filter;
    }

    private String findTreeIsh(Update update) {
        TablesNamesFinder tableNamesFinder = new TablesNamesFinder();
        List<String> tables = tableNamesFinder.getTableList(update);
        checkArgument(tables.size() == 1, "Expected only one TABLE, got: " + tables);
        String treeRef = tables.get(0);
        treeRef = treeRef.replace("\"", "");
        return treeRef;
    }

    private Update parse(final String statement) {
        Update update;
        try {
            Statement stmt;
            stmt = CCJSqlParserUtil.parse(statement);
            checkArgument(stmt instanceof Update, "Expected UPDATE statement: %s", statement);
            update = (Update) stmt;
        } catch (JSQLParserException e) {
            Throwables.propagateIfInstanceOf(Throwables.getRootCause(e),
                    IllegalArgumentException.class);
            throw new IllegalArgumentException("Unable to parse query: " + e.getMessage(), e);
        }
        // TODO: validate allowed structure?
        return update;
    }

}
