/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
/*
 * @(#)$Id: MatcherBundle.java,v 1.1 2005/05/04 23:55:58 ebruchez Exp $
 *
 * Copyright 2001 Sun Microsystems, Inc. All Rights Reserved.
 * 
 * This software is the proprietary information of Sun Microsystems, Inc.  
 * Use is subject to license terms.
 * 
 */
package org.orbeon.oxf.xforms.msv;

import org.relaxng.datatype.Datatype;

/**
 * Base implementation of Matcher coordinator.
 * 
 * This class behaves as a parent of several other matchers, or as a composite
 * XPath matcher.
 * Those child matchers are not directly registered to IDConstraintChecker.
 * Instead, they receive notifications through this object.
 * 
 * @author <a href="mailto:kohsuke.kawaguchi@eng.sun.com">Kohsuke KAWAGUCHI</a>
 */
class MatcherBundle extends Matcher {
    
    /** child matchers. */
    protected Matcher[] children;
    /** depth. */
    private int depth = 0;
    protected final int getDepth() { return depth; }
    
    /**
     * the derived class must initialize the children field appropriately.
     */
    protected MatcherBundle( IDConstraintChecker owner ) {
        super( owner );
    }
    
    protected void startElement( final org.dom4j.Element elt ) {
        
        depth++;
        for( int i=0; i<children.length; i++ )
            children[i].startElement( elt );
    }
    
    protected void onAttribute( final org.dom4j.Attribute att, final Datatype type )  {
        for( int i=0; i<children.length; i++ )
            children[i].onAttribute( att,type );
    }
    
    protected void endElement( final Datatype type )  {
        for( int i=0; i<children.length; i++ )
            children[i].endElement(type);
        if( depth-- == 0 ) {
            // traversal complete.
            owner.remove(this);
            onRemoved(); 
        }
    }

    
    /**
     * called when this bundle is deactivated.
     * This method is called by the endElement method when this bundle is
     * removed. A derived class can override this method to do whatever
     * necessary.
     */
    protected void onRemoved() {
    }
}
