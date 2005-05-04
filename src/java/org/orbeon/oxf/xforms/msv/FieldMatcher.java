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
 * @(#)$Id: FieldMatcher.java,v 1.1 2005/05/04 23:55:58 ebruchez Exp $
 *
 * Copyright 2001 Sun Microsystems, Inc. All Rights Reserved.
 * 
 * This software is the proprietary information of Sun Microsystems, Inc.  
 * Use is subject to license terms.
 * 
 */
package org.orbeon.oxf.xforms.msv;

import com.sun.msv.grammar.xmlschema.Field;
import org.relaxng.datatype.Datatype;

/**
 * XPath matcher that tests one field of a key.
 * 
 * This object is created by a FieldsMatcher when a SelectorMathcer
 * finds a match to its selector. This object is responsible for finding
 * a match to one field of the constraint.
 * 
 * A field XPath may consist of "A|B|C". Each sub case A,B, and C is
 * tested by a child FieldPathMatcher object. This class coordinates
 * the work of those children and collects actual text that matches
 * the given XPath.
 * 
 * @author <a href="mailto:kohsuke.kawaguchi@eng.sun.com">Kohsuke KAWAGUCHI</a>
 */
public class FieldMatcher extends PathMatcher {
    
    protected Field field;
    
    /**
     * the matched value. If this field is null, then it means
     * nothing is matched yet.
     */
    protected Object    value;
    
    /** parent FieldsMatcher object. */
    protected final FieldsMatcher parent;

    private org.dom4j.Element element;
    
    FieldMatcher( FieldsMatcher parent, Field field )  {
        super( parent.owner, field.paths );
        this.parent = parent;
        
        // test the initial match
        super.start( parent.element );
    }


    /**
     * this method is called when the element matches the XPath.
     */
    protected void onElementMatched( final org.dom4j.Element elt )  {
        if( com.sun.msv.driver.textui.Debug.debug )
            System.out.println("field match for "+ parent.selector.idConst.localName );
        
        // this field matches this element.
        // wait for the corresponding endElement call and
        // obtain text.
        element = elt;
    }

    /**
     * this method is called when the attribute matches the XPath.
     */
    protected void onAttributeMatched( final org.dom4j.Attribute att, Datatype type )  {
        
        if( com.sun.msv.driver.textui.Debug.debug )
            System.out.println("field match for "+ parent.selector.idConst.localName );
        
        final String val = att.getValue();
        setValue( val, type );
    }
    
    protected void startElement( final org.dom4j.Element elt ) 
                                 {
        if( element != null ) {
            // this situation is an error because a matched element
            // cannot contain any child element.
            // But what I don't know is how to treat this situation.
            
            // 1. to make the document invalid?
            // 2. cancel the match?
            
            // the current implementation choose the 2nd.
            element = null;
        }
        super.startElement( elt );
    }
    
    protected void endElement( Datatype type ) {
        super.endElement(type);
        // double match error is already checked in the corresponding
        // startElement method.
        if( element !=null ) {
            final String val = element.getText();
            setValue( val, type );
            element = null;
        }
    }

    
    /** sets the value field. */
    private void setValue( String lexical, Datatype type ) {
        if(value!=null) {
            // not the first match.
            doubleMatchError();
            return;
        }
        
        if(type==null) {
            // this is possible only when we are recovering from errors.
            value = lexical;
            if(com.sun.msv.driver.textui.Debug.debug)
                System.out.println("no type info available");
        } else
            value = type.createValue(lexical,owner);
    }
    
    /** this field matches more than once. */
    private void doubleMatchError() {
        int i;
        // compute the index number of this field.
        for( i=0; i<parent.children.length; i++ )
            if( parent.children[i]==this )
                break;
        
        owner.reportError( parent.element, IDConstraintChecker.ERR_DOUBLE_MATCH,
            new Object[]{
                parent.selector.idConst.namespaceURI,
                parent.selector.idConst.localName,
                new Integer(i+1)} );
    }
}
