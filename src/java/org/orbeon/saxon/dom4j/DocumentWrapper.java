package org.orbeon.saxon.dom4j;
import org.dom4j.Document;
import org.orbeon.oxf.processor.xforms.output.InstanceData;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.om.NamePool;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.type.Type;

/**
  * The root node of an XPath tree. (Or equivalently, the tree itself).<P>
  * This class should have been named Root; it is used not only for the root of a document,
  * but also for the root of a result tree fragment, which is not constrained to contain a
  * single top-level element.
  * @author <A HREF="mailto:michael.h.kay@ntlworld.com>Michael H. Kay</A>
  */

public class DocumentWrapper extends NodeWrapper implements DocumentInfo {

    protected NamePool namePool;
    protected String baseURI;
    protected int documentNumber;
    protected Configuration configuration;

    /**
     * Create a Saxon wrapper for a JDOM document
     * @param doc     The JDOM document
     * @param baseURI The base URI for all the nodes in the document
     */

    public DocumentWrapper(Document doc, String baseURI) {
        super(doc, null, 0);
        node = doc;
        nodeKind = Type.DOCUMENT;

        // Extract the location info is available
        if(baseURI == null) {
            Object data = doc.getRootElement().getData();
            if(data instanceof LocationData)   {
                this.baseURI = ((LocationData)data).getSystemID();
            } else if(data instanceof InstanceData) {
                this.baseURI = ((InstanceData)data).getSystemId();
            }
        }
        else
            this.baseURI = baseURI;

        docWrapper = this;
        namePool = NamePool.getDefaultNamePool();
    }

    /**
     * Wrap a node in the JDOM document.
     * @param node The node to be wrapped. This must be a node in the same document
     * (the system does not check for this).
     * @return the wrapping NodeInfo object
     */

    public NodeInfo wrap(Object node) {
        if (node==this.node) {
            return this;
        }
        return makeWrapper(node, this);
    }



    /**
    * Set the name pool used for all names in this document
    */

    public void setNamePool(NamePool pool) {
        namePool = pool;
        documentNumber = pool.allocateDocumentNumber(this);
    }

    /**
    * Get the name pool used for the names in this document
    */

    public NamePool getNamePool() {
        return namePool;
    }

    /**
    * Get the unique document number
    */

    public int getDocumentNumber() {
        return documentNumber;
    }

    /**
    * Get the element with a given ID, if any
    * @param id the required ID value
    * @return null: JDOM does not provide any information about attribute types.
    */

    public NodeInfo selectID(String id) {
        return null;
    }

    /**
    * Get the unparsed entity with a given name
    * @param name the name of the entity
    * @return null: JDOM does not provide access to unparsed entities
    */

    public String[] getUnparsedEntity(String name) {
        return null;
    }

    /**
     * Get the configuration previously set using setConfiguration
     */

    public Configuration getConfiguration() {
        return configuration;
    }

    /**
     * Set the configuration, which defines the name pool used for all names in this document.
     * This is always called after a new
     * document has been created. The implementation must register the name pool with the document,
     * so that it can be retrieved using getNamePool(). It must also call NamePool.allocateDocumentNumber(),
     * and return the relevant document number when getDocumentNumber() is subsequently called.
     *
     * @param config The configuration to be used
     */

    public void setConfiguration(Configuration config) {
        this.configuration = config;
        documentNumber = config.getNamePool().allocateDocumentNumber(this);
    }


}

//
// The contents of this file are subject to the Mozilla Public License Version 1.0 (the "License");
// you may not use this file except in compliance with the License. You may obtain a copy of the
// License at http://www.mozilla.org/MPL/
//
// Software distributed under the License is distributed on an "AS IS" basis,
// WITHOUT WARRANTY OF ANY KIND, either express or implied.
// See the License for the specific language governing rights and limitations under the License.
//
// The Original Code is: all this file.
//
// The Initial Developer of the Original Code is
// Michael Kay (michael.h.kay@ntlworld.com).
//
// Portions created by (your name) are Copyright (C) (your legal entity). All Rights Reserved.
//
// Contributor(s): none.
//
