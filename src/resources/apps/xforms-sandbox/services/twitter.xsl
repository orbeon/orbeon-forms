<?xml version="1.0" encoding="UTF-8"?>
<!--
    Copyright (C) 2009 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<!--

    This is kind of hacky, but this transformation also serves as an instance and is
    modified by setvalue elements in the pageflow!

-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:atom="http://www.w3.org/2005/Atom"
    xmlns:twit="http://www.orbeon.com/sandbox/twitter" exclude-result-prefixes="xs atom" version="2.0"
    xpath-default-namespace="http://www.w3.org/2005/Atom">
    <xsl:variable name="rpp" select="10"/>
    <xsl:variable name="q">
        <q xmlns="">xforms OR orbeon OR ebruchez OR avernet</q>
    </xsl:variable>
    <xsl:variable name="page" select="1"/>
    <xsl:template match="@*|node()" name="identity">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>
    <xsl:template match="atom:entry"/>
    <xsl:template match="atom:entry[1]">
        <twit:nbResults>
            <xsl:value-of select="count(../atom:entry)"/>
        </twit:nbResults>
        <twit:q>
            <xsl:value-of select="$q"/>
        </twit:q>
        <twit:rpp>
            <xsl:value-of select="$rpp"/>
        </twit:rpp>
        <twit:page>
            <xsl:value-of select="$page"/>
        </twit:page>
        <twit:sort>
            <twit:by>
                <xsl:value-of
                    select="doc('input:instance')/xsl:stylesheet/xsl:template/xsl:apply-templates/xsl:sort/@select"
                />
            </twit:by>
            <twit:order>
                <xsl:value-of
                    select="doc('input:instance')/xsl:stylesheet/xsl:template/xsl:apply-templates/xsl:sort/@order"
                />
            </twit:order>
        </twit:sort>
        <xsl:apply-templates select="../atom:entry" mode="paginate">
            <xsl:sort select="position()" order="ascending"/>
        </xsl:apply-templates>
    </xsl:template>
    <xsl:template match="atom:entry" mode="paginate">
        <xsl:if test="position() > ($page - 1) * $rpp and position() &lt;= $page * $rpp">
            <xsl:call-template name="identity"/>
        </xsl:if>
    </xsl:template>

</xsl:stylesheet>
