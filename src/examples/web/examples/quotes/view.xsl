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
            xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
            xmlns:f="http://orbeon.org/oxf/xml/formatting"
            xmlns:xhtml="http://www.w3.org/1999/xhtml"
            xsl:version="2.0">
    <xhtml:head><xhtml:title>Famous Quote</xhtml:title></xhtml:head>
    <xhtml:body>
        <xhtml:p>
            <xsl:copy-of select="/return/body/node()"/>
            <xsl:if test="string-length(/return/source) > 0">
                <xhtml:p>
                    <xsl:text>&#160;&#160;&#160;-&#160;</xsl:text>
                    <xsl:copy-of select="/return/source/node()"/>
                </xhtml:p>
            </xsl:if>
        </xhtml:p>
    </xhtml:body>
</xhtml:html>
