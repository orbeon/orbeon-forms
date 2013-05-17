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
        <!-- Call the data access layer -->
        <p:processor name="oxf:pipeline">
            <p:input name="config" href="../data-access/delegate/delete-document.xpl"/>
            <p:input name="document-id" href="current()"/>
        </p:processor>
    </p:for-each>

</p:config>
