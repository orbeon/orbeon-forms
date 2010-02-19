<!--
    Copyright (C) 2006-2007 Orbeon, Inc.

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
    will or, even better, copy it as theme-[yourapp].xsl and refer to the new name from the epilogue pipeline.
-->
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:version="java:org.orbeon.oxf.common.Version">

    <!-- Orbeon Forms version -->
    <xsl:variable name="orbeon-forms-version" select="version:getVersion()" as="xs:string"/>

    <!-- - - - - - - Themed page template - - - - - - -->
    <xsl:template match="/*">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <xhtml:head>
                <!-- Add meta as early as possible -->
                <xsl:apply-templates select="/xhtml:html/xhtml:head/xhtml:meta"/>
                <!-- Handle head elements except scripts -->
                <xsl:apply-templates select="/xhtml:html/xhtml:head/(xhtml:link | xhtml:style)"/>
                <!-- Title -->
                <xhtml:title>
                    <xsl:apply-templates select="/xhtml:html/xhtml:head/xhtml:title/@*"/>
                    <xsl:choose>
                        <xsl:when test="/xhtml:html/xhtml:head/xhtml:title != ''">
                            <xsl:value-of select="/xhtml:html/xhtml:head/xhtml:title"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:value-of select="(/xhtml:html/xhtml:body/xhtml:h1)[1]"/>
                        </xsl:otherwise>
                    </xsl:choose>
                </xhtml:title>
                <!-- Orbeon Forms version -->
                <xhtml:meta name="generator" content="Orbeon Forms {$orbeon-forms-version}"/>
                <!-- Favicon -->
                <xhtml:link rel="shortcut icon" href="/ops/images/orbeon-icon-16.ico"/>
                <xhtml:link rel="icon" href="/ops/images/orbeon-icon-16.png" type="image/png"/>
            </xhtml:head>
            <xhtml:body>
                <!-- Copy body attributes -->
                <xsl:apply-templates select="/xhtml:html/xhtml:body/@*"/>
                <!-- Copy body -->
                <xsl:apply-templates select="/xhtml:html/xhtml:body/node()"/>
            </xhtml:body>
            <!-- Scripts at the bottom of the page. This is not valid HTML, but it is a recommended practice for
                 performance as of early 2008. See http://developer.yahoo.com/performance/rules.html#js_bottom -->
            <xsl:for-each select="/xhtml:html/xhtml:head/xhtml:script">
                <xsl:element name="xhtml:{local-name()}" namespace="{namespace-uri()}">
                    <xsl:apply-templates select="@*|node()"/>
                </xsl:element>
            </xsl:for-each>
        </xsl:copy>
    </xsl:template>

    <!-- Simply copy everything that's not matched -->
    <xsl:template match="@*|node()" priority="-2">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
