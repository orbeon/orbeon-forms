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
    xmlns:twit="http://www.orbeon.com/sandbox/tritter"
    exclude-result-prefixes="xs" version="2.0">
    <xsl:variable name="rpp" select="10"/>
    <twit:query>xforms</twit:query>
    <xsl:variable name="page" select="1"/>
    <xsl:template match="@*|node()" name="identity">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>
    <xsl:template match="atom:entry"/>
    <xsl:template match="atom:entry[1]">
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
