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
 * @(#)$Id: Matcher.java,v 1.1 2005/05/04 23:55:58 ebruchez Exp $
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
 * Base abstract implementation of XPath matcher.
 * 
 * XPath mathcer tracks the startElement event and the endElement event.
 * The characters event is also used by some derived classes.
 * 
 * @author <a href="mailto:kohsuke.kawaguchi@eng.sun.com">Kohsuke KAWAGUCHI</a>
 */
public abstract class Matcher {
    
    protected final IDConstraintChecker owner;
    Matcher( IDConstraintChecker ow ) {
        owner = ow;
    }
    
    protected abstract void startElement( final org.dom4j.Element elt ) ;
    protected abstract void onAttribute( final org.dom4j.Attribute att, final Datatype type ) ;
    protected abstract void endElement( final Datatype type ) ;
    
    protected final void characters( final char[] buf, final int start, final int len ) {
        // do nothing by default.
    }
}
