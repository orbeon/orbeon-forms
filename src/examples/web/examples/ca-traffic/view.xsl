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
<xhtml:html xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
            xmlns:xforms="http://www.w3.org/2002/xforms"
            xmlns:f="http://orbeon.org/oxf/xml/formatting"
            xmlns:xhtml="http://www.w3.org/1999/xhtml"
            xsl:version="2.0">
    <xhtml:head>
        <xhtml:title>California Traffic</xhtml:title>
    </xhtml:head>
    <xhtml:body>
        <xforms:group>
            <xhtml:p>
                Enter an highway number (e.g. 101):
                <xforms:input ref="highway"/>
                <xsl:text>&#160;</xsl:text>
                <xforms:submit>
                    <xforms:label>Get Traffic</xforms:label>
                </xforms:submit>
            </xhtml:p>
        </xforms:group>
        <xsl:if test="document('oxf:instance')/highway != ''">
            <pre><xsl:value-of select="/return"/></pre>
        </xsl:if>
    </xhtml:body>
</xhtml:html>
