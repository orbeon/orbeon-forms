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
<xhtml:html xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml"
            xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
            xsl:version="2.0">
    <xhtml:head><xhtml:title>XForms Wizard - Step 4</xhtml:title></xhtml:head>
    <xhtml:body>
        <xhtml:p>
            <xhtml:table class="gridtable">
                <xhtml:tr>
                    <xhtml:th>Your first name:</xhtml:th>
                    <xhtml:td><xsl:value-of select="/form/user/firstname"/></xhtml:td>
                </xhtml:tr>
                <xhtml:tr>
                    <xhtml:th>Your last name:</xhtml:th>
                    <xhtml:td><xsl:value-of select="/form/user/lastname"/></xhtml:td>
                </xhtml:tr>
                <xhtml:tr>
                    <xhtml:th>Your SSN:</xhtml:th>
                    <xhtml:td><xsl:value-of select="/form/user/ssn"/></xhtml:td>
                </xhtml:tr>
            </xhtml:table>
        </xhtml:p>
    </xhtml:body>
</xhtml:html>
