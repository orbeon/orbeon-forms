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
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml">

    <xsl:template match="/">
        <xhtml:html>
            <xhtml:head><xhtml:title>XQuery Sandbox</xhtml:title></xhtml:head>
            <xhtml:body>
                <xforms:group ref="form">
                    <table class="gridtable">
                        <tr>
                            <th>Input Document</th>
                            <td style="padding: 1em">
                                <f:xml-source>
                                    <xsl:copy-of select="/root/input/*"/>
                                </f:xml-source>
                            </td>
                        </tr>
                        <tr>
                            <th>XQuery</th>
                            <td style="padding: 1em">
                                <xsl:variable name="input-lines">
                                    <xsl:call-template name="lines">
                                        <xsl:with-param name="text" select="string(document('oxf:instance')/form/xquery)"/>
                                    </xsl:call-template>
                                </xsl:variable>
                                <xsl:variable name="lines">
                                    <xsl:choose>
                                        <xsl:when test="$input-lines >= 10">
                                            <xsl:value-of select="$input-lines"/>
                                        </xsl:when>
                                        <xsl:otherwise>10</xsl:otherwise>
                                    </xsl:choose>
                                </xsl:variable>
                                <xforms:textarea ref="xquery" xhtml:style="width: 100%" xhtml:rows="{$lines}"/>
                                <xhtml:p>
                                    <xforms:submit xxforms:appearance="button">
                                        <xforms:label>Execute</xforms:label>
                                    </xforms:submit>
                                </xhtml:p>
                            </td>
                        </tr>
                        <tr>
                            <th>Output Document</th>
                            <td style="padding: 1em">
                                <f:xml-source>
                                    <xsl:copy-of select="/root/output/*"/>
                                </f:xml-source>
                            </td>
                        </tr>
                    </table>
                </xforms:group>
            </xhtml:body>
        </xhtml:html>
    </xsl:template>

    <xsl:template name="lines">
        <xsl:param name="text"/>
        <xsl:param name="count" select="0"/>

        <xsl:choose>
            <xsl:when test="contains($text, '&#10;')">
                <xsl:call-template name="lines">
                    <xsl:with-param name="text" select="substring-after($text, '&#10;')"/>
                    <xsl:with-param name="count" select="$count + 1"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="$count + 1"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

</xsl:stylesheet>
