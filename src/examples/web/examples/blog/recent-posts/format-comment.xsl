<!--
    Copyright (C) 2005 Orbeon, Inc.

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
                xmlns:saxon="http://saxon.sf.net/"
                xmlns:xs="http://www.w3.org/2001/XMLSchema">

    <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
    <!-- HTML output method used by saxon:serialize() -->
    <xsl:output method="html" omit-xml-declaration="yes" name="html-output"/>

    <!-- We process the comment-->
    <xsl:template match="/">
        <xsl:apply-templates/>
    </xsl:template>

    <!-- Format date -->
    <xsl:template match="date-created">
        <xsl:copy>
            <xsl:value-of select="format-dateTime(., '[MNn] [D], [Y] [H01]:[m01]:[s01] UTC', 'en', (), ())"/>
        </xsl:copy>
    </xsl:template>

    <!-- Format name -->
    <xsl:template match="name">
        <xsl:copy>
            <xsl:choose>
                <xsl:when test="normalize-space(.) = ''">
                    <xsl:text>[Your name will appear here]</xsl:text>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="normalize-space(.)"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:copy>
    </xsl:template>

    <!-- Filter and format text -->
    <xsl:template match="text">
        <xsl:copy>
            <xsl:variable name="comment"
                select="saxon:parse(concat('&lt;root>', ., '&lt;/root>'))/*/node()" as="node()*"/>
            <xsl:variable name="processed-comment" as="element()">
                <root>
                    <xsl:apply-templates select="$comment" mode="comment-text"/>
                </root>
            </xsl:variable>
            <xsl:value-of select="substring-before(substring-after(saxon:serialize($processed-comment, 'html-output'), '>'), '&lt;/root>')"/>
        </xsl:copy>
    </xsl:template>

    <!-- These templates are used to filter comment text -->
    <xsl:template match="p | ul | ol | li | blockquote | a | br | code | sub | sup | i | b" mode="comment-text">
        <xsl:copy>
            <xsl:apply-templates mode="comment-text"/>
        </xsl:copy>
    </xsl:template>
    <xsl:template match="a" mode="comment-text">
        <xsl:copy>
            <xsl:copy-of select="@href"/>
            <xsl:apply-templates mode="comment-text"/>
        </xsl:copy>
    </xsl:template>
    <xsl:template match="text()" mode="comment-text">
        <xsl:copy/>
    </xsl:template>
</xsl:stylesheet>
