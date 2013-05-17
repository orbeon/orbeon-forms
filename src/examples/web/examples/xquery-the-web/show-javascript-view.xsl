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
<xhtml:html xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:f="http://orbeon.org/oxf/xml/formatting"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
    xsl:version="2.0">

    <xsl:variable name="instance" as="element()" select="/root/instance"/>
    
    <xhtml:head><xhtml:title>XQuery The Web - JavaScript</xhtml:title></xhtml:head>
    <xhtml:body>
        <p>
            Include this code in your HTML page where you want the text to be included:
        </p>
        <input type="text" size="45" value="{/javascript}"/>
        <xforms:group>
            <xforms:submit xxforms:appearance="link">
                <xforms:label>Back</xforms:label>
                <xforms:setvalue ref="/instance/action">back</xforms:setvalue>
            </xforms:submit>
        </xforms:group>
    </xhtml:body>
</xhtml:html>
