<!--
    Copyright (C) 2006 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<xsl:stylesheet version="2.0"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:xforms="http://www.w3.org/2002/xforms"
        xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xhtml="http://www.w3.org/1999/xhtml"
        xmlns:f="http//www.orbeon.com/function">

    <xsl:template match="/">
        <xhtml:html>
            <xhtml:head>
                <xhtml:style type="text/css">
                    .info      { font-size: 10px; color: #aaa; }
                    .even-line { background: #ffb ; }
                    .odd-line  { background: #ddf; }
                    .label     { font-style: italic; }
                </xhtml:style>
            </xhtml:head>
            <xhtml:body>
                <xhtml:h1>Java Profiling: Split By Functionality</xhtml:h1>
                <xhtml:table class="gridtable">
                    <xsl:for-each select="/analysis/functionality">
                        <xhtml:tr>
                            <xhtml:td>
                                <xsl:value-of select="@name"/>
                            </xhtml:td>
                            <xhtml:td align="right">
                                <xsl:value-of select="round(@frequency * 1000) div 10"/>
                                <xsl:text>%</xsl:text>
                            </xhtml:td>
                        </xhtml:tr>
                    </xsl:for-each>
                </xhtml:table>
            </xhtml:body>
        </xhtml:html>

    </xsl:template>

</xsl:stylesheet>