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


import com.softwareag.tamino.db.api.io.TInputStream;
import com.softwareag.tamino.db.api.io.TStreamHeader;
import com.softwareag.tamino.db.api.namespace.TInoNamespace;
import com.softwareag.tamino.db.api.namespace.TXQLNamespace;
import com.softwareag.tamino.db.api.namespace.TXQNamespace;
import com.softwareag.tamino.db.api.objectModel.TXMLObject;
import com.softwareag.tamino.db.api.response.*;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.dom4j.io.SAXReader;
import org.dom4j.tree.DefaultText;
import org.orbeon.oxf.xml.dom4j.NonLazyUserDataDocumentFactory;
import org.xml.sax.SAXException;

import java.util.Iterator;
import java.util.List;


/**
 ** TDOM4JInputStreamInterpreter is the implemenation for the interpreting as it is needed
 ** for DOM4J needs. This means that instantiated objects for query results are concrete
 ** TXMLObject instances with an underlying DOM4J object model.
 **/
public class TDOM4JInputStreamInterpreter extends TInputStreamInterpreter {
	
	/**
	 **
	 **/
	public TDOM4JInputStreamInterpreter(String parserName) {
		super();
		initialize( parserName );
	}
	
	/**
	 **
	 **/
	public TDOM4JInputStreamInterpreter() {
		super();
		initialize( null );
	}
	
	/**
	 ** Sets a generic property on the concrete interpreter instance. The properties that can actually be set
	 ** depend on the TXMLObjectModel that is related to the concrete interpreter. Each interpreter that might
	 ** be instantiated is related to a TXMLObjectModel. Here the user can set specific properties that might
	 ** be relevant as additional parameters to the interpreter.
	 ** This method does nothing for the DOM4J specific interpreter.
	 **/
	public void setProperty(String key,Object value) {
	}
	
	/**
	 ** Interprets the a general inputStream in a generic fashion. As a consequence only the
	 ** result state will be obtained.
	 **
	 ** @param inputStream is the InputStream for a response to a previous operation on Tamino.
	 ** @exception TStreamInterpretException when interpreting errors occur.
	 **/
	protected void doInterpret(TInputStream inputStream) throws TStreamInterpretException {
		// please have a look at comment in the initialize method.
		try {
			// Create the DOM4J SAXReader with the DOM4J specific underlying SAX parser. This is a JAXP parser!
			SAXReader saxReader = (parserName == null || parserName.equals( "") ) ? new SAXReader() : new SAXReader( parserName );
            
			// Make sure the document and its children are thread safe by using our
                        // document factory.
			final NonLazyUserDataDocumentFactory fctry = NonLazyUserDataDocumentFactory.getInstance14();
			saxReader.setDocumentFactory( fctry );
 
			// Invoke the parsing and obtain the Document instance
			document = saxReader.read( inputStream );
			TStreamHeader header = inputStream.getHeader();
			// Set the result state
			setResponseInfoContent( document );
			// Set the result set of querried XML objects only if result relates to a query
			if ( document.getRootElement().element( new QName(TXQLNamespace.QUERY.getName(), xqlNamespace) ) != null
			   || document.getRootElement().element( new QName(TXQNamespace.XQUERY.getName(), xqNamespace) ) != null ) {
				setResponseQueryContent( document ,
											(String)header.getValue( TStreamHeader.COLLECTION ) ,
											(String)header.getValue( TStreamHeader.DOCTYPE ) );
			}
		} catch(SAXException saxException) {
			throw new TStreamInterpretException( "Interpreting the input stream for DOM4J failed!", saxException );
		} catch(Exception exception) {
			throw new TStreamInterpretException( "Interpreting the input stream failed!", exception );
		}
	}
	
	// Initializes this object.
	private void initialize(String parserName) {
		// The method to retrieve the SAX XMLReader (parser) is delegated to DOM4J in the method doInterpret().
		// This method is not the preferred one, but due to a bug in WebLogic 6.1 this work around has been
		// implemented. The "good" version can be retrieved from revision 1.10
		this.parserName = parserName;
		// Create the internal ino namespace instance.
		this.inoNamespace = Namespace.get( TInoNamespace.getInstance().getPrefix(), TInoNamespace.getInstance().getUri() );
		// Create the internal xql namespace instance. Hardcoding will be replaced later :-)
		this.xqlNamespace = Namespace.get( TXQLNamespace.getInstance().getPrefix(), TXQLNamespace.getInstance().getUri() );
		// Create the internal xq namespace instance. Hardcoding will be replaced later :-)
		this.xqNamespace = Namespace.get( TXQNamespace.getInstance().getPrefix(), TXQNamespace.getInstance().getUri() );
		// Obtain the response content item factory singleton.
		this.responseContentItemFactory = TResponseContentItemFactory.getInstance();
	}
	
	// Sets the result state due to the XML parsing.
	private void setResponseInfoContent(Document document) {
		// Obtain the response info content first.
		TResponseInfoContent responseInfoContent = getResponseInfoContent();
		// First set the message content item
		setMessageContentItem( responseInfoContent, document );
		// Set the query content item if given
		setQueryContentItem( responseInfoContent, document );
		// Set the session content item if given
		setSessionContentItem( responseInfoContent, document );
		// Set the object content item if given
		setObjectContentItem( responseInfoContent, document );
		// Set the cursor content item if given
		setCursorContentItem( responseInfoContent, document );
	}
	
	// Sets the message state due to the XML parsing.
	private void setMessageContentItem(TResponseInfoContent responseInfoContent,Document document) {
		// Analyze the message state
		Element re = document.getRootElement();
		// Obtain the list of message elements.
		List messageList = re.elements( new QName(TInoNamespace.MESSAGE.getName(), inoNamespace) );
		Iterator messageIter = messageList.iterator();
		// Iterate thru the list of messages.
		while ( messageIter.hasNext() ) {
			Element message = (Element)messageIter.next();
			// Obtain the messages returnValue.
			QName qname = new QName(TInoNamespace.RETURN_VALUE.getName(), inoNamespace);
			String returnValue = message.attributeValue( qname );
			TMessageLineContentItem[] messageLineContentArray = null;
			// Obtain the list of message lists within the message.
			qname = new QName( TInoNamespace.MESSAGE_LINE.getName(), inoNamespace );
			List messageLineList = message.elements( qname );
			// Go thru the list of message lines and retrieve for each the subject and text content.
			if ( messageLineList.size() > 0 ) {
				messageLineContentArray = new TMessageLineContentItem[ messageLineList.size() ];
				int index = 0;
				Iterator messageLineIter = messageLineList.iterator();
				while ( messageLineIter.hasNext() ) {
					Element messageLine = (Element)messageLineIter.next();
					qname = new QName(TInoNamespace.SUBJECT.getName(), inoNamespace);
					String subject = messageLine.attributeValue( qname );
					qname = new QName(TInoNamespace.UNIT.getName(), inoNamespace);
					String unit    = messageLine.attributeValue( qname );
					String text 	 = messageLine.getText();
					// Instantiate the message line content item.
					TMessageLineContentItem messageLineContentItem = responseContentItemFactory.newMessageLineContentItem( subject, text );
					if ( unit != null && !unit.equals("") ) {
						messageLineContentItem.addAttribute( TInoNamespace.UNIT.getQualifiedName(), unit );
					}
					messageLineContentArray[index++] = messageLineContentItem;
					
				}
			}
			TMessageTextContentItem messageTextContent = null;
			// Obtain the message text if given.
			qname = new QName( TInoNamespace.MESSAGE_TEXT.getName(), inoNamespace );
			Element messageText = message.element( qname );
			if ( messageText != null ) {
				qname = new QName( TInoNamespace.CODE.getName(), inoNamespace );
				String code = messageText.attributeValue( qname );
				String text = messageText.getText();
				// Instantiate the message text content item for code and text content.
				messageTextContent = responseContentItemFactory.newMessageTextContentItem( code, text );
			}
			// Finally instantiate the message content with returnValue, message lines and message text.
			TMessageContentItem messageContent = responseContentItemFactory.newMessageContentItem( returnValue, messageLineContentArray, messageTextContent );
			// Put the message content into the info content.
			responseInfoContent.putItem( TMessageContentItem.SPECIFIER, messageContent );
		}
	}
	
	// Set the (x)query content item due to the XML parsing.
	private void setQueryContentItem(TResponseInfoContent responseInfoContent,Document document ) {
		Element re = document.getRootElement();
		QName qnameXQuery = new QName( TXQNamespace.XQUERY.getName(), xqNamespace );
		Element xqueryElement = re.element( qnameXQuery );
		if ( xqueryElement != null ) {
			TXQueryContentItem xqueryContentItem = responseContentItemFactory.newXQueryContentItem( xqueryElement.getText() );
			responseInfoContent.putItem( TQueryContentItem.SPECIFIER, xqueryContentItem );
		} else {
			QName qnameQuery = new QName( TXQLNamespace.QUERY.getName(), xqlNamespace );
			Element queryElement = re.element( qnameQuery );
			if ( queryElement != null ) {
				TQueryContentItem queryContentItem = responseContentItemFactory.newQueryContentItem( queryElement.getText() );
				responseInfoContent.putItem( TQueryContentItem.SPECIFIER, queryContentItem );
			}
		}
	}
	
	// Sets the session state due to the XML parsing.
	private void setSessionContentItem(TResponseInfoContent responseInfoContent,Document document) {
		// Set the session state if one is given within the ino:response root tag
		Element re = document.getRootElement();
		QName qname = new QName( TInoNamespace.SESSION_ID.getName(), inoNamespace );
		String sessionId  = re.attributeValue( qname );
		qname = new QName( TInoNamespace.SESSION_KEY.getName(), inoNamespace );
		String sessionKey = re.attributeValue( qname );
		if ( sessionId != null && sessionKey != null ) {
			TSessionContentItem sessionContentItem = responseContentItemFactory.newSessionContentItem( sessionId, sessionKey );
			responseInfoContent.putItem( TSessionContentItem.SPECIFIER, sessionContentItem );
			// This is the place where the mode listener has to be called!!
			//transactionModeListener.sessionStateChanged( sessionId, sessionKey );
		}
	}
	
	// Sets the object state due to the XML parsing.
	private void setObjectContentItem(TResponseInfoContent responseInfoContent,Document document) {
		// Set the object state (only relevant for insert or update) if one is given within ino:object child element beneath root element
		Element re  = document.getRootElement();
		QName qname = new QName( TInoNamespace.OBJECT.getName(), inoNamespace );
		Element obj = re.element( qname );
		if ( obj != null ) {
			qname = new QName( TInoNamespace.COLLECTION.getName(), inoNamespace );
			String collection = obj.attributeValue( qname );
			qname = new QName( TInoNamespace.DOCTYPE.getName(), inoNamespace);
			String doctype 		= obj.attributeValue( qname );
			qname = new QName(TInoNamespace.ID.getName(), inoNamespace);
			String id 				= obj.attributeValue( qname );
			TObjectContentItem objectContentItem = responseContentItemFactory.newObjectContentItem( collection, doctype, id );
			responseInfoContent.putItem( TObjectContentItem.SPECIFIER, objectContentItem );
		}
	}
	
	// Sets the cursor state due to the XML parsing.
	private void setCursorContentItem(TResponseInfoContent responseInfoContent,Document document) {
		QName qname = new QName(TInoNamespace.CURSOR.getName(), inoNamespace );
		// Set the cursor state if one is given within ino:cursor element beneath root element
		Element re = document.getRootElement();
		Element ce = re.element( qname );
		if ( ce != null ) {
			// Get the cursor handle attribute of the cursor element
			qname = new QName(TInoNamespace.HANDLE.getName(), inoNamespace);
			String handleValue = ce.attributeValue( qname );
			// Get the count attribute of the cursor element if available
			qname = new QName(TInoNamespace.COUNT.getName(), inoNamespace);
			String countValue = ce.attributeValue( qname );
			// Get the current, next and previous attribute values of cursor structure
			qname = new QName(TInoNamespace.CURRENT.getName(), inoNamespace);
			Element current = ce.element( qname );
			qname = new QName(TInoNamespace.NEXT.getName(), inoNamespace);
			Element next    = ce.element( qname );
			qname = new QName(TInoNamespace.PREVIOUS.getName(), inoNamespace);
			Element prev    = ce.element( qname );
			// Get the concrete attribute values
			qname = new QName(TInoNamespace.POSITION.getName(), inoNamespace );
			String currentValue  = ( current != null ) ? current.attributeValue( qname ) : null;
			qname = new QName(TInoNamespace.QUANTITY.getName(), inoNamespace );
			String quantityValue = ( current != null ) ? current.attributeValue( qname ) : null;
			qname = new QName( TInoNamespace.POSITION.getName(), inoNamespace );
			String nextValue     = ( next != null ) ? next.attributeValue( qname ) : null;
			String prevValue     = ( prev != null ) ? prev.attributeValue( qname ) : null;
			// Instantiate the cursor instance
			TCursorContentItem cursorContentItem = responseContentItemFactory.newCursorContentItem( handleValue, currentValue, quantityValue, nextValue, prevValue, countValue );
			responseInfoContent.putItem( TCursorContentItem.SPECIFIER, cursorContentItem );
		}
	}
	
	// Sets the result set enumeration due to the XML parsing.
	private void setResponseQueryContent(Document document,String collection,String doctype) {
		// Get all the Elements returned from Tamino
		Element re = document.getRootElement();
		// Get the xql/xq:result element as the container for the querried XML instances.
		QName qname = new QName( TXQNamespace.RESULT.getName(), xqNamespace );
		Element qc = re.element( qname );
		if ( qc == null ) {
			qname = new QName( TXQLNamespace.RESULT.getName(), xqlNamespace );
			qc = re.element( qname );
		}
		
		if ( qc != null ) {
			// Get the list of all result DOM4J element instances referring to the XML instances. Currently getMixedContent is used, will be changed later.
			//List list  = qc.elements();
			List list = qc.content();
			if ( list.size() != 0 ) {
				// Obtain the response query content.
				TResponseQueryContent responseQueryContent = getResponseQueryContent();
				Iterator iter = list.iterator();
				while ( iter.hasNext() ) {
					Object item = iter.next();
					// Well, if the item happens to be a string then there is either mixed content or only text content.
					if ( item instanceof String ) {
						responseQueryContent.setText( item.toString() );
					} else if (item instanceof DefaultText){
						responseQueryContent.setText(((DefaultText)item).getText());
					} else if ( item instanceof Element ) {
						Element element = (Element)item;
						// Create the appropiate Tamino XML object with the given ino:id, collection and schema information.
						TXMLObject xmlObject = TXMLObject.newInstance( element );
						xmlObject.setCollection( collection );
						xmlObject.setDoctype( doctype );
						// Add the obtained TXMLObject to the result set.
						responseQueryContent.add( xmlObject );
					}
				}
			}
		}
	}
	
	// The DOM4J ino Namespace attribute.
	private Namespace inoNamespace = null;
	
	// The DOM4J xql Namespace attribute.
	private Namespace xqlNamespace = null;
	
	// The DOM4J xq Namespace attribute.
	private Namespace xqNamespace = null;
	
	// The SAX parser class type.
	private String parserName = "";
	
	// The DOM4J document obtained from the parsing
	private Document document = null;
	
	// Factory for the instantiation of response content item instances.
	private TResponseContentItemFactory responseContentItemFactory = null;
	
}
