/**
 *  Copyright (C) 2004 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xml;

import org.jaxen.expr.*;

import java.util.Iterator;
import java.util.List;

public class JaxenSimpleVisitor implements Visitor {

    public void visit(PathExpr expr) {
        if (expr.getFilterExpr() != null)
            expr.getFilterExpr().accept(this);
        if (expr.getLocationPath() != null)
            expr.getLocationPath().accept(this);
    }

    public void visit(LocationPath expr) {
        Iterator steps = expr.getSteps().iterator();
        while (steps.hasNext()) {
            Step step = (Step) steps.next();
            step.accept(this);
        }
    }

    public void visit(LogicalExpr expr) {
        expr.getLHS().accept(this);
        expr.getRHS().accept(this);
    }

    public void visit(EqualityExpr expr) {
        expr.getLHS().accept(this);
        expr.getRHS().accept(this);
    }

    public void visit(FilterExpr expr) {
        handlePredicates(expr.getPredicates());
        expr.getExpr().accept(this);
    }

    public void visit(RelationalExpr expr) {
        expr.getLHS().accept(this);
        expr.getRHS().accept(this);
    }

    public void visit(AdditiveExpr expr) {
        expr.getLHS().accept(this);
        expr.getRHS().accept(this);
    }

    public void visit(MultiplicativeExpr expr) {
        expr.getLHS().accept(this);
        expr.getRHS().accept(this);
    }

    public void visit(UnaryExpr expr) {
        expr.getExpr().accept(this);
    }

    public void visit(UnionExpr expr) {
        expr.getLHS().accept(this);
        expr.getRHS().accept(this);
    }

    public void visit(NumberExpr expr) {
        // empty
    }

    public void visit(LiteralExpr expr) {
        // empty
    }

    public void visit(VariableReferenceExpr expr) {
        // empty
    }

    public void visit(FunctionCallExpr expr) {
        for (Iterator i = expr.getParameters().iterator(); i.hasNext();)
            ((Expr) i.next()).accept(this);
    }

    public void visit(NameStep expr) {
        handlePredicates(expr.getPredicates());
    }

    public void visit(ProcessingInstructionNodeStep expr) {
        handlePredicates(expr.getPredicates());
    }

    public void visit(AllNodeStep expr) {
        handlePredicates(expr.getPredicates());
    }

    public void visit(TextNodeStep expr) {
        handlePredicates(expr.getPredicates());

    }

    public void visit(CommentNodeStep expr) {
        handlePredicates(expr.getPredicates());
    }

    public void visit(Predicate predicate) {
        predicate.getExpr().accept(this);
    }

    private void handlePredicates(List predicates) {
        if (predicates != null) {
            for (Iterator i = predicates.iterator(); i.hasNext();)
                ((Predicate) i.next()).accept(this);
        }
    }
}
