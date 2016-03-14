/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/saxpath/helpers/DefaultXPathHandler.java,v 1.7 2006/02/05 21:47:42 elharo Exp $
 * $Revision: 1.7 $
 * $Date: 2006/02/05 21:47:42 $
 *
 * ====================================================================
 *
 * Copyright 2000-2002 bob mcwhirter & James Strachan.
 * All rights reserved.
 *
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   * Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   * Neither the name of the Jaxen Project nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * ====================================================================
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Jaxen Project and was originally
 * created by bob mcwhirter <bob@werken.com> and
 * James Strachan <jstrachan@apache.org>.  For more information on the
 * Jaxen Project, please see <http://www.jaxen.org/>.
 *
 * $Id: DefaultXPathHandler.java,v 1.7 2006/02/05 21:47:42 elharo Exp $
 */




package org.orbeon.jaxen.saxpath.helpers;

import org.orbeon.jaxen.saxpath.SAXPathException;
import org.orbeon.jaxen.saxpath.XPathHandler;

/**

   Default base class for SAXPath event handlers.

   This class is available as a convenience base class for SAXPath
   applications: it provides a default do-nothing implementation
   for all of the callbacks in the core SAXPath handler class, {@link
   org.orbeon.jaxen.saxpath.XPathHandler}.

   Application writers can extend this class when they need to
   implement only part of the <code>XPathHandler</code>
   interface. Parser writers can instantiate
   this class to provide default handlers when the application has not
   supplied its own. */

public class DefaultXPathHandler implements XPathHandler
{

    public void startXPath() throws SAXPathException
    {
    }

    public void endXPath() throws SAXPathException
    {
    }

    public void startPathExpr() throws SAXPathException
    {
    }

    public void endPathExpr() throws SAXPathException
    {
    }

    public void startAbsoluteLocationPath() throws SAXPathException
    {
    }

    public void endAbsoluteLocationPath() throws SAXPathException
    {
    }

    public void startRelativeLocationPath() throws SAXPathException
    {
    }

    public void endRelativeLocationPath() throws SAXPathException
    {
    }

    public void startNameStep(int axis,
                              String prefix,
                              String localName) throws SAXPathException
    {
    }

    public void endNameStep() throws SAXPathException
    {
    }

    public void startTextNodeStep(int axis) throws SAXPathException
    {
    }
    public void endTextNodeStep() throws SAXPathException
    {
    }

    public void startCommentNodeStep(int axis) throws SAXPathException
    {
    }

    public void endCommentNodeStep() throws SAXPathException
    {
    }

    public void startAllNodeStep(int axis) throws SAXPathException
    {
    }

    public void endAllNodeStep() throws SAXPathException
    {
    }

    public void startProcessingInstructionNodeStep(int axis,
                                                   String name) throws SAXPathException
    {
    }
    public void endProcessingInstructionNodeStep() throws SAXPathException
    {
    }

    public void startPredicate() throws SAXPathException
    {
    }

    public void endPredicate() throws SAXPathException
    {
    }

    public void startFilterExpr() throws SAXPathException
    {
    }

    public void endFilterExpr() throws SAXPathException
    {
    }

    public void startOrExpr() throws SAXPathException
    {
    }

    public void endOrExpr(boolean create) throws SAXPathException
    {
    }

    public void startAndExpr() throws SAXPathException
    {
    }

    public void endAndExpr(boolean create) throws SAXPathException
    {
    }

    public void startEqualityExpr() throws SAXPathException
    {
    }

    public void endEqualityExpr(int operator) throws SAXPathException
    {
    }

    public void startRelationalExpr() throws SAXPathException
    {
    }

    public void endRelationalExpr(int operator) throws SAXPathException
    {
    }

    public void startAdditiveExpr() throws SAXPathException
    {
    }

    public void endAdditiveExpr(int operator) throws SAXPathException
    {
    }

    public void startMultiplicativeExpr() throws SAXPathException
    {
    }

    public void endMultiplicativeExpr(int operator) throws SAXPathException
    {
    }

    public void startUnaryExpr() throws SAXPathException
    {
    }

    public void endUnaryExpr(int operator) throws SAXPathException
    {
    }

    public void startUnionExpr() throws SAXPathException
    {
    }

    public void endUnionExpr(boolean create) throws SAXPathException
    {
    }

    public void number(int number) throws SAXPathException
    {
    }

    public void number(double number) throws SAXPathException
    {
    }

    public void literal(String literal) throws SAXPathException
    {
    }

    public void variableReference(String prefix,
                                  String variableName) throws SAXPathException
    {
    }

    public void startFunction(String prefix,
                              String functionName) throws SAXPathException
    {
    }

    public void endFunction() throws SAXPathException
    {
    }

}
