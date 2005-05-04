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
 * @(#)$Id: FieldsMatcher.java,v 1.1 2005/05/04 23:55:58 ebruchez Exp $
 *
 * Copyright 2001 Sun Microsystems, Inc. All Rights Reserved.
 * 
 * This software is the proprietary information of Sun Microsystems, Inc.  
 * Use is subject to license terms.
 * 
 */
package org.orbeon.oxf.xforms.msv;


import com.sun.msv.grammar.xmlschema.KeyConstraint;
import com.sun.msv.grammar.xmlschema.KeyRefConstraint;

/**
 * Coordinator of FieldMatcher.
 * 
 * This object is created when SelectorMatcher finds a match.
 * This object then creates FieldMatcher for each field, and
 * let them find their field matchs.
 * When leaving the element that matched the selector, it collects
 * field values and registers a key value to IDConstraintChecker.
 * 
 * <p>
 * Depending on the type of the constraint, it works differently.
 * 
 * @author <a href="mailto:kohsuke.kawaguchi@eng.sun.com">Kohsuke KAWAGUCHI</a>
 */
public class FieldsMatcher extends MatcherBundle {
    
    
    /**
     * the parent SelectorMatcher.
     */
    protected final SelectorMatcher selector;
    protected final org.dom4j.Element element;
    
    protected FieldsMatcher( SelectorMatcher selector, final org.dom4j.Element elt )  {
        super(selector.owner);
        
        this.selector = selector;
        element = elt;
        
        children = new Matcher[selector.idConst.fields.length];
        for( int i=0; i<selector.idConst.fields.length; i++ )
            children[i] = new FieldMatcher(
                this,selector.idConst.fields[i]  );
    }
    
    protected void onRemoved()  {
        Object[] values = new Object[children.length];
            
        // copy matched values into "values" variable,
        // while checking any unmatched fields.
        for( int i=0; i<children.length; i++ )
            if( (values[i]=((FieldMatcher)children[i]).value) == null ) {
                if(!(selector.idConst instanceof KeyConstraint))
                    // some fields didn't match to anything.
                    // In case of KeyRef and Unique constraints,
                    // we can ignore this node.
                    return;
                    
                // if this is the key constraint, it is an error
                owner.reportError(
                    element, IDConstraintChecker.ERR_UNMATCHED_KEY_FIELD,
                    new Object[]{
                        selector.idConst.namespaceURI,
                        selector.idConst.localName,
                        new Integer(i+1)} );
                return;
            }

        if( com.sun.msv.driver.textui.Debug.debug )
            System.out.println("fields collected for "+selector.idConst.localName);
        
        KeyValue kv = new KeyValue(values, element);
        if(owner.addKeyValue( selector, kv ))
            return;
        
        // the same value already exists.
        
        if( selector.idConst instanceof KeyRefConstraint )
            // multiple reference to the same key value.
            // not a problem.
            return;
        
        // find a value that collides with kv
        Object[] items = owner.getKeyValues(selector);
        int i;
        for( i=0; i<values.length; i++ )
            if( items[i].equals(kv) )
                break;
        
        // violates uniqueness constraint.
        // this set already has this value.
        owner.reportError(
            element, IDConstraintChecker.ERR_NOT_UNIQUE,
            new Object[]{
                selector.idConst.namespaceURI, selector.idConst.localName} );
        owner.reportError(
            ((KeyValue)items[i]).element, IDConstraintChecker.ERR_NOT_UNIQUE_DIAG,
            new Object[]{
                selector.idConst.namespaceURI, selector.idConst.localName} );
    }
    
}
