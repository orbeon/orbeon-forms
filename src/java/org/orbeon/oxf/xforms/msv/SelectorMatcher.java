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
 * @(#)$Id: SelectorMatcher.java,v 1.1 2005/05/04 23:55:58 ebruchez Exp $
 *
 * Copyright 2001 Sun Microsystems, Inc. All Rights Reserved.
 * 
 * This software is the proprietary information of Sun Microsystems, Inc.  
 * Use is subject to license terms.
 * 
 */
package org.orbeon.oxf.xforms.msv;

import com.sun.msv.grammar.xmlschema.IdentityConstraint;
import org.relaxng.datatype.Datatype;

/**
 * XPath matcher that tests the selector of an identity constraint.
 * 
 * This object is created whenever an element with identity constraints is found.
 * XML Schema guarantees that we can see if an element has id constraints at the
 * startElement method.
 * 
 * This mathcer then monitor startElement/endElement and find matches to the
 * specified XPath. Every time it finds a match ("target node" in XML Schema
 * terminology), it creates a FieldsMatcher.
 * 
 * @author <a href="mailto:kohsuke.kawaguchi@eng.sun.com">Kohsuke KAWAGUCHI</a>
 */
public class SelectorMatcher extends PathMatcher {
    
    protected IdentityConstraint idConst;
    
    final org.dom4j.Element element;
    

    SelectorMatcher(
                IDConstraintChecker owner, IdentityConstraint idConst, final org.dom4j.Element elt ) {
        super(owner, idConst.selectors );
        this.idConst = idConst;
        element = elt;
        
        // register this scope as active.
        owner.pushActiveScope(idConst,this);
        
        if(com.sun.msv.driver.textui.Debug.debug) {
            System.out.println("new id scope is available for {"+idConst.localName+"}");
        }
        super.start( elt );
    }

    protected void onRemoved()  {
        super.onRemoved();
        // this scope is no longer active.
        owner.popActiveScope(idConst,this);
    }

    
    protected void onElementMatched( final org.dom4j.Element elt ) {
        if( com.sun.msv.driver.textui.Debug.debug )
            System.out.println("find a match for a selector: "+idConst.localName);
            
        // this element matches the path.
        owner.add( new FieldsMatcher( this, elt ) );
    }
    
    protected void onAttributeMatched( final org.dom4j.Attribute att, Datatype type ) {
        
        // assertion failed:
        // selectors cannot contain attribute steps.
        throw new Error();
    }
    
}
