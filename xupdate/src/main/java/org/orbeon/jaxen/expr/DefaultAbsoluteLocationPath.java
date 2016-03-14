/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/expr/DefaultAbsoluteLocationPath.java,v 1.17 2006/05/03 16:07:02 elharo Exp $
 * $Revision: 1.17 $
 * $Date: 2006/05/03 16:07:02 $
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
 * $Id: DefaultAbsoluteLocationPath.java,v 1.17 2006/05/03 16:07:02 elharo Exp $
 */
package org.orbeon.jaxen.expr;

import org.orbeon.jaxen.Context;
import org.orbeon.jaxen.ContextSupport;
import org.orbeon.jaxen.JaxenException;
import org.orbeon.jaxen.Navigator;
import org.orbeon.jaxen.util.SingletonList;

import java.util.Collections;
import java.util.List;

/**
 * @deprecated this class will become non-public in the future;
 *     use the interface instead
 */
public class DefaultAbsoluteLocationPath extends DefaultLocationPath
{
    /**
     *
     */
    private static final long serialVersionUID = 2174836928310146874L;

    public DefaultAbsoluteLocationPath()
    {
    }

    public String toString()
    {
        return "[(DefaultAbsoluteLocationPath): " + super.toString() + "]";
    }

    public boolean isAbsolute()
    {
        return true;
    }

    public String getText()
    {
        return "/" + super.getText();
    }

    public Object evaluate(Context context) throws JaxenException
    {
        ContextSupport support = context.getContextSupport();
        Navigator      nav     = support.getNavigator();
        Context absContext = new Context( support );
        List contextNodes = context.getNodeSet();

        if ( contextNodes.isEmpty() )
        {
            return Collections.EMPTY_LIST;
        }

        Object firstNode = contextNodes.get( 0 );
        Object docNode   = nav.getDocumentNode( firstNode );

        if ( docNode == null )
        {
            return Collections.EMPTY_LIST;
        }

        List list = new SingletonList(docNode);

        absContext.setNodeSet( list );

        return super.evaluate( absContext );
    }

    public void accept(Visitor visitor)
    {
        visitor.visit(this);
    }
}

