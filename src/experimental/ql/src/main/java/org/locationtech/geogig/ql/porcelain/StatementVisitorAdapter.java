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

import net.sf.jsqlparser.statement.Block;
import net.sf.jsqlparser.statement.Commit;
import net.sf.jsqlparser.statement.DeclareStatement;
import net.sf.jsqlparser.statement.DescribeStatement;
import net.sf.jsqlparser.statement.ExplainStatement;
import net.sf.jsqlparser.statement.SetStatement;
import net.sf.jsqlparser.statement.ShowColumnsStatement;
import net.sf.jsqlparser.statement.ShowStatement;
import net.sf.jsqlparser.statement.StatementVisitor;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.UseStatement;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.comment.Comment;
import net.sf.jsqlparser.statement.create.index.CreateIndex;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.view.AlterView;
import net.sf.jsqlparser.statement.create.view.CreateView;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.execute.Execute;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.merge.Merge;
import net.sf.jsqlparser.statement.replace.Replace;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.truncate.Truncate;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.upsert.Upsert;
import net.sf.jsqlparser.statement.values.ValuesStatement;

class StatementVisitorAdapter implements StatementVisitor {

    private void unsupported(Object o) throws UnsupportedOperationException {
        throw new UnsupportedOperationException(String.valueOf(o));
    }

    @Override
    public void visit(Select select) {
        unsupported(select);
    }

    @Override
    public void visit(Delete delete) {
        unsupported(delete);
    }

    @Override
    public void visit(Update update) {
        unsupported(update);
    }

    @Override
    public void visit(Insert insert) {
        unsupported(insert);
    }

    @Override
    public void visit(Replace replace) {
        unsupported(replace);
    }

    @Override
    public void visit(Drop drop) {
        unsupported(drop);
    }

    @Override
    public void visit(Truncate truncate) {
        unsupported(truncate);
    }

    @Override
    public void visit(CreateIndex createIndex) {
        unsupported(createIndex);
    }

    @Override
    public void visit(CreateTable createTable) {
        unsupported(createTable);
    }

    @Override
    public void visit(CreateView createView) {
        unsupported(createView);
    }

    @Override
    public void visit(Alter alter) {
        unsupported(alter);
    }

    @Override
    public void visit(Statements stmts) {
        unsupported(stmts);
    }

    @Override
    public void visit(Execute execute) {
        unsupported(execute);
    }

    @Override
    public void visit(SetStatement set) {
        unsupported(set);
    }

    @Override
    public void visit(Merge merge) {
        unsupported(merge);
    }

    @Override
    public void visit(Comment comment) {
        // ignore
    }

    @Override
    public void visit(Commit commit) {
        unsupported(commit);
    }

    @Override
    public void visit(AlterView alterView) {
        unsupported(alterView);
    }

    @Override
    public void visit(ShowColumnsStatement set) {
        unsupported(set);
    }

    @Override
    public void visit(Upsert upsert) {
        unsupported(upsert);
    }

    @Override
    public void visit(UseStatement use) {
        unsupported(use);
    }

    @Override
    public void visit(Block block) {
        unsupported(block);
    }

    @Override
    public void visit(ValuesStatement values) {
        unsupported(values);
    }

    @Override
    public void visit(DescribeStatement describe) {
        unsupported(describe);
    }

    @Override
    public void visit(ExplainStatement aThis) {
        unsupported(aThis);
    }

    @Override
    public void visit(ShowStatement aThis) {
        unsupported(aThis);
    }

    @Override
    public void visit(DeclareStatement aThis) {
        unsupported(aThis);
    }

}