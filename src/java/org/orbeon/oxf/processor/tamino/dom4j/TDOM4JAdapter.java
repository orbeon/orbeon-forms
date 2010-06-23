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


import com.softwareag.common.instrumentation.contract.Postcondition;
import com.softwareag.common.instrumentation.contract.Precondition;
import com.softwareag.tamino.db.api.common.TPreference;
import com.softwareag.tamino.db.api.common.TString;
import com.softwareag.tamino.db.api.io.TStreamReadException;
import com.softwareag.tamino.db.api.io.TStreamWriteException;
import com.softwareag.tamino.db.api.namespace.TInoNamespace;
import com.softwareag.tamino.db.api.objectModel.TXMLObject;
import org.dom4j.*;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.dom4j.tree.FlyweightAttribute;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;

import java.io.*;

/**
 * DOM4JAdapter is the adaption of the DOM4J object model to the TXMLObject class.
 * It extends the TXMLObject class. XML data is internally represented by the
 * DOM4J implementation. XML data is always given by DOM4J Element instances.
 * *
 */
public class TDOM4JAdapter extends TXMLObject implements Serializable {

    /**
     * Default Constructor. Initializes the adapter with no content.
     */
    public TDOM4JAdapter() {
        this((Element) null);
    }

    /**
     * Special Constructor. Initializes the adapter with the given document.
     */
    public TDOM4JAdapter(Document document) {
        this((document != null) ? document.getRootElement() : (Element) null);
        // Indicate that this TDOM4JAdapter instance is build from a document.
        this.isBuildFromDocument = true;
    }

    /**
     * Special Constructor. Initializes the adapter with the given element, collection
     * and schema.
     */
    public TDOM4JAdapter(Element element) {
        super();
        if (element != null) {
            this.document = element.getDocument();
            this.element = element;
        }
        // Initialize the io helpers.
        initializeIOHelpers();
    }

    /**
     * Sets the ino:docname on this DOM4J specific TXMLObject.
     *
     * @param docname is the ino:docname attribute of the data object.
     */
    public void setDocname(String docname) {
        if (docname == null || docname.equals("")) {
            if (element == null)
                super.setDocname(null);
            else {
                QName qname = new QName(TInoNamespace.DOCNAME.getName(), inoNamespace);
                Attribute att = new FlyweightAttribute(qname);
                element.remove(att);
            }
        } else {
            if (element == null)
                super.setDocname(docname);
            else {
                QName qname = new QName(TInoNamespace.DOCNAME.getName(), inoNamespace);
                Attribute att = element.attribute(qname);
                if (att != null)
                    att.setValue(docname);
                else {
                    att = new FlyweightAttribute(TInoNamespace.DOCNAME.getName(), docname, inoNamespace);
                    element.add(att);
                }
            }
        }
    }

    /**
     * Sets the ino:id for the XML instance.
     */
    public void setId(String inoId) {
        if (inoId == null || inoId.length() == 0) {
            if (element == null)
                super.setId(null);
            else {
                QName qtmpName = new QName(TInoNamespace.ID.getName(), inoNamespace);
                Attribute tmpAttribute = new FlyweightAttribute(qtmpName);
                element.remove(tmpAttribute);
            }
        } else {
            if (element == null)
                super.setId(inoId);
            else {
                QName qname = new QName(TInoNamespace.ID.getName(), inoNamespace);
                Attribute att = element.attribute(qname);
                if (att != null)
                    att.setValue(inoId);
                else {
                    att = new FlyweightAttribute(TInoNamespace.ID.getName(), inoId, inoNamespace);
                    element.add(att);
                }
            }
        }
    }

    /**
     * Gets the ino:docname from this Tamino data object.
     */
    public String getDocname() {
        if (element == null)
            return super.getDocname();
        QName qname = new QName(TInoNamespace.DOCNAME.getName(), inoNamespace);
        Attribute att = element.attribute(qname);
        return (att != null) ? att.getValue() : "";
    }

    /**
     * Gets the ino:id attribute for the underlying XML data.
     */
    public String getId() {
        if (element == null)
            return super.getId();
        QName qname = new QName(TInoNamespace.ID.getName(), inoNamespace);
        Attribute att = element.attribute(qname);
        return (att != null) ? att.getValue() : "";
    }

    /**
     * Gets the doctype for this Tamino data object. If the doctype has not been explicitly set
     * the name of the root element is assumed as the doctype.
     */
    public String getDoctype() {
        String doctype = super.getDoctype();
        if (element != null && doctype.equals(""))
            doctype = element.getQualifiedName();
        return doctype;
    }

    /**
     * Gets the document instance of the underlying object model. If there is none null is returned.
     * E.g. if currently DOM is in use an org.3c.dom.Document instance is returned, for DOM4J this
     * would be an org.dom4j.Document instance.
     *
     * @return The document instance of the underlying object model (E.g. org.w3c.dom.Document for DOM, org.dom4j.Document for DOM4J).
     * 				If this element is not given due to the fact that the TXMLObject has been instantiated solely with an input stream
     * 				null is returned.
     */
    public Object getDocument() {
        return document;
    }

    /**
     * Gets element instance of the underlying object model. If there is none null is returned.
     * E.g. if currently DOM is in use an org.w3c.dom.Element instance is returned, for DOM4J this
     * would be an org.dom4j.Element instance.
     *
     * @return The element instance of the underlying object model (E.g. org.w3c.dom.Element for DOM, org.dom4j.Element for DOM4J).
     * 					If this element is not given due to the fact that the TXMLObject has been instantiated solely with an input stream
     * 					null is returned.
     */
    public Object getElement() {
        return element;
    }

    /**
     * Reads data from inputStream and initializes the concrete object. If exception occurs due to
     * problems of XML parsing the instance remains in its current state.
     *
     * @param inputStream denotes the byte stream from which the object is newly initialized.
     * @exception TStreamReadException due to any problems when reading the stream.
     *
     * @post TXMLObject has new internal element representation.
     */
    public void readFrom(InputStream inputStream, String baseUri) throws TStreamReadException {
        try {
            // Obtain the created DOM4J document.
            document = TransformerUtils.readDom4j(inputStream, baseUri, false, true);
            // Reset the temporay id and docname that might be given in base class if element previously has been null.
            if (element == null) {
                super.setDocname(null);
                super.setId(null);
            }
            // Set the element to the new element.
            element = document.getRootElement();
            // Indicate that this TDOM4JAdapter instance is build from a document.
            this.isBuildFromDocument = true;
        }
        catch (Exception exception) {
            throw new TStreamReadException("Problems during parsing input stream and building DOM4J document.", exception);
        }
    }

    /**
     * Reads data from reader and initializes the concrete object. If exception occurs due to
     * problems of XML parsing the instance remains in its current state.
     *
     * @param reader denotes the character stream from which the object is newly initialized.
     * @exception TStreamReadException due to any problems when reading the stream.
     *
     * @post TXMLObject has new internal element representation.
     */
    public void readFrom(Reader reader, String baseUri) throws TStreamReadException {
        try {
            // Obtain the created DOM4J document.
            document = TString.isEmpty(baseUri) ? Dom4jUtils.readDom4j(reader) : Dom4jUtils.readDom4j(reader, baseUri);
            // Reset the temporay id and docname that might be given in base class if element previously has been null.
            if (element == null) {
                super.setDocname(null);
                super.setId(null);
            }
            // Set the element to the new element.
            element = document.getRootElement();
            // Indicate that this TDOM4JAdapter instance is build from a document.
            this.isBuildFromDocument = true;
        }
        catch (Exception exception) {
            throw new TStreamReadException("Problems during parsing input stream and building DOM4J document.", exception);
        }
    }

    /**
     * Reads data from inputStream and initializes the concrete object. If exception occurs due to
     * problems of XML parsing the instance remains in its current state.
     *
     * @param inputStream denotes the byte stream from which the object is newly initialized.
     * @exception TStreamReadException due to any problems when reading the stream.
     *
     * @post TXMLObject has new internal element representation.
     */
    public void readFrom(InputStream inputStream) throws TStreamReadException {
        try {
            // Obtain the created DOM4J document.
            document = TransformerUtils.readDom4j(inputStream, null, false, true);
            // Reset the temporay id and docname that might be given in base class if element previously has been null.
            if (element == null) {
                super.setDocname(null);
                super.setId(null);
            }
            // Set the element to the new element.
            element = document.getRootElement();
            // Indicate that this TDOM4JAdapter instance is build from a document.
            this.isBuildFromDocument = true;
        }
        catch (Exception exception) {
            throw new TStreamReadException("Problems during parsing input stream and building DOM4J document.", exception);
        }
    }

    /**
     * Reads data from reader and initializes the concrete object. If exception occurs due to
     * problems of XML parsing the instance remains in its current state.
     *
     * @param reader denotes the character stream from which the object is newly initialized.
     * @exception TStreamReadException due to any problems when reading the stream.
     *
     * @post TXMLObject has new internal element representation.
     */
    public void readFrom(Reader reader) throws TStreamReadException {
        try {
            // Obtain the created DOM4J document.
            document = Dom4jUtils.readDom4j(reader);
            // Reset the temporay id and docname that might be given in base class if element previously has been null.
            if (element == null) {
                super.setDocname(null);
                super.setId(null);
            }
            // Set the element to the new element.
            element = document.getRootElement();
            // Indicate that this TDOM4JAdapter instance is build from a document.
            this.isBuildFromDocument = true;
        }
        catch (Exception exception) {
            throw new TStreamReadException("Problems during parsing input stream and building DOM4J document.", exception);
        }
    }

    /**
     * Writes data to the given outputStream.
     *
     * @param outputStream denotes the byte stream to which the internal object representation is written.
     *
     * @exception TStreamWriteException due to any problems when writing the stream.
     *
     * @pre getElement() != null
     */
    public void writeTo(OutputStream outputStream) throws TStreamWriteException {

        Precondition.check("No DOM element is given. Writing to output stream not possible!", getElement() != null);
        // Tell the outputter to write content to outputStream. Used encoding is according the preference encoding.
        try {
            outputter.setOutputStream(outputStream);
            if (this.isBuildFromDocument) {
                outputter.write(document);
            } else {
                outputter.write(element);
            }
        }
        // Handle IOException and throw TStreamWriteException
        catch (IOException ioException) {
            throw new TStreamWriteException(ioException);
        }
    }

    /**
     * Writes data to the given writer.
     *
     * @param writer denotes the character stream to which the internal object representation is written.
     *
     * @exception TStreamWriteException due to any problems when writing the stream.
     *
     * @pre getElement() != null
     */
    public void writeTo(Writer writer) throws TStreamWriteException {
        Precondition.check("No DOM element is given. Writing to output stream not possible!", getElement() != null);
        // Tell the outputter to write content to writer. Used encoding depends on the writer.
        try {
            outputter.setWriter(writer);
            if (this.isBuildFromDocument) {
                outputter.write(document);
            } else {
                outputter.write(element);
            }
        }
        // Handle IOException and throw TStreamWriteException
        catch (IOException ioException) {
            throw new TStreamWriteException(ioException);
        }
    }

    /**
     * Indicates if this instance can be currently written to an output stream.
     *
     * @return true if invocation of writeTo is possible, false otherwise.
     */
    protected boolean canWriteToOutputStream() {
        return (getElement() != null);
    }

    /**
     * Serialization is controlled by this method. Each time a TDOM4JAdapter instance is
     * serialized, this method is invoked which controlls the way how parts are serialized.
     */
    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        // Write base class state.
        super.writeStateTo(out);
    }

    /**
     * This abstract method serves as a plugin method for writeStateTo. Each time writeStateTo is called
     * with the writeDocument parameter set to true, this method is invoked at the end of the operation.
     * This method is needed for the implementation of serialization.
     *
     * @param out the ObjectOutputStream needed for serialization.
     */
    protected void writeDocumentStateTo(java.io.ObjectOutputStream out) throws IOException {
        out.writeObject(new Boolean(document != null));
        if (document != null) {
            out.writeObject(document);
            out.writeObject(new Boolean(this.isBuildFromDocument));
        }
    }

    /**
     * Deserialization is controlled by this method. Each time a TDOM4JAdapter instance is
     * deserialized, this method is invoked which controlls the way how parts are deserialized.
     */
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        // Initialize the io helpers.
        initializeIOHelpers();
        // Read the base class state.
        super.readStateFrom(in);
    }

    /**
     * This abstract method serves as a plugin method for readStateFrom. Each time readStateFrom is called
     * this method is invoked at the end of the operation. It is needed for the implementation of deserialization.
     */
    protected void readDocumentStateFrom(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        boolean documentIsGiven = ((Boolean) in.readObject()).booleanValue();
        if (documentIsGiven) {
            document = (Document) in.readObject();
            element = document.getRootElement();
            isBuildFromDocument = ((Boolean) in.readObject()).booleanValue();
        } else {
            document = null;
            element = null;
            isBuildFromDocument = false;
        }
    }

    /**
     * Initializes the io output helpers. Here only output helpers are needed.
     * ATTENTION: org.dom4j.XMLWriter is used, if you have no IOHelper in your
     * specific object model, you have to create a
     */
    private void initializeIOHelpers() {
        try {
            OutputFormat outformat = new OutputFormat();
            // Obtain the active encoding from the central preference instance.
            this.encoding = TPreference.getInstance().getEncoding();
            // Set the encoding on the outputter.
            outformat.setEncoding(this.encoding);
            // For serialization encoding attribute shall be included.
            outformat.setOmitEncoding(false);
            this.outputter = new XMLWriter(outformat);
        } catch (UnsupportedEncodingException uee) {
            System.err.println("WARNING: " + uee.getMessage());
        }
    }

    /* The used encoding for writing to an OutputStream.*/
    private String encoding = "";

    /* Indicates if this instance is build from Document.*/
    private boolean isBuildFromDocument = false;

    /* The wrapped DOM document*/
    private Document document = null;

    /* The wrapped DOM4J element.*/
    private Element element = null;

    /* The Outputter needed for the serialization.*/
    private XMLWriter outputter = null;

    /* The ino Namespace object.*/
    private static Namespace inoNamespace = Namespace.get(TInoNamespace.getInstance().getPrefix(), TInoNamespace.getInstance().getUri());

    /**
     * Enables/disables precondition testing due to a global setting.
     */
    private static final boolean PRE_CHECK = Precondition.isEnabled(TDOM4JAdapter.class);

    /**
     * Enables/disables postcondition testing due to a global setting.
     */
    private static final boolean POST_CHECK = Postcondition.isEnabled(TDOM4JAdapter.class);
}
