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
<xh:html xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
            xmlns:xf="http://www.w3.org/2002/xforms"
            xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
            xmlns:f="http://orbeon.org/oxf/xml/formatting"
            xmlns:xh="http://www.w3.org/1999/xhtml"
            xsl:version="2.0">
    <xh:head>
        <xh:title>Java Properties</xh:title>
    </xh:head>
    <xh:body>
        <xh:p>
            This is a list of properties of the underlying Java VM:
        </xh:p>
        <xh:table class="gridtable">
            <xh:tr>
                <xh:th>Name</xh:th>
                <xh:th>Value</xh:th>
            </xh:tr>
            <xsl:variable name="path-separator" select="/properties/property[name = 'path.separator']/value"/>
            <xsl:for-each select="/properties/property">
                <xh:tr>
                    <xh:td><xsl:value-of select="name"/></xh:td>
                    <xh:td>
                        <xsl:analyze-string select="value" regex="{$path-separator}">
                            <xsl:non-matching-substring>
                                <xsl:value-of select="."/>
                            </xsl:non-matching-substring>
                            <xsl:matching-substring>
                                <xsl:value-of select="$path-separator"/>
                                <xh:wbr/>
                            </xsl:matching-substring>
                        </xsl:analyze-string>
                    </xh:td>
                </xh:tr>
            </xsl:for-each>
        </xh:table>
    </xh:body>
</xh:html>
