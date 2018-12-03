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

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.data.DataUtilities;
import org.geotools.data.Query;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.data.store.ContentFeatureSource;
import org.geotools.data.store.ReTypingFeatureCollection;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.geotools.data.GeoGigDataStore;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.DiffCount;
import org.locationtech.geogig.plumbing.RevParse;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.DiffObjectCount;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.base.Throwables;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.Offset;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SelectItemVisitor;
import net.sf.jsqlparser.statement.select.SelectVisitor;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.util.TablesNamesFinder;

/**
 * Runs a select query against a feature tree in the current {@code WORK_TREE}, or any other
 * tree-ish if explicitly specified in the from clause.
 * <p>
 * <ul>
 * <li>{@code SELECT <columns-expression> [INTO <new-tree>] FROM 
 * <tree-ish> [WHERE <where-expression] [LIMIT [<offset>,]
 * <limit>]}
 * <li><b>{@code <columns-expression>}</b> := {@code * | <column-list>}
 * <li>{@code <column-list>} := {@code <attribute-name>[, <attribute-name>]+}
 * <li>{@code <attribute-name>} := {@code <unspaced-identifier> | <quoted-identifier>}
 * <li>{@code <unspaced-identifier>} := an attribute name with no spaces or other characters that
 * are not letters, numbers, or underscore.
 * <li>{@code <quoted-identifier>} := {@code 
 * "<unspaced-identifier>[[<space>]+<unspaced-identifier>]+"}
 * <li><b>{@code <new-tree>}</b> := an {@code <attribute-name>} that doesn't exist in the current
 * {@code WORK_TREE}
 * <li><b>{@code <tree-ish>}</b> := {@code [<commit-ish>|<root-ref>:]<tree-path>}
 * <li>{@code <commit-ish>} := a "refspec" that resolves to a commit as supported by
 * {@link RevParse}
 * <li>{@code <root-ref>} := a {@link Ref} that points to a root tree rather than a commit, like in
 * {@code WORK_HEAD}, {@code STAGE_HEAD}
 * <li><b>{@code <where-expression>}</b> := {@code <boolean-expression>|<boolean-function>}
 * <li><b>{@code <offset>}</b> := {@code 0 | <positive-integer>}
 * <li><b>{@code <limit>}</b> := {@code 0 | <positive-integer>}
 * </ul>
 */
public @CanRunDuringConflict class QLSelect extends AbstractGeoGigOp<SimpleFeatureCollection> {

    public String statement;

    private Select select;

    public static final SimpleFeatureType BOUNDS_TYPE;

    public static final SimpleFeatureType COUNT_TYPE;
    static {
        try {
            BOUNDS_TYPE = DataUtilities.createType("@bounds",
                    "minx:double,miny:double,maxx:double,maxy:double,crs:string");
            COUNT_TYPE = DataUtilities.createType("@count", "count:Integer");
        } catch (SchemaException e) {
            throw new RuntimeException(e);
        }
    }

    public QLSelect setStatement(final String statement) {
        checkNotNull(statement, "statement is null");
        this.statement = statement;
        this.select = null;
        return this;
    }

    /**
     * Used by {@link QLInsert} when evaluating a {@code INSERT INTO <tree> SELECT ...} kind of
     * statement, pas pass directly the parsed select.
     */
    QLSelect setStatement(final Select select) {
        checkNotNull(select, "statement is null");
        this.select = select;
        this.statement = null;
        return this;
    }

    @Override
    protected SimpleFeatureCollection _call() {
        final Select select = this.select == null ? parse(statement) : this.select;

        final String treeish = findTreeIsh(select);
        final String headRefSpec = parseHeadRefSpec(treeish);
        final String treePath = parseTreePath(treeish);

        GeoGigDataStore dataStore = new GeoGigDataStore(repository());
        dataStore.setHead(headRefSpec);

        ContentFeatureSource source;
        try {
            source = dataStore.getFeatureSource(treePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final Query query = parseFilter(select, new Query());

        if (isSelectFunction(select)) {
            if (isCount(select)) {
                return selectCount(select, source, query);
            }

            if (isBounds(select)) {
                return selectBounds(select, source, query);
            }
            PlainSelect ps = (PlainSelect) select.getSelectBody();
            throw new IllegalArgumentException(
                    "Aggregate function not supported: " + ps.getSelectItems().get(0));
        }

        // query.setSortBy(new SortBy[]{SortBy.NATURAL_ORDER});
        query.setPropertyNames(parsePropertyNames(select));

        SimpleFeatureCollection features;
        try {
            features = source.getFeatures(query);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (!isSelectInto(select)) {
            return features;
        }

        Table intoTable = getIntoTable(select);
        final String targetTableName = intoTable.getName();
        SimpleFeatureType sourceSchema = features.getSchema();
        SimpleFeatureType targetSchema;
        boolean targetSchemaCreated = false;
        try {
            targetSchema = dataStore.getSchema(targetTableName);
            if (!sourceSchema.equals(targetSchema)) {
                features = new ReTypingFeatureCollection(features, targetSchema);
            }
        } catch (IOException notFound) {
            try {
                targetSchema = DataUtilities.createSubType(sourceSchema,
                        DataUtilities.attributeNames(sourceSchema), null, targetTableName, null);
                workingTree().createTypeTree(targetTableName, targetSchema);
                targetSchemaCreated = true;

                features = new ReTypingFeatureCollection(features, targetSchema);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        final String oldRefSpec = workingTree().getTree().getId() + ":" + targetTableName;
        final String newRefSpec;
        try {
            SimpleFeatureStore target;
            target = (SimpleFeatureStore) dataStore.getFeatureSource(targetTableName);
            target.addFeatures(features);
            newRefSpec = "WORK_HEAD:" + targetTableName;
        } catch (Exception e) {
            if (targetSchemaCreated) {
                repository().workingTree().delete(targetTableName);
            }
            throw new RuntimeException(e);
        }

        DiffObjectCount count = command(DiffCount.class).setOldVersion(oldRefSpec)
                .setNewVersion(newRefSpec).call();

        SimpleFeatureBuilder b = new SimpleFeatureBuilder(COUNT_TYPE);
        b.set("count", count.getFeaturesAdded());
        SimpleFeature f = b.buildFeature("count");
        return DataUtilities.collection(f);
    }

    private SimpleFeatureCollection selectBounds(Select select, ContentFeatureSource source,
            Query query) {

        ReferencedEnvelope bounds;
        try {
            bounds = source.getBounds(query);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        SimpleFeatureBuilder b = new SimpleFeatureBuilder(BOUNDS_TYPE);
        b.set("minx", bounds.getMinX());
        b.set("miny", bounds.getMinY());
        b.set("maxx", bounds.getMaxX());
        b.set("maxy", bounds.getMaxY());
        CoordinateReferenceSystem crs = bounds.getCoordinateReferenceSystem();
        if (crs != null) {
            String srs;
            try {
                srs = CRS.toSRS(crs);
                b.set("crs", srs);
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
        SimpleFeature f = b.buildFeature("bounds");
        return DataUtilities.collection(f);
    }

    private SimpleFeatureCollection selectCount(Select select, ContentFeatureSource source,
            Query query) {

        int count;
        try {
            count = source.getCount(query);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        SimpleFeatureBuilder b = new SimpleFeatureBuilder(COUNT_TYPE);
        b.set("count", count);
        SimpleFeature f = b.buildFeature("count");
        return DataUtilities.collection(f);
    }

    private boolean isSelectInto(Select select) {
        Table intoTable = getIntoTable(select);
        return intoTable != null;
    }

    private @Nullable Table getIntoTable(Select select) {
        SelectBody selectBody = select.getSelectBody();
        if (!(selectBody instanceof PlainSelect)) {
            return null;
        }
        List<Table> intoTables = ((PlainSelect) selectBody).getIntoTables();
        if (intoTables == null || intoTables.isEmpty()) {
            return null;
        }
        checkArgument(1 == intoTables.size(),
                "select into supports only one target table: " + intoTables);
        return intoTables.get(0);
    }

    private boolean isBounds(Select select) {
        SelectItem selectItem = getSelectItems(select).get(0);
        if (selectItem instanceof SelectExpressionItem
                && ((SelectExpressionItem) selectItem).getExpression() instanceof Function) {

            Function f = (Function) ((SelectExpressionItem) selectItem).getExpression();

            String name = f.getName();
            if ("bounds".equalsIgnoreCase(name)) {
                checkArgument(f.isAllColumns(), "only bounds(*) is supported: " + f);
                return true;
            }
        }
        return false;
    }

    private boolean isCount(Select select) {
        SelectItem selectItem = getSelectItems(select).get(0);
        if (selectItem instanceof SelectExpressionItem
                && ((SelectExpressionItem) selectItem).getExpression() instanceof Function) {

            Function f = (Function) ((SelectExpressionItem) selectItem).getExpression();

            String name = f.getName();
            if ("count".equalsIgnoreCase(name)) {
                checkArgument(f.isAllColumns(), "only count(*) is supported: " + f);
                return true;
            }
        }
        return false;
    }

    private boolean isSelectFunction(Select select) {
        List<SelectItem> selectItems = getSelectItems(select);
        for (int i = 0; i < selectItems.size(); i++) {
            SelectItem si = selectItems.get(i);
            if (si instanceof SelectExpressionItem) {
                if (((SelectExpressionItem) si).getExpression() instanceof Function) {
                    checkArgument(selectItems.size() == 1,
                            "Only one select item is allowed when using a function: "
                                    + selectItems);
                    return true;
                }
            }
        }
        return false;
    }

    private List<SelectItem> getSelectItems(Select select) {
        final List<SelectItem> items = new ArrayList<>(2);

        select.getSelectBody().accept(new SelectVisitor() {

            @Override
            public void visit(WithItem withItem) {
            }

            @Override
            public void visit(SetOperationList setOpList) {
            }

            @Override
            public void visit(PlainSelect plainSelect) {
                items.addAll(plainSelect.getSelectItems());
            }
        });
        return items;
    }

    private String[] parsePropertyNames(Select select) {
        final List<String> props = new ArrayList<>();

        select.getSelectBody().accept(new SelectVisitor() {
            @Override
            public void visit(WithItem withItem) {
                throw new IllegalArgumentException("WITH not supported: " + withItem);
            }

            @Override
            public void visit(SetOperationList setOpList) {
                throw new IllegalArgumentException("not supported: " + setOpList);
            }

            @Override
            public void visit(PlainSelect plainSelect) {
                SelectItemVisitor selectItemVisitor = new SelectItemVisitor() {

                    @Override
                    public void visit(SelectExpressionItem selectExpressionItem) {
                        String col = selectExpressionItem.toString();
                        props.add(col);
                    }

                    @Override
                    public void visit(AllTableColumns allTableColumns) {
                        throw new IllegalArgumentException("not supported: " + allTableColumns);
                    }

                    @Override
                    public void visit(AllColumns allColumns) {
                        props.clear();
                    }
                };
                plainSelect.getSelectItems().forEach((i) -> i.accept(selectItemVisitor));
            }
        });

        String[] propNames = props.isEmpty() ? null : props.toArray(new String[props.size()]);
        return propNames;
    }

    private String parseTreePath(String treeish) {
        int idx = treeish.indexOf(':');
        return idx == -1 ? treeish : treeish.substring(idx + 1);
    }

    private String parseHeadRefSpec(String treeish) {
        int idx = treeish.indexOf(':');
        return idx == -1 ? Ref.WORK_HEAD : treeish.substring(0, idx);
    }

    private Query parseFilter(Select select, Query query) {

        select.getSelectBody().accept(new SelectVisitor() {

            @Override
            public void visit(WithItem withItem) {
                throw new IllegalArgumentException("not supported: " + withItem);
            }

            @Override
            public void visit(SetOperationList setOpList) {
                throw new IllegalArgumentException("not supported: " + setOpList);

            }

            @Override
            public void visit(PlainSelect plainSelect) {
                Offset off = plainSelect.getOffset();
                Limit limit = plainSelect.getLimit();

                if (off != null) {
                    checkArgument(off.getOffset() > -1 && off.getOffset() <= Integer.MAX_VALUE);
                    query.setStartIndex((int) off.getOffset());
                }
                if (limit != null) {
                    checkArgument(
                            limit.getRowCount() > -1 && limit.getRowCount() <= Integer.MAX_VALUE);
                    query.setMaxFeatures((int) limit.getRowCount());
                    if (off == null) {
                        int offset = (int) limit.getOffset();
                        checkArgument(offset > -1 && offset <= Integer.MAX_VALUE);
                        query.setStartIndex(offset);
                    }
                }
                Expression where = plainSelect.getWhere();
                if (where != null) {
                    Filter filter = new ExpressionToFilterConverter().convert(where);
                    query.setFilter(filter);
                }
            }
        });

        return query;
    }

    private String findTreeIsh(Select select) {
        TablesNamesFinder tableNamesFinder = new TablesNamesFinder();
        List<String> tables = tableNamesFinder.getTableList(select);
        checkArgument(tables.size() == 1, "Expected only one TABLE, got: " + tables);
        String treeRef = tables.get(0);
        treeRef = treeRef.replace("\"", "");
        return treeRef;
    }

    public static Select parse(final String statement) {
        Select select;
        try {
            Statement stmt;
            stmt = CCJSqlParserUtil.parse(statement);
            checkArgument(stmt instanceof Select, "Expected SELECT statement: %s", statement);
            select = (Select) stmt;
        } catch (JSQLParserException e) {
            Throwables.propagateIfInstanceOf(Throwables.getRootCause(e),
                    IllegalArgumentException.class);
            throw new IllegalArgumentException("Unable to parse query: " + e.getMessage(), e);
        }
        // TODO: validate allowed structure?
        return select;
    }

}
