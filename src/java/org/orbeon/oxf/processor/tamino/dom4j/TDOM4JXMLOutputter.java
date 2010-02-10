/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.processor.tamino.dom4j;
// namespace dependencies
import com.softwareag.tamino.db.api.namespace.TInoNamespace;
import org.dom4j.*;
import org.orbeon.oxf.util.StringBuilderWriter;

import java.io.*;
import java.util.*;




/**
 ** <p><code>XMLOutputter</code> takes a DOM4J tree and formats it to a
 ** stream as XML.  This formatter performs typical document
 ** formatting.  The XML declaration and processing instructions are
 ** always on their own lines.  Empty elements are printed as
 ** &lt;empty/&gt; and text-only contents are printed as
 ** &lt;tag&gt;content&lt;/tag&gt; on a single line.  Constructor
 ** parameters control the indent amount and whether new lines are
 ** printed between elements.  The other parameters are configurable
 ** through the <code>set*</code> methods.  </p>
 **
 ** <p> For compact machine-readable output create a default
 ** XMLOutputter and call setTrimText(true) to strip any whitespace
 ** that was preserved from the source.  </p>
 **
 ** <p> There are <code>output(...)</code> methods to print any of the
 ** standard DOM4J classes, including <code>Document</code> and
 ** <code>Element</code>, to either a <code>Writer</code> or an
 ** <code>OutputStream</code>.  Warning: using your own
 ** <code>Writer</code> may cause the outputter's preferred character
 ** encoding to be ignored.  If you use encodings other than UTF8, we
 ** recommend using the method that takes an OutputStream instead.
 ** </p>
 **
 ** <p> The methods <code>outputString(...)</code> are for convenience
 ** only; for top performance you should call <code>output(...)</code>
 ** and pass in your own <code>Writer</code> or
 ** <code>OutputStream</code> to if possible.  </p>
 **
 **/
class TDOM4JXMLOutputter implements Cloneable {
	
    /** standard value to indent by, if we are indenting **/
    protected static final String STANDARD_INDENT = "  ";
    
    /** Whether or not to suppress the XML declaration
	 * - default is <code>false</code> */
    private boolean suppressDeclaration = false;
	
    /** The encoding format */
    private String encoding = "UTF8";
	
    /** Whether or not to output the encoding in the XML declaration
	 * - default is <code>false</code> */
    private boolean omitEncoding = false;
	
    /** The default indent is no spaces (as original document) */
    private String indent = null;
	
    /** The initial number of indentations (so you can print a whole
	 document indented, if you like) **/
    // kind of dangerous having same name for instance and local
    // variable, but that's OK...
    private int indentLevel = 0;
    
    /** Whether or not to expand empty elements to
	 * &lt;tagName&gt;&lt;/tagName&gt; - default is <code>false</code> */
    private boolean expandEmptyElements = false;
	
    /** The default new line flag, set to do new lines only as in
	 * original document */
    private boolean newlines = false;
	
    /** New line separator */
    private String lineSeparator = "\r\n";
	
    /** should we preserve whitespace or not in text nodes? */
    private boolean trimText = false;
	
    /** pad string-element boundaries with whitespace **/
    private boolean padText = false;
	
	/** padding characters **/
    protected String padTextString = " ";
	
    /**
	 * <p>
	 * This will create an <code>XMLOutputter</code> with
	 *   no additional whitespace (indent or new lines) added;
	 *   the whitespace from the element text content is fully preserved.
	 * </p>
	 */
    public TDOM4JXMLOutputter() {
    }
	
    /**
	 * <p>
	 * This will create an <code>XMLOutputter</code> with
	 *   the given indent added but no new lines added;
	 *   all whitespace from the element text content is included as well.
	 * </p>
	 *
	 * @param indent  the indent string, usually some number of spaces
	 */
    public TDOM4JXMLOutputter(String indent) {
		this.indent = indent;
    }
	
    /**
	 * <p>
	 * This will create an <code>XMLOutputter</code> with
	 *   the given indent that prints newlines only if <code>newlines</code> is
	 *   <code>true</code>;
	 *   all whitespace from the element text content is included as well.
	 * </p>
	 *
	 * @param indent the indent <code>String</code>, usually some number
	 *        of spaces
	 * @param newlines <code>true</code> indicates new lines should be
	 *                 printed, else new lines are ignored (compacted).
	 */
    public TDOM4JXMLOutputter(String indent, boolean newlines) {
		this.indent = indent;
		this.newlines = newlines;
    }
	
    /**
	 * <p>
	 * This will create an <code>XMLOutputter</code> with
	 *   the given indent and new lines printing only if newlines is
	 *   <code>true</code>, and encoding format <code>encoding</code>.
	 * </p>
	 *
	 * @param indent the indent <code>String</code>, usually some number
	 *        of spaces
	 * @param newlines <code>true</code> indicates new lines should be
	 *                 printed, else new lines are ignored (compacted).
	 * @param encoding set encoding format.
	 */
    public TDOM4JXMLOutputter(String indent, boolean newlines, String encoding) {
		this.indent = indent;
		this.newlines = newlines;
		this.encoding = encoding;
    }
	
    /**
	 * <p> This will create an <code>XMLOutputter</code> with all the
	 * options as set in the given <code>XMLOutputter</code>.  Note
	 * that <code>XMLOutputter two = (XMLOutputter)one.clone();</code>
	 * would work equally well.  </p>
	 *
	 * @param that the XMLOutputter to clone
	 **/
    public TDOM4JXMLOutputter(TDOM4JXMLOutputter that) {
		this.suppressDeclaration = that.suppressDeclaration;
		this.omitEncoding = that.omitEncoding;
		this.indent = that.indent;
		this.indentLevel = that.indentLevel;
		this.expandEmptyElements = that.expandEmptyElements;
		this.newlines = that.newlines;
		this.encoding = that.encoding;
		this.lineSeparator = that.lineSeparator;
		this.trimText = that.trimText;
		this.padText = that.padText;
    }
    
    /**
	 * <p>This will set the new-line separator. The default is
	 * <code>\r\n</code>. Note that if the "newlines" property is
	 * false, this value is irrelevant.  To make it output the system
	 * default line ending string, call
	 * <code>setLineSeparator(System.getProperty("line.separator"))</code>
	 * </p>
	 *
	 * <blockquote>
	 *  We could change this to the System default,
	 *  but I prefer not to make output platform dependent.
	 *  A carriage return, linefeed pair is the most generally
	 *  acceptable linebreak.  Another possibility is to use
	 *  only a line feed, which is XML's preferred (but not required)
	 *  solution. However, both carriage return and linefeed are
	 *  required for many network protocols, and the parser on the
	 *  other end should normalize this.  --Rusty
	 * </blockquote>
	 *
	 * @see #setNewlines(boolean)
	 * @param separator <code>String</code> line separator to use.
	 **/
    public void setLineSeparator(String separator) {
		lineSeparator = separator;
    }
	
    /**
	 * @see #setLineSeparator(String)
	 * @param newlines <code>true</code> indicates new lines should be
	 *                 printed, else new lines are ignored (compacted).
	 **/
    public void setNewlines(boolean newlines) {
		this.newlines = newlines;
    }
	
    /**
	 * @param encoding encoding format
	 **/
    public void setEncoding(String encoding) {
		this.encoding = encoding;
    }
	
    /**
	 * <p>
	 *  This will set whether the XML declaration
	 *  (<code>&lt;?xml version="1.0" encoding="UTF-8"?&gt;</code>)
	 *  includes the encoding of the document. It is common to suppress
	 *  this in uses such as WML and other wireless device protocols.
	 * </p>
	 *
	 * @param omitEncoding <code>boolean</code> indicating whether or not
	 *        the XML declaration should indicate the document encoding.
	 */
    public void setOmitEncoding(boolean omitEncoding) {
		this.omitEncoding = omitEncoding;
    }
	
    /**
	 * <p>
	 *  This will set whether the XML declaration
	 *  (<code>&lt;?xml version="1.0"?&gt;</code>)
	 *  will be suppressed or not. It is common to suppress this in uses such
	 *  as SOAP and XML-RPC calls.
	 * </p>
	 *
	 * @param suppressDeclaration <code>boolean</code> indicating whether or not
	 *        the XML declaration should be suppressed.
	 */
    public void setSuppressDeclaration(boolean suppressDeclaration) {
		this.suppressDeclaration = suppressDeclaration;
    }
	
    /**
	 * <p>
	 *  This will set whether empty elements are expanded from
	 *  <code>&lt;tagName&gt;</code> to
	 *  <code>&lt;tagName&gt;&lt;/tagName&gt;</code>.
	 * </p>
	 *
	 * @param expandEmptyElements <code>boolean</code> indicating whether or not
	 *        empty elements should be expanded.
	 */
    public void setExpandEmptyElements(boolean expandEmptyElements) {
		this.expandEmptyElements = expandEmptyElements;
    }
	
    /**
	 * <p> This will set whether the text is output verbatim (false)
	 *  or with whitespace stripped as per <code>{@link
	 *  org.dom4j.Element#getTextTrim()}</code>.<p>
	 *
	 * <p>Default: false </p>
	 *
	 * @param trimText <code>boolean</code> true=>trim the whitespace,
	 * false=>use text verbatim
	 **/
    public void setTrimText(boolean trimText) {
		this.trimText = trimText;
    }
	
    /**
	 * <p> Ensure that text immediately preceded by or followed by an
	 * element will be "padded" with a single space.  This is used to
	 * allow make browser-friendly HTML, avoiding trimText's
	 * transformation of, e.g.,
	 * <code>The quick &lt;b&gt;brown&lt;/b&gt; fox</code> into
	 * <code>The quick&lt;b&gt;brown&lt;/b&gt;fox</code> (the latter
	 * will run the three separate words together into a single word).
	 *
	 * This setting is not too useful if you haven't also called
	 * {@link #setTrimText(boolean)}.</p>
	 *
	 * <p>Default: false </p>
	 *
	 * @param padText <code>boolean</code> if true, pad string-element
	 * boundaries
	 **/
    public void setPadText(boolean padText) {
		this.padText = padText;
    }
	
    /**
	 * <p> This will set the indent <code>String</code> to use; this
	 *   is usually a <code>String</code> of empty spaces. If you pass
	 *   null, or the empty string (""), then no indentation will
	 *   happen. </p>
	 * Default: none (null)
	 *
	 * @param indent <code>String</code> to use for indentation.
	 **/
    public void setIndent(String indent) {
		// if passed the empty string, change it to null, for marginal
		// performance gains later (can compare to null first instead
		// of calling equals())
		if ("".equals(indent))
			indent = null;
		this.indent = indent;
    }
	
    /**
	 * Set the indent on or off.  If setting on, will use the value of
	 * STANDARD_INDENT, which is usually two spaces.
	 *
	 * @param doIndent if true, set indenting on; if false, set indenting off
	 **/
    public void setIndent(boolean doIndent) {
		if (doIndent) {
			this.indent = STANDARD_INDENT;
		}
		else {
			this.indent = null;
		}
    }
	
    /**
	 * Set the initial indentation level.  This can be used to output
	 * a document (or, more likely, an element) starting at a given
	 * indent level, so it's not always flush against the left margin.
	 * Default: 0
	 *
	 * @param indentLevel the number of indents to start with
	 **/
    public void setIndentLevel(int indentLevel) {
		this.indentLevel = indentLevel;
    }
    
    /**
	 * <p>
	 *   This will set the indent <code>String</code>'s size; an indentSize
	 *   of 4 would result in the indention being equivalent to the
	 *   <code>String</code> "&nbsp;&nbsp;&nbsp;&nbsp;" (four space chars).
	 * </p>
	 *
	 * @param indentSize <code>int</code> number of spaces in indentation.
	 */
    public void setIndentSize(int indentSize) {
		StringBuffer indentBuffer = new StringBuffer();
		for (int i=0; i<indentSize; i++) {
			indentBuffer.append(" ");
		}
		this.indent = indentBuffer.toString();
    }
	
    /**
	 * <p>
	 * This will print the proper indent characters for the given indent level.
	 * </p>
	 *
	 * @param out <code>Writer</code> to write to
	 * @param level <code>int</code> indentation level
	 */
    protected void indent(Writer out, int level) throws IOException {
		if (indent != null && !indent.equals("")) {
			for (int i = 0; i < level; i++) {
				out.write(indent);
			}
		}
    }
    
    /**
	 * <p>
	 * This will print a new line only if the newlines flag was set to true
	 * </p>
	 *
	 * @param out <code>Writer</code> to write to
	 */
    protected void maybePrintln(Writer out) throws IOException  {
		if (newlines) {
			out.write(lineSeparator);
		}
    }
	
    /**
	 * Get an OutputStreamWriter, use preferred encoding.
	 */
    protected Writer makeWriter(OutputStream out)
		throws java.io.UnsupportedEncodingException {
		Writer writer = new OutputStreamWriter
			(new BufferedOutputStream(out), this.encoding);
		return writer;
    }
    
    /**
	 * Get an OutputStreamWriter, use specified encoding.
	 */
    protected Writer makeWriter(OutputStream out, String encoding)
		throws java.io.UnsupportedEncodingException {
		Writer writer = new OutputStreamWriter
			(new BufferedOutputStream(out), encoding);
		return writer;
    }
    
    /**
	 * <p>
	 * This will print the <code>Document</code> to the given output stream.
	 *   The characters are printed using the encoding specified in the
	 *   constructor, or a default of UTF-8.
	 * </p>
	 *
	 * @param doc <code>Document</code> to format.
	 * @param out <code>OutputStream</code> to write to.
	 * @throws <code>IOException</code> - if there's any problem writing.
	 */
    public void output(Document doc, OutputStream out)
		throws IOException {
		Writer writer = makeWriter(out);
		output(doc, writer);
		writer.flush();
    }
	
    /**
	 * <p> This will print the <code>Document</code> to the given
	 * Writer.
	 * </p>
	 *
	 * <p> Warning: using your own Writer may cause the outputter's
	 * preferred character encoding to be ignored.  If you use
	 * encodings other than UTF8, we recommend using the method that
	 * takes an OutputStream instead.  </p>
	 *
	 * <p>Note: as with all Writers, you may need to flush() yours
	 * after this method returns.</p>
	 *
	 * @param doc <code>Document</code> to format.
	 * @param out <code>Writer</code> to write to.
	 * @throws <code>IOException</code> - if there's any problem writing.
	 **/
    public void output(Document doc, Writer writer)
		throws IOException {
		// Print out XML declaration
		if (indentLevel>0)
			indent(writer, indentLevel);
		printDeclaration(doc, writer, encoding);
		
		if (doc.getDocType() != null) {
			if (indentLevel>0)
				indent(writer, indentLevel);
			printDocType(doc.getDocType(), writer);
		}
		
		// Print out root element, as well as any root level
		// comments and processing instructions,
		// starting with no indentation
		Iterator i = doc.getRootElement().elements().iterator();
		while (i.hasNext()) {
			Object obj = i.next();
			if (obj instanceof Element) {
				output(doc.getRootElement(), writer); // at initial indentLevel
			} else if (obj instanceof Comment) {
				printComment((Comment) obj, writer, indentLevel);
			} else if (obj instanceof ProcessingInstruction) {
				printProcessingInstruction((ProcessingInstruction) obj,
										   writer, indentLevel);
			} else if (obj instanceof CDATA) {
				printCDATASection((CDATA)obj, writer, indentLevel);
			}
		}
		
		// Output final line separator
		writer.write(lineSeparator);
    }
	
    // output element
    
    /**
	 * <p>
	 * Print out an <code>{@link Element}</code>, including
	 *   its <code>{@link Attribute}</code>s, and its value, and all
	 *   contained (child) elements etc.
	 * </p>
	 *
	 * @param element <code>Element</code> to output.
	 * @param out <code>Writer</code> to write to.
	 **/
    public void output(Element element, Writer out) throws IOException {
		/****** Original Source *******/
		// If this is the root element we could pre-initialize the
		// namespace stack with the namespaces
		//printElement(element, out, indentLevel, new TDOM4JNamespaceStack());
		/******************************/
		TDOM4JNamespaceStack namespaces = new TDOM4JNamespaceStack();
		Document document = element.getDocument();
		Element rootElement = ( document != null ) ? document.getRootElement() : null;
		// If element is not root element or prefix of root element is not "ino" then add ino namespace to stack
		if ( element != rootElement || !rootElement.getNamespacePrefix().equals( TInoNamespace.PREFIX ) ) {
			//System.err.println( "Adding ino namespace!" );
			Namespace inoNamespace = Namespace.get( TInoNamespace.getInstance().getPrefix() , TInoNamespace.getInstance().getUri() );
			namespaces.push( inoNamespace );
		}
		//System.out.println( "Getting Parent Namespaces!" );
		namespaces = getParentNamespaces( element.getParent() , namespaces );
		printElement( element , out , indentLevel , namespaces );
	}
    
    /**
	 * <p>
	 * Print out an <code>{@link Element}</code>, including
	 *   its <code>{@link Attribute}</code>s, and its value, and all
	 *   contained (child) elements etc.
	 * </p>
	 *
	 * @param element <code>Element</code> to output.
	 * @param out <code>Writer</code> to write to.
	 **/
    public void output(Element element, OutputStream out) throws IOException {
		Writer writer = makeWriter(out);
		output(element, writer);
		writer.flush();         // Flush the output to the underlying stream
    }
	
    /**
	 * <p> This will handle printing out an <code>{@link
	 * Element}</code>'s content only, not including its tag, and
	 * attributes.  This can be useful for printing the content of an
	 * element that contains HTML, like "&lt;description&gt;DOM4J is
	 * &lt;b&gt;fun&gt;!&lt;/description&gt;".  </p>
	 *
	 * @param element <code>Element</code> to output.
	 * @param out <code>Writer</code> to write to.
	 * @param indent <code>int</code> level of indention.  */
    public void outputElementContent(Element element, Writer out)
		throws IOException {
		List mixedContent = element.elements();
		printElementContent(element, out, indentLevel,
							new TDOM4JNamespaceStack(),
							mixedContent);
    }
    
    // output cdata
	
    /**
	 * <p>
	 * Print out a <code>{@link CDATA}</code>
	 * </p>
	 *
	 * @param cdata <code>CDATA</code> to output.
	 * @param out <code>Writer</code> to write to.
	 **/
    public void output(CDATA cdata, Writer out) throws IOException {
		printCDATASection(cdata, out, indentLevel);
    }
    
    /**
	 * <p>
	 * Print out a <code>{@link CDATA}</code>
	 * </p>
	 *
	 * @param cdata <code>CDATA</code> to output.
	 * @param out <code>OutputStream</code> to write to.
	 **/
    public void output(CDATA cdata, OutputStream out) throws IOException {
		Writer writer = makeWriter(out);
		output(cdata, writer);
		writer.flush();         // Flush the output to the underlying stream
    }
	
    // output comment
	
    /**
	 * <p>
	 * Print out a <code>{@link Comment}</code>
	 * </p>
	 *
	 * @param comment <code>Comment</code> to output.
	 * @param out <code>Writer</code> to write to.
	 **/
    public void output(Comment comment, Writer out) throws IOException {
		printComment(comment, out, indentLevel);
    }
    
    /**
	 * <p>
	 * Print out a <code>{@link Comment}</code>
	 * </p>
	 *
	 * @param comment <code>Comment</code> to output.
	 * @param out <code>OutputStream</code> to write to.
	 **/
    public void output(Comment comment, OutputStream out) throws IOException {
		Writer writer = makeWriter(out);
		output(comment, writer);
		writer.flush();         // Flush the output to the underlying stream
    }
    
	
    // output String
	
    /**
	 * <p> Print out a <code>{@link java.lang.String}</code>.  Perfoms
	 * the necessary entity escaping and whitespace stripping.  </p>
	 *
	 * @param string <code>String</code> to output.
	 * @param out <code>Writer</code> to write to.
	 **/
    public void output(String string, Writer out) throws IOException {
		printString(string, out);
    }
    
    /**
	 * <p>
	 * <p> Print out a <code>{@link java.lang.String}</code>.  Perfoms
	 * the necessary entity escaping and whitespace stripping.  </p>
	 * </p>
	 *
	 * @param cdata <code>CDATA</code> to output.
	 * @param out <code>OutputStream</code> to write to.
	 **/
    public void output(String string, OutputStream out) throws IOException {
		Writer writer = makeWriter(out);
		printString(string, writer);
		writer.flush();         // Flush the output to the underlying stream
    }
    
    // output Entity
	
    /**
	 * <p> Print out an <code>{@link Entity}</code>.
	 * </p>
	 *
	 * @param entity <code>Entity</code> to output.
	 * @param out <code>Writer</code> to write to.
	 **/
    public void output(Entity entity, Writer out) throws IOException {
		printEntity(entity, out);
    }
    
    /**
	 * <p>
	 * Print out an <code>{@link Entity}</code>.
	 * </p>
	 *
	 * @param cdata <code>CDATA</code> to output.
	 * @param out <code>OutputStream</code> to write to.
	 **/
    public void output(Entity entity, OutputStream out) throws IOException {
		Writer writer = makeWriter(out);
		printEntity(entity, writer);
		writer.flush();         // Flush the output to the underlying stream
    }
    
	
    // output processingInstruction
	
    /**
	 * <p>
	 * Print out a <code>{@link ProcessingInstruction}</code>
	 * </p>
	 *
	 * @param element <code>ProcessingInstruction</code> to output.
	 * @param out <code>Writer</code> to write to.
	 **/
    public void output(ProcessingInstruction pi, Writer out)
		throws IOException {
		printProcessingInstruction(pi, out, indentLevel);
    }
    
    /**
	 * <p>
	 * Print out a <code>{@link ProcessingInstruction}</code>
	 * </p>
	 *
	 * @param processingInstruction <code>ProcessingInstruction</code>
	 * to output.
	 * @param out <code>OutputStream</code> to write to.
	 **/
    public void output(ProcessingInstruction pi, OutputStream out)
		throws IOException {
		Writer writer = makeWriter(out);
		output(pi, writer);
		writer.flush();         // Flush the output to the underlying stream
    }
    
	
    // output as string
    
    /**
	 * Return a string representing a document.  Uses an internal
	 * StringBuilderWriter. Warning: a String is Unicode, which may not match
	 * the outputter's specified encoding.
	 *
	 * @param doc <code>Document</code> to format.
	 **/
    public String outputString(Document doc) throws IOException {
		StringBuilderWriter out = new StringBuilderWriter();
		output(doc, out);
		out.flush();
		return out.toString();
    }
	
    /**
	 * Return a string representing an element. Warning: a String is
	 * Unicode, which may not match the outputter's specified
	 * encoding.
	 *
	 * @param doc <code>Element</code> to format.
	 **/
    public String outputString(Element element) throws IOException {
		StringBuilderWriter out = new StringBuilderWriter();
		output(element, out);
		out.flush();
		return out.toString();
    }
	
    // internal printing methods
    
    /**
	 * <p>
	 * This will write the declaration to the given Writer.
	 *   Assumes XML version 1.0 since we don't directly know.
	 * </p>
	 *
	 * @param docType <code>DocumentType</code> whose declaration to write.
	 * @param out <code>Writer</code> to write to.
	 */
    protected void printDeclaration(Document doc,
									Writer out,
									String encoding) throws IOException {
		
		// Only print of declaration is not suppressed
		if (!suppressDeclaration) {
			// Assume 1.0 version
			if (encoding.equals("UTF8")) {
				out.write("<?xml version=\"1.0\"");
				if (!omitEncoding) {
					out.write(" encoding=\"UTF-8\"");
				}
				out.write("?>");
			} else {
				out.write("<?xml version=\"1.0\"");
				if (!omitEncoding) {
					out.write(" encoding=\"" + encoding + "\"");
				}
				out.write("?>");
			}
			
			// Print new line after decl always, even if no other new lines
			// Helps the output look better and is semantically
			// inconsequential
			out.write(lineSeparator);
		}
    }
	
    /**
	 * <p>
	 * This will write the DOCTYPE declaration if one exists.
	 * </p>
	 *
	 * @param doc <code>Document</code> whose declaration to write.
	 * @param out <code>Writer</code> to write to.
	 */
    protected void printDocType(DocumentType docType, Writer out)
		throws IOException {
		if (docType == null) {
			return;
		}
		
		String publicID = docType.getPublicID();
		String systemID = docType.getSystemID();
		boolean hasPublic = false;
		
		out.write("<!DOCTYPE ");
		out.write(docType.getElementName());
		if ((publicID != null) && (!publicID.equals(""))) {
			out.write(" PUBLIC \"");
			out.write(publicID);
			out.write("\"");
			hasPublic = true;
		}
		if ((systemID != null) && (!systemID.equals(""))) {
			if (!hasPublic) {
				out.write(" SYSTEM");
			}
			out.write(" \"");
			out.write(systemID);
			out.write("\"");
		}
		out.write(">");
		maybePrintln(out);
    }
	
    /**
	 * <p>
	 * This will write the comment to the specified writer.
	 * </p>
	 *
	 * @param comment <code>Comment</code> to write.
	 * @param out <code>Writer</code> to write to.
	 * @param indentLevel Current depth in hierarchy.
	 */
    protected void printComment(Comment comment,
								Writer out, int indentLevel) throws IOException
    {
		indent(out, indentLevel);
		out.write(comment.asXML());  //XXX
		maybePrintln(out);
    }
	
    /**
	 * <p>
	 * This will write the processing instruction to the specified writer.
	 * </p>
	 *
	 * @param comment <code>ProcessingInstruction</code> to write.
	 * @param out <code>Writer</code> to write to.
	 * @param indentLevel Current depth in hierarchy.
	 */
    protected void printProcessingInstruction(ProcessingInstruction pi,
											  Writer out, int indentLevel) throws IOException {
		
		indent(out, indentLevel);
		out.write(pi.asXML());
		maybePrintln(out);
		
    }
    
    /**
	 * <p>
	 * This will handle printing out an <code>{@link CDATA}</code>,
	 *   and its value.
	 * </p>
	 *
	 * @param cdata <code>CDATA</code> to output.
	 * @param out <code>Writer</code> to write to.
	 * @param indent <code>int</code> level of indention.
	 */
    protected void printCDATASection(CDATA cdata,
									 Writer out, int indentLevel) throws IOException {
		
		indent(out, indentLevel);
		out.write(cdata.asXML());
		maybePrintln(out);
		
    }
	
    /**
	 * <p>
	 * This will handle printing out an <code>{@link Element}</code>,
	 *   its <code>{@link Attribute}</code>s, and its value.
	 * </p>
	 *
	 * @param element <code>Element</code> to output.
	 * @param out <code>Writer</code> to write to.
	 * @param indent <code>int</code> level of indention.
	 * @param namespaces <code>List</code> stack of Namespaces in scope.
	 */
    protected void printElement(Element element, Writer out,
								int indentLevel, TDOM4JNamespaceStack namespaces)
		throws IOException {
		
		List mixedContent = element.elements();
		
		boolean empty = mixedContent.size() == 0;
		boolean stringOnly =
			!empty &&
			mixedContent.size() == 1 &&
			mixedContent.get(0) instanceof String;
		
		// Print beginning element tag
		/* maybe the doctype, xml declaration, and processing instructions
		 should only break before and not after; then this check is
		 unnecessary, or maybe the println should only come after and
		 never before.  Then the output always ends with a newline */
		
		indent(out, indentLevel);
		
		// Print the beginning of the tag plus attributes and any
		// necessary namespace declarations
		out.write("<");
		out.write(element.getQualifiedName());
		int previouslyDeclaredNamespaces = namespaces.size();
		
		Namespace ns = element.getNamespace();
		
		// Add namespace decl only if it's not the XML namespace and it's
		// not the NO_NAMESPACE with the prefix "" not yet mapped
		// (we do output xmlns="" if the "" prefix was already used and we
		// need to reclaim it for the NO_NAMESPACE)
		if (ns != Namespace.XML_NAMESPACE &&
			!(ns == Namespace.NO_NAMESPACE && namespaces.getURI("") == null)) {
			String prefix = ns.getPrefix();
			String uri = namespaces.getURI(prefix);
			if (!ns.getURI().equals(uri)) { // output a new namespace decl
				namespaces.push(ns);
				printNamespace(ns, out);
			}
		}
		
		// Print out additional namespace declarations
		List additionalNamespaces = element.additionalNamespaces();
		if (additionalNamespaces != null) {
			for (int i=0; i<additionalNamespaces.size(); i++) {
				Namespace additional = (Namespace)additionalNamespaces.get(i);
				String prefix = additional.getPrefix();
				String uri = namespaces.getURI(prefix);
				if (!additional.getURI().equals(uri)) {
					namespaces.push(additional);
					printNamespace(additional, out);
				}
			}
		}
		
		printAttributes(element.attributes(), element, out, namespaces);
		
		// handle "" string same as empty
		if (stringOnly) {
			String elementText =
				trimText ? element.getTextTrim() : element.getText();
			if (elementText == null ||
				elementText.equals("")) {
				empty = true;
			}
		}
		
		if (empty) {
			// Simply close up
			if (!expandEmptyElements) {
				out.write(" />");
			} else {
				out.write("></");
				out.write(element.getQualifiedName());
				out.write(">");
			}
			maybePrintln(out);
		} else {
			// we know it's not null or empty from above
			out.write(">");
			
			if (stringOnly) {
				// if string only, print content on same line as tags
				printElementContent(element, out, indentLevel,
									namespaces, mixedContent);
			}
			else {
				maybePrintln(out);
				printElementContent(element, out, indentLevel,
									namespaces, mixedContent);
				indent(out, indentLevel);
			}
			
			out.write("</");
			out.write(element.getQualifiedName());
			out.write(">");
			
			maybePrintln(out);
		}
		
		// remove declared namespaces from stack
		while (namespaces.size() > previouslyDeclaredNamespaces) {
			namespaces.pop();
		}
    }
    
    /**
	 * <p> This will handle printing out an <code>{@link
	 * Element}</code>'s content only, not including its tag,
	 * attributes, and namespace info.  </p>
	 *
	 * @param element <code>Element</code> to output.
	 * @param out <code>Writer</code> to write to.
	 * @param indent <code>int</code> level of indention.  */
    protected void printElementContent(Element element, Writer out,
									   int indentLevel,
									   TDOM4JNamespaceStack namespaces,
									   List mixedContent) throws IOException {
		// get same local flags as printElement does
		// a little redundant code-wise, but not performance-wise
		boolean empty = mixedContent.size() == 0;
		boolean stringOnly =
			!empty &&
			(mixedContent.size() == 1) &&
			mixedContent.get(0) instanceof String;
		
		if (stringOnly) {
			// Print the tag  with String on same line
			// Example: <tag name="value">content</tag>
			String elementText =
				trimText ? element.getTextTrim() : element.getText();
			
			out.write(escapeElementEntities(elementText));
			
		} else {
			/**
			 * Print with children on future lines
			 * Rather than check for mixed content or not, just print
			 * Example: <tag name="value">
			 *             <child/>
			 *          </tag>
			 */
			// Iterate through children
			Object content = null;
			Class justOutput = null;
			for (int i=0, size=mixedContent.size(); i<size; i++) {
				content = mixedContent.get(i);
				// See if text, an element, a PI or a comment
				if (content instanceof Comment) {
					printComment((Comment) content, out, indentLevel + 1);
					justOutput = Comment.class;
				} else if (content instanceof String) {
					if (padText && (justOutput == Element.class))
						out.write(padTextString);
					printString((String)content, out);
					justOutput = String.class;
				} else if (content instanceof Element) {
					if (padText && (justOutput == String.class))
						out.write(padTextString);
					printElement((Element) content, out,
								 indentLevel + 1, namespaces);
					justOutput = Element.class;
				} else if (content instanceof Entity) {
					printEntity((Entity) content, out);
					justOutput = Entity.class;
				} else if (content instanceof ProcessingInstruction) {
					printProcessingInstruction((ProcessingInstruction) content,
											   out, indentLevel + 1);
					justOutput = ProcessingInstruction.class;
				} else if (content instanceof CDATA) {
					printCDATASection((CDATA)content, out, indentLevel + 1);
					justOutput = CDATA.class;
				}
				// Unsupported types are *not* printed
			}
		}
    }  // printElementContent
	
	
    /**
	 * Print a string.  Escapes the element entities, trims interior
	 * whitespace if necessary.
	 **/
    protected void printString(String s, Writer out) throws IOException {
		s = escapeElementEntities(s);
		// patch by Brad Morgan to strip interior whitespace
		// (Brad.Morgan@e-pubcorp.com)
		if (trimText) {
			StringTokenizer tokenizer = new StringTokenizer(s);
			while (tokenizer.hasMoreTokens()) {
				String token = tokenizer.nextToken();
				out.write(token);
				if (tokenizer.hasMoreTokens()) {
					out.write(" ");
				}
			}
		} else {
			out.write(s);
		}
    }
    
    /**
	 * <p>
	 * This will handle printing out an <code>{@link Entity}</code>.
	 * Only the entity reference such as <code>&amp;entity;</code>
	 * will be printed. However, subclasses are free to override
	 * this method to print the contents of the entity instead.
	 * </p>
	 *
	 * @param entity <code>Entity</code> to output.
	 * @param out <code>Writer</code> to write to.  */
    protected void printEntity(Entity entity, Writer out) throws IOException {
		out.write(entity.asXML());
    }
    
	
    /**
	 * <p>
	 *  This will handle printing out any needed <code>{@link Namespace}</code>
	 *    declarations.
	 * </p>
	 *
	 * @param ns <code>Namespace</code> to print definition of
	 * @param out <code>Writer</code> to write to.
	 */
    protected void printNamespace(Namespace ns, Writer out) throws IOException {
		out.write(" xmlns");
		String prefix = ns.getPrefix();
		if (!prefix.equals("")) {
			out.write(":");
			out.write(prefix);
		}
		out.write("=\"");
		out.write(ns.getURI());
		out.write("\"");
    }
	
    /**
	 * <p>
	 * This will handle printing out an <code>{@link Attribute}</code> list.
	 * </p>
	 *
	 * @param attributes <code>List</code> of Attribute objcts
	 * @param out <code>Writer</code> to write to
	 */
    protected void printAttributes(List attributes, Element parent,
								   Writer out, TDOM4JNamespaceStack namespaces)
		throws IOException {
		
		// I do not yet handle the case where the same prefix maps to
		// two different URIs. For attributes on the same element
		// this is illegal; but as yet we don't throw an exception
		// if someone tries to do this
		Set prefixes = new HashSet();
		
		for (int i=0, size=attributes.size(); i < size; i++) {
			Attribute attribute = (Attribute)attributes.get(i);
			Namespace ns = attribute.getNamespace();
			if (ns != Namespace.NO_NAMESPACE && ns != Namespace.XML_NAMESPACE) {
				String prefix = ns.getPrefix();
				String uri = namespaces.getURI(prefix);
				if (!ns.getURI().equals(uri)) { // output a new namespace decl
					printNamespace(ns, out);
					namespaces.push(ns);
				}
			}
			
			out.write(" ");
			out.write(attribute.getQualifiedName());
			out.write("=");
			
			out.write("\"");
			out.write(escapeAttributeEntities(attribute.getValue()));
			out.write("\"");
		}
		
    }
	
    /**
	 * <p>
	 * This will take the five pre-defined entities in XML 1.0 and
	 *   convert their character representation to the appropriate
	 *   entity reference, suitable for XML attributes.
	 * </p>
	 *
	 * @param st <code>String</code> input to escape.
	 * @return <code>String</code> with escaped content.
	 */
    protected String escapeAttributeEntities(String st) {
		StringBuffer buff = new StringBuffer();
		char[] block = st.toCharArray();
		String stEntity = null;
		int i, last;
		
		for (i=0, last=0; i < block.length; i++) {
			switch(block[i]) {
				case '<' :
					stEntity = "&lt;";
					break;
				case '>' :
					stEntity = "&gt;";
					break;
				case '\'' :
					stEntity = "&apos;";
					break;
				case '\"' :
					stEntity = "&quot;";
					break;
				case '&' :
					stEntity = "&amp;";
					break;
				default :
					/* no-op */ ;
			}
			if (stEntity != null) {
				buff.append(block, last, i - last);
				buff.append(stEntity);
				stEntity = null;
				last = i + 1;
			}
		}
		if(last < block.length) {
			buff.append(block, last, i - last);
		}
		
		return buff.toString();
    }
	
	
    /**
	 * <p>
	 * This will take the three pre-defined entities in XML 1.0
	 *   (used specifically in XML elements) and
	 *   convert their character representation to the appropriate
	 *   entity reference, suitable for XML element.
	 * </p>
	 *
	 * @param st <code>String</code> input to escape.
	 * @return <code>String</code> with escaped content.
	 */
    protected String escapeElementEntities(String st) {
		StringBuffer buff = new StringBuffer();
		char[] block = st.toCharArray();
		String stEntity = null;
		int i, last;
		
		for (i=0, last=0; i < block.length; i++) {
			switch(block[i]) {
				case '<' :
					stEntity = "&lt;";
					break;
				case '>' :
					stEntity = "&gt;";
					break;
				case '&' :
					stEntity = "&amp;";
					break;
				default :
					/* no-op */ ;
			}
			if (stEntity != null) {
				buff.append(block, last, i - last);
				buff.append(stEntity);
				stEntity = null;
				last = i + 1;
			}
		}
		if(last < block.length) {
			buff.append(block, last, i - last);
		}
		
		return buff.toString();
    }
	
    /**
	 * parse command-line arguments of the form <code>-omitEncoding
	 * -indentSize 3 ...</code>
	 * @return int index of first parameter that we didn't understand
	 **/
    public int parseArgs(String[] args, int i) {
		for (; i<args.length; ++i) {
			if (args[i].equals("-suppressDeclaration")) {
				setSuppressDeclaration(true);
			}
			else if (args[i].equals("-omitEncoding")) {
				setOmitEncoding(true);
			}
			else if (args[i].equals("-indent")) {
				setIndent(args[++i]);
			}
			else if (args[i].equals("-indentSize")) {
				setIndentSize(Integer.parseInt(args[++i]));
			}
			else if (args[i].equals("-indentLevel")) {
				setIndentLevel(Integer.parseInt(args[++i]));
			}
			else if (args[i].startsWith("-expandEmpty")) {
				setExpandEmptyElements(true);
			}
			else if (args[i].equals("-encoding")) {
				setEncoding(args[++i]);
			}
			else if (args[i].equals("-newlines")) {
				setNewlines(true);
			}
			else if (args[i].equals("-lineSeparator")) {
				setLineSeparator(args[++i]);
			}
			else if (args[i].equals("-trimText")) {
				setTrimText(true);
			}
			else if (args[i].equals("-padText")) {
				setPadText(true);
			}
			else {
				return i;
			}
		}
		return i;
    } // parseArgs
	
	/**
	 ** Gets all the declared namespaces starting at the given element and accumulates the detected namespaces
	 ** within the given namespace stack. The given namespace stack is also returned as the result.
	 ** Please note, that this method has been added for the purpose that not always all given namespaces
	 ** are serialized if they are already given for an ancestor.
	 **/
	private TDOM4JNamespaceStack getParentNamespaces(Element element,TDOM4JNamespaceStack namespaces)  {
		
		if ( element == null )
			return namespaces;
		
		if ( element.getParent() != null )
			namespaces = getParentNamespaces( element.getParent() , namespaces );
		
		Namespace ns = element.getNamespace();
		
		// Add namespace decl only if it's not the XML namespace and it's
		// not the NO_NAMESPACE with the prefix "" not yet mapped
		// (we do output xmlns="" if the "" prefix was already used and we
		// need to reclaim it for the NO_NAMESPACE)
		if ( ns != Namespace.XML_NAMESPACE && !( ns == Namespace.NO_NAMESPACE && namespaces.getURI("") == null ) ) {
			String prefix = ns.getPrefix();
			String uri = namespaces.getURI(prefix);
			// Put a new namespace declaratation into the namespace stack
			if ( !ns.getURI().equals( uri ) ) {
				namespaces.push(ns);
			}
		}
		
		// Add additional namespace declarations if not given yet
		List additionalNamespaces = element.additionalNamespaces();
		if (additionalNamespaces != null) {
			for (int i=0 ; i< additionalNamespaces.size() ; i++ ) {
				Namespace additional = (Namespace)additionalNamespaces.get(i);
				String prefix = additional.getPrefix();
				String uri = namespaces.getURI( prefix );
				if ( !additional.getURI().equals(uri) )
					namespaces.push(additional);
			}
		}
		
		return namespaces;
		
	}
	
}
