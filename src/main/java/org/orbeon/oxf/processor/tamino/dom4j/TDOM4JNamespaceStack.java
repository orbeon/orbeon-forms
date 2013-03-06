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
package org.orbeon.oxf.processor.tamino.dom4j;
import org.dom4j.Namespace;

import java.util.Stack;




/**
 **	TDOM4JNamespaceStack is a helper class needed by the TDOM4JXMLOutputter so
 ** that DOM4J based document content can be serialized and written to an
 ** output stream.
 **
 **/

class TDOM4JNamespaceStack {
	
    /**
	 ** Initializes the namespace stack.
	 **/
    public TDOM4JNamespaceStack() {
		prefixes = new Stack();
		uris = new Stack();
	}
	
    /**
	 **  This will add a new namespace to the current available stack.
	 **
	 ** @param ns Namespace to add.
	 **/
    public void push(Namespace ns) {
		prefixes.push(ns.getPrefix());
		uris.push(ns.getURI());
    }
    
    /**
	 ** <p>
	 **  This will remove the topmost (most recently added)
	 **    <code>{@link Namespace}</code>, and return its prefix.
	 ** </p>
	 **
	 ** @return <code>String</code> - the popped namespace prefix.
	 **/
    public String pop() {
		String prefix = (String)prefixes.pop();
		uris.pop();
		
		return prefix;
    }
    
    /**
	 ** <p> This returns the number of available namespaces. </p>
	 **
	 ** @return <code>int</code> - size of the namespace stack.
	 **/
    public int size() {
		return prefixes.size();
    }
	
    /**
	 ** <p>
	 **  Given a prefix, this will return the namespace URI most
	 **    rencently (topmost) associated with that prefix.
	 ** </p>
	 **
	 ** @param prefix <code>String</code> namespace prefix.
	 ** @return <code>String</code> - the namespace URI for that prefix.
	 **/
    public String getURI(String prefix) {
		int index = prefixes.lastIndexOf(prefix);
		if (index == -1) {
			return null;
		}
		String uri = (String)uris.elementAt(index);
		return uri;
    }
    
    /**
	 ** <p>
	 **  This will print out the size and current stack, from the
	 **    most recently added <code>{@link Namespace}</code> to
	 **    the "oldest," all to <code>System.out</code>.
	 ** </p>
	 **/
    public void printStack() {
		System.out.println("Stack: " + prefixes.size());
		for (int i = 0; i < prefixes.size(); i++) {
			System.out.println(prefixes.elementAt(i) + "&" + uris.elementAt(i));
		}
    }
	
	// The prefixes available
    private Stack prefixes;
	
    // The URIs available
    private Stack uris;
	
}
