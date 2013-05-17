<!--
    Copyright (C) 2004 Orbeon, Inc.
  
    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.
  
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.
  
    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:oxf="http://www.orbeon.com/oxf/processors"
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <!-- List of documents to import -->
    <p:processor name="oxf:identity">
        <p:input name="data">
            <documents>
                <document>claim-1.xml</document>
                <document>claim-2.xml</document>
                <document>claim-3.xml</document>
            </documents>
        </p:input>
        <p:output name="data" id="documents"/>
    </p:processor>

    <!-- For each document, read it, format it and insert it -->
    <p:for-each href="#documents" select="/*/document">

        <p:processor name="oxf:url-generator">
            <p:input name="config" href="aggregate('config', aggregate('url', current()#xpointer(concat('../schema/', string(/)))))"/>
            <p:output name="data" id="file"/>
        </p:processor>

        <!-- Dynamically generate document to insert with a new id -->
        <p:processor name="oxf:unsafe-xslt">
            <p:input name="data" href="#file"/>
            <p:input name="config">
                <document-info xsl:version="2.0" xmlns:uuid="java:org.orbeon.oxf.util.UUIDUtils">
                    <document-id>
                        <!-- Create a document id by calling some Java code -->
                        <xsl:value-of select="uuid:createPseudoUUID()"/>
                    </document-id>
                    <document>
                        <xsl:copy-of select="/*"/>
                    </document>
                </document-info>
            </p:input>
            <p:output name="data" id="document-info"/>
        </p:processor>

        <!-- Call the data access layer -->
        <p:processor name="oxf:pipeline">
            <p:input name="config" href="../data-access/delegate/create-document.xpl"/>
            <p:input name="document-info" href="#document-info"/>
        </p:processor>

    </p:for-each>

</p:config>
