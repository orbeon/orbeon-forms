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

import com.softwareag.tamino.db.api.objectModel.TIteratorException;
import com.softwareag.tamino.db.api.objectModel.TNoSuchXMLObjectException;
import com.softwareag.tamino.db.api.objectModel.TXMLObjectIterator;
import org.dom4j.Element;




/**
 ** TDOM4JElementIterator defines all operations needed for an unidirectional
 ** iterator that is capable to navigate typesafe over org.dom4j.Element
 ** instances in forward direction. This iterator can directly be created
 ** from a given TXMLObjectIterator for TXMLObject instances that make use
 ** of DOM4J as the object model.
 **
 **/

public class TDOM4JElementIterator {
	
	/**
	 ** Special Constructor. Intializes this iterator from a TResultSetIterator instance.
	 **/
	public TDOM4JElementIterator(TXMLObjectIterator xmlObjectIterator) {
		this.xmlObjectIterator = xmlObjectIterator;
	}
	
	/**
	 ** Indicates if iterator has next Element instance.
	 **/
	public boolean hasNext() {
		return xmlObjectIterator.hasNext();
	}
	
	/**
	 ** Returns the next element in the list. This method may be called repeatedly to iterate
	 ** through the list.
	 **
	 ** @return The next org.dom4j.Element instance.
	 ** @exception NoSuchElementException if iteration has no more elements.
	 ** @exception TIteratorException if iteration failed because of an underlying Tamino problem.
	 **/
	public Element next() throws TNoSuchXMLObjectException, TIteratorException {
		return (Element)xmlObjectIterator.next().getElement();
	}
	
	// The wrapped TXMLObjectIterator instance
	private TXMLObjectIterator xmlObjectIterator = null;
	
}
