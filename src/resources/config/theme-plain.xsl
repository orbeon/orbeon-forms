<!--
  Copyright (C) 2010 Orbeon, Inc.

  This program is free software; you can redistribute it and/or modify it under the terms of the
  GNU Lesser General Public License as published by the Free Software Foundation; either version
  2.1 of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Lesser General Public License for more details.

  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  -->
<!--
    This is a very simple theme that shows you how to create a common layout for all your pages. You can modify it at
    will or, even better, copy it as theme.xsl under your application folder where it will be picked up. For example,
    if your app is my-app: resources/my-app/theme.xsl.
-->
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xh="http://www.w3.org/1999/xhtml"
    xmlns:version="java:org.orbeon.oxf.common.Version">

    <xsl:variable
        name="version"
        as="xs:string?"
        select="version:versionStringIfAllowedOrEmpty()"
        xmlns:version="java:org.orbeon.oxf.common.Version"/>

    <xsl:template match="xh:head">
        <xsl:copy>
            <xsl:call-template name="head"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template name="head">
        <xsl:apply-templates select="@*"/>
        <!-- Handle head elements except scripts -->
        <!-- See https://github.com/orbeon/orbeon-forms/issues/2311 -->
        <xsl:apply-templates select="xh:meta | xh:link | xh:style | comment()[starts-with(., '[if')]"/>
        <!-- Title -->
        <xh:title>
            <xsl:apply-templates select="xh:title/@*"/>
            <xsl:choose>
                <xsl:when test="xh:title != ''">
                    <xsl:value-of select="xh:title"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="(/xh:html/xh:body/xh:h1)[1]"/>
                </xsl:otherwise>
            </xsl:choose>
        </xh:title>
        <xsl:if test="$version">
            <!-- Orbeon Forms version -->
            <xh:meta name="generator" content="{$version}"/>
        </xsl:if>
        <!-- Handle head scripts if present -->
        <xsl:apply-templates select="xh:script"/>
    </xsl:template>

    <xsl:template match="xh:body">
        <xsl:copy>
            <xsl:apply-templates select="@* except @class"/>
            <xsl:attribute name="class" select="string-join(('orbeon', @class), ' ')"/>
            <xsl:apply-templates select="node()"/>
        </xsl:copy>
    </xsl:template>

    <!-- Simply copy everything that's not matched -->
    <xsl:template match="@*|node()" priority="-2">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
