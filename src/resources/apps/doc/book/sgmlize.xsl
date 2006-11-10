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
    xmlns:formatting="http://www.orbeon.com/oxf/doc/formatting"
    extension-element-prefixes="formatting"
    xmlns:xalan="http://xml.apache.org/xalan">

    <xalan:component prefix="formatting" functions="removeEmptyLines">
        <xalan:script lang="javascript">
            function removeEmptyLines(text) {
                var result;
                while (true) {
                    result = text.replace(/\n[ ]*\n/g, "\n")
                    if (result.length == text.length) break;
                    text = result;
                }
                return result;
            }
        </xalan:script>
    </xalan:component>

    <xsl:template match="/">
        <![CDATA[<!doctype linuxdoc system>]]>
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="*">
        <xsl:choose>
            <xsl:when test="count(./node()) = 0">
                <xsl:text>&lt;</xsl:text>
                <xsl:value-of select="name()"/>
                <xsl:call-template name="handle-attributes"/>
                <xsl:text>&gt;</xsl:text>
            </xsl:when>
            <xsl:otherwise>
                <xsl:text>&lt;</xsl:text>
                <xsl:value-of select="name()"/>
                <xsl:call-template name="handle-attributes"/>
                <xsl:text>&gt;</xsl:text>
                <xsl:apply-templates/>
                <xsl:text>&lt;/</xsl:text>
                <xsl:value-of select="name()"/>
                <xsl:text>&gt;</xsl:text>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="text()">
        <xsl:value-of select="formatting:removeEmptyLines(string(.))"/>
    </xsl:template>

    <xsl:template name="handle-attributes">
        <xsl:for-each select="@*">
            <xsl:text> </xsl:text>
            <xsl:value-of select="name()"/>
            <xsl:text>="</xsl:text>
            <xsl:value-of select="."/>
            <xsl:text>"</xsl:text>
        </xsl:for-each>
    </xsl:template>
</xsl:stylesheet>
