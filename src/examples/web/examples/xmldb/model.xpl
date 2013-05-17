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
          xmlns:xdb="http://orbeon.org/oxf/xml/xmldb"
          xmlns:xu="http://www.xmldb.org/xupdate">

    <p:param name="instance" type="input"/>
    <p:param name="data"     type="output"/>

    <!-- Delete a document
    <p:processor name="oxf:xmldb-delete">
        <p:input name="datasource" href="datasource.xml"/>
        <p:input name="query">
            <xdb:delete collection="/db/oxf/xmldb-example">/musician[@id=12345]</xdb:delete>
        </p:input>
    </p:processor>
    -->

    <!-- Insert a document -->
    <p:processor name="oxf:xmldb-insert">
        <p:input name="datasource" href="datasource.xml"/>
        <p:input name="query">
            <xdb:insert collection="/db/oxf/xmldb-example" create-collection="true" resource-id="musician1"/>
        </p:input>
        <p:input name="data" href="input-document.xml"/>
    </p:processor>

    <!-- Update a document
    <p:processor name="oxf:xmldb-update">
        <p:input name="datasource" href="datasource.xml"/>
        <p:input name="query">
            <xdb:update collection="/db/oxf/xmldb-example" resource-id="musician1">
                <xu:modifications version="1.0">
                    <xu:update select="/*/@id">12345</xu:update>
                </xu:modifications>
            </xdb:update>
        </p:input>
    </p:processor>
    -->

    <!-- Execute a query provided by the user -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance#xpointer(/form/query)"/>
        <p:input name="config">
            <xdb:query collection="/db/oxf/xmldb-example"
                xmlns:saxon="http://saxon.sf.net/"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xsl:version="2.0">
                <xsl:copy-of select="saxon:parse(concat('&lt;xquery>', string(/), '&lt;/xquery>'))/*/node()"/>
            </xdb:query>
        </p:input>
        <p:output name="data" id="query"/>
    </p:processor>

    <p:processor name="oxf:xmldb-query">
        <p:input name="datasource" href="datasource.xml"/>
        <p:input name="query" href="#query"/>
        <p:output name="data" id="output-document"/>
    </p:processor>

    <!-- Aggregate the input and output documents and return them -->
    <p:processor name="oxf:identity">
        <p:input name="data" href="aggregate('documents', aggregate('input', input-document.xml), aggregate('output', #output-document))"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
