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


import com.softwareag.tamino.db.api.objectModel.TXMLObjectModel;




/**
 ** TDOM4JObjectModel represents the DOM4J object model. This class is used when creating
 ** accessor instances. An accessor is always created for a specific object model. The
 ** factory methods for creating an accessor take an instance of an object model class.<p>
 ** Using this object model for an accessor determines that the XML documents
 ** provided by that accessor are DOM4J documents.<p>
 ** This class implements the singleton pattern, i.e. there is always only
 ** one instance of this class available.
 **
 **/

public class TDOM4JObjectModel extends TXMLObjectModel {
	
	/**
	 ** Internal constructor. Initializes the singleton instance of the DOM4J object model.
	 **/
	protected TDOM4JObjectModel() {
		super( "DOM4J" ,
			  org.dom4j.Document.class 	,
			  org.dom4j.Element.class	 	,
			  TDOM4JAdapter.class ,
			  TDOM4JInputStreamInterpreter.class );
	}
	
	/**
	 ** Gets the singleton TDOM4JObjectModel instance.
	 **
	 ** @return the singleton TDOM4JObjectModel instance.
	 **/
	public synchronized static TXMLObjectModel getInstance() {
		if ( singleton == null )
			singleton = new TDOM4JObjectModel();
		return singleton;
	}
	
	// The singleton instance.
	private static TDOM4JObjectModel singleton = null;
	
}
