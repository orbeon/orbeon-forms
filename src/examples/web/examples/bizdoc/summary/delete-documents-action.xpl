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
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:xdb="http://orbeon.org/oxf/xml/xmldb"
          xmlns:xu="http://www.xmldb.org/xupdate">

    <p:param name="instance" type="input"/>

    <!-- Create list of document ids -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance"/>
        <p:input name="config">
            <document-ids xsl:version="2.0">
                <xsl:for-each select="tokenize(/form/document-id, '\s+')">
                    <document-id><xsl:value-of select="."/></document-id>
                </xsl:for-each>
            </document-ids>
        </p:input>
        <p:output name="data" id="document-ids"/>
    </p:processor>

    <!-- For each document id, delete associated document -->
    <p:for-each href="#document-ids" select="/*/document-id">
        <p:processor name="oxf:xslt">
            <p:input name="data" href="current()"/>
            <p:input name="config">
                <xdb:delete xsl:version="2.0" collection="/db/oxf/adaptive-example">
                    <xsl:text>/document-info[document-id = '</xsl:text>
                    <xsl:value-of select="."/>
                    <xsl:text>']</xsl:text>
                </xdb:delete>
            </p:input>
            <p:output name="data" id="query"/>
        </p:processor>

        <p:processor name="oxf:xmldb-delete">
            <p:input name="datasource" href="../datasource.xml"/>
            <p:input name="query" href="#query"/>
        </p:processor>
    </p:for-each>

</p:config>
