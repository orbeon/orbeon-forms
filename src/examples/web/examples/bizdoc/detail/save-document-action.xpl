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

    <!-- Strip instance of XForms annotations -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance"/>
        <p:input name="config">
            <xsl:transform version="2.0" xmlns:xxforms="http://orbeon.org/oxf/xml/xforms">
                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
                <xsl:template match="@xxforms:*"/>
            </xsl:transform>
        </p:input>
        <p:output name="data" id="stripped-instance"/>
    </p:processor>

    <!-- Retrieve existing document if possible -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../summary/find-document-action.xpl"/>
        <p:input name="instance" href="#stripped-instance"/>
        <p:output name="data" id="result"/>
    </p:processor>

    <!-- Update or insert -->
    <p:choose href="#result">
        <p:when test="/document-info/document-id != ''">
            <!-- Document exists already, call the data access layer to update it -->
            <p:processor name="oxf:pipeline">
                <p:input name="config" href="../data-access/delegate/update-document.xpl"/>
                <p:input name="document-info" href="aggregate('document-info', #stripped-instance#xpointer(/form/document-id | /form/document))"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <!-- Document does not exist, call the data access layer to insert it -->
            <p:processor name="oxf:pipeline">
                <p:input name="config" href="../data-access/delegate/create-document.xpl"/>
                <p:input name="document-info" href="aggregate('document-info', #stripped-instance#xpointer(/form/document-id | /form/document))"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

</p:config>
