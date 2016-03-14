package org.orbeon.jaxen.util;

import org.orbeon.jaxen.expr.*;

import java.io.PrintWriter;
import java.util.Iterator;
import java.util.List;

public class XPath2XMLVisitor implements Visitor {

    protected PrintWriter printer;
    protected int tabIndex;

    public XPath2XMLVisitor() {
        this.printer = new PrintWriter(System.out);
    }

    public XPath2XMLVisitor(PrintWriter printer) {
        this.printer = printer;
    }

    public void visit(PathExpr expr) {
        printLn("<PathExpr>");
        if (expr.getFilterExpr() != null){
            expr.getFilterExpr().accept(this);
        }
        if (expr.getLocationPath() != null){
            expr.getLocationPath().accept(this);
        }
        printLn("</PathExpr>");
    }

    public void visit(LocationPath expr) {
        printLn("<LocationPath absolute=\"" + expr.isAbsolute() + "\">");
        Iterator steps = expr.getSteps().iterator();

        while (steps.hasNext()){
            Step step = (Step)steps.next();
            step.accept(this);
        }
        printLn("</LocationPath>");
    }

    public void visit(LogicalExpr expr) {
        printLn("<LogicalExpr operator=\""+ expr.getOperator() + "\">");
        printLhsRhs(expr.getLHS(), expr.getRHS());
        printLn("</LogicalExpr>");
    }

    void printLhsRhs(Expr lhs, Expr rhs){
        tabIndex++;
        printLn("<lhsExpr>");
        lhs.accept(this);
        printLn("</lhsExpr>");
        printLn("<rhsExpr>");
        rhs.accept(this);
        printLn("</rhsExpr>");
        tabIndex--;
    }

    public void visit(EqualityExpr expr) {
        printLn("<EqualityExpr operator=\""+ expr.getOperator() + "\">");
        printLhsRhs(expr.getLHS(), expr.getRHS());
        printLn("</EqualityExpr>");
    }

    public void visit(FilterExpr expr) {
        printLn("<FilterExpr>");
        tabIndex++;
        if (expr.getExpr() != null){
            expr.getExpr().accept(this);
        }
        Iterator iter = expr.getPredicates().iterator();
        while (iter.hasNext()){
            ((Predicate)iter.next()).getExpr().accept(this);
        }
        tabIndex--;
        printLn("</FilterExpr>");
    }

    public void visit(RelationalExpr expr) {
        printLn("<RelationalExpr operator=\""+ expr.getOperator() + "\">");
        printLhsRhs(expr.getLHS(), expr.getRHS());
        printLn("</RelationalExpr>");
    }

    public void visit(AdditiveExpr expr) {
        printLn("<AdditiveExpr operator=\""+ expr.getOperator() + "\">");
        printLhsRhs(expr.getLHS(), expr.getRHS());
        printLn("</AdditiveExpr>");
    }

    public void visit(MultiplicativeExpr expr) {
        printLn("<MultiplicativeExpr operator=\""+ expr.getOperator() + "\">");
        printLhsRhs(expr.getLHS(), expr.getRHS());
        printLn("</MultiplicativeExpr>");
    }

    public void visit(UnaryExpr expr) {
        printLn("<UnaryExpr>");
        expr.getExpr().accept(this);
        printLn("</UnaryExpr>");
    }

    public void visit(UnionExpr expr) {
        printLn("<UnionExpr>");
        printLhsRhs(expr.getLHS(), expr.getRHS());
        printLn("</UnionExpr>");
    }

    public void visit(NumberExpr expr) {
        printLn("<NumberExpr>");
        printLn(expr.getNumber().toString());
        printLn("</NumberExpr>");
    }

    public void visit(LiteralExpr expr) {
        printLn("<LiteralExpr literal=\"" + expr.getLiteral() + "\"/>");
    }

    public void visit(VariableReferenceExpr expr) {
        printLn("<VariableReferenceExpr name=\"" + expr.getVariableName() + "\"/>");
    }

    public void visit(FunctionCallExpr expr){
        printLn("<FunctionCallExpr prefix=\"" + expr.getPrefix() +
        "\" functionName=\"" + expr.getFunctionName() + "\">");

        Iterator iterator = expr.getParameters().iterator();
        tabIndex++;
        printLn("<Args>");
        while (iterator.hasNext()){
            ((Expr)iterator.next()).accept(this);
        }
        printLn("</Args>");
        tabIndex--;
        printLn("</FunctionCallExpr>");
    }

    public void visit(NameStep step){
        printLn("<NameStep prefix=\"" + step.getPrefix()+
            "\" localName=\"" + step.getLocalName() + "\">");
        Iterator iter = step.getPredicates().iterator();
        tabIndex++;
        while(iter.hasNext()){
            Predicate predicate = (Predicate)iter.next();
            predicate.accept(this);
        }
        tabIndex--;
        printLn("</NameStep>");
    }

    public void visit(ProcessingInstructionNodeStep step){
        printLn("<ProcessingInstructionNodeStep name=\"" + step.getName() +
            "\" axis=\"" + step.getAxis() + ">");

        tabIndex++;
        handlePredicates(step.getPredicates());
        tabIndex--;
        printLn("</ProcessingInstructionNodeStep>");
    }

    public void visit(AllNodeStep step){
        printLn("<AllNodeStep>");
        tabIndex++;
        handlePredicates(step.getPredicates());
        tabIndex--;
        printLn("</AllNodeStep>");
    }

    public void visit(TextNodeStep step){
        printLn("<TextNodeStep>");
        tabIndex++;
        handlePredicates(step.getPredicates());
        tabIndex--;
        printLn("</TextNodeStep>");
    }

    public void visit(CommentNodeStep step){
        printLn("<CommentNodeStep>");
        tabIndex++;
        handlePredicates(step.getPredicates());
        tabIndex--;
        printLn("</CommentNodeStep>");
    }

    public void visit(Predicate predicate){
        printLn("<Predicate>");
        tabIndex++;
        predicate.getExpr().accept(this);
        tabIndex--;
        printLn("</Predicate>");
    }

    //---------------------------------------------------------------
    protected void printLn(String str){
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < tabIndex; i++) {
            buffer.append("\t");
        }
        buffer.append(str);

        printer.println(buffer.toString());
    }

    protected void handlePredicates(List predicates){
        if (predicates != null){
            Iterator iter = predicates.iterator();
            while(iter.hasNext()){
                ((Predicate)iter.next()).accept(this);
            }
        }
    }

}
