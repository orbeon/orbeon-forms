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
        <xhtml:title>XSLT 2.0 Knight Tour</xhtml:title>
    </xhtml:head>
    <xhtml:body>
        <xforms:group ref="/form">
            <xhtml:p>
                Enter a start case, for example <i>b1</i> or <i>h8</i>:
                <xforms:input ref="start" xhtml:size="2"/>
                <xsl:text> </xsl:text>
                <xforms:submit>
                    <xforms:label>Change</xforms:label>
                </xforms:submit>
            </xhtml:p>

            <xsl:for-each select="/html/body//table[1]">
                <xhtml:table class="gridtable" width="auto">
                    <xhtml:tr>
                        <xhtml:th/>
                        <xsl:for-each select="1 to 8">
                            <xhtml:th>
                                <xsl:value-of select="codepoints-to-string(string-to-codepoints('a') + current() - 1)"/>
                            </xhtml:th>
                        </xsl:for-each>
                    </xhtml:tr>
                    <xsl:for-each select="tr">
                        <xsl:variable name="position" select="position()"/>
                        <xhtml:tr>
                            <xhtml:th>
                                <xsl:value-of select="8 - $position + 1"/>
                            </xhtml:th>
                            <xsl:for-each select="td">
                                <xhtml:td align="center">
                                    <xsl:copy-of select="node()"/>
                                </xhtml:td>
                            </xsl:for-each>
                        </xhtml:tr>
                    </xsl:for-each>
                </xhtml:table>
            </xsl:for-each>
        </xforms:group>
    </xhtml:body>
</xhtml:html>
