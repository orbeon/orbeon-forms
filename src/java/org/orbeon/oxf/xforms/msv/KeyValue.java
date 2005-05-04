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
 * @(#)$Id: KeyValue.java,v 1.1 2005/05/04 23:55:58 ebruchez Exp $
 *
 * Copyright 2001 Sun Microsystems, Inc. All Rights Reserved.
 * 
 * This software is the proprietary information of Sun Microsystems, Inc.  
 * Use is subject to license terms.
 * 
 */
package org.orbeon.oxf.xforms.msv;


/**
 * represents multi-field keys.
 * 
 * this class implements equality test and hash code based on
 * the equalities of each item.
 * 
 * @author <a href="mailto:kohsuke.kawaguchi@eng.sun.com">Kohsuke KAWAGUCHI</a>
 */
class KeyValue {
    public final Object[] values;
    
    /** source location that this value is found. */
    public final org.dom4j.Element element;
    
    KeyValue( Object[] values, final org.dom4j.Element elt ) {
        this.values = values;
        element = elt;
    }
    
    public int hashCode() {
        int code = 0;
        for( int i=0; i<values.length; i++ )
            code ^= values[i].hashCode();
        return code;
    }
    
    public boolean equals( Object o ) {
        if(!(o instanceof KeyValue))    return false;
        KeyValue rhs = (KeyValue)o;
        if( values.length!=rhs.values.length )    return false;
        
        for( int i=0; i<values.length; i++ )
            if( !values[i].equals(rhs.values[i]) )    return false;
        
        return true;
    }
}
