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
    <xhtml:head>
        <xhtml:title>Java Properties</xhtml:title>
    </xhtml:head>
    <xhtml:body>
        <xhtml:p>
            This is a list of properties of the underlying Java VM:
        </xhtml:p>
        <xhtml:table class="gridtable">
            <xhtml:tr>
                <xhtml:th>Name</xhtml:th>
                <xhtml:th>Value</xhtml:th>
            </xhtml:tr>
            <xsl:variable name="path-separator" select="/properties/property[name = 'path.separator']/value"/>
            <xsl:for-each select="/properties/property">
                <xhtml:tr>
                    <xhtml:td><xsl:value-of select="name"/></xhtml:td>
                    <xhtml:td>
                        <xsl:analyze-string select="value" regex="{$path-separator}">
                            <xsl:non-matching-substring>
                                <xsl:value-of select="."/>
                            </xsl:non-matching-substring>
                            <xsl:matching-substring>
                                <xsl:value-of select="$path-separator"/>
                                <xhtml:wbr/>
                            </xsl:matching-substring>
                        </xsl:analyze-string>
                    </xhtml:td>
                </xhtml:tr>
            </xsl:for-each>
        </xhtml:table>
    </xhtml:body>
</xhtml:html>
