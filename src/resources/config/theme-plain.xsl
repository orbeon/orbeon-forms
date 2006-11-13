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
<!--
    This is a very simple theme that shows you how to create a common layout for all your pages. You
    can modify it at will.
-->
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:f="http://orbeon.org/oxf/xml/formatting"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms">

    <!-- - - - - - - Themed page template - - - - - - -->
    <xsl:template match="/">
        <xhtml:html>
            <xhtml:head>
                <!-- Standard scripts/styles -->
                <!-- NOTE: The XForms engine may place additional scripts and stylesheets here as needed -->
                <xhtml:link rel="stylesheet" href="/config/theme/orbeon.css" type="text/css"/>
                <!-- Handle meta elements -->
                <xsl:apply-templates select="/xhtml:html/xhtml:head/xhtml:meta"/>
                <!-- Handle user-defined links -->
                <xsl:apply-templates select="/xhtml:html/xhtml:head/xhtml:link"/>
                <!-- Handle user-defined stylesheets -->
                <xsl:apply-templates select="/xhtml:html/xhtml:head/xhtml:style"/>
                <!-- Handle user-defined scripts -->
                <xsl:apply-templates select="/xhtml:html/xhtml:head/xhtml:script"/>
                <!-- Title -->
                <xhtml:title>
                    <xsl:choose>
                        <xsl:when test="/xhtml:html/xhtml:head/xhtml:title != ''">
                            <xsl:value-of select="/xhtml:html/xhtml:head/xhtml:title"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:value-of select="(/xhtml:html/xhtml:body/xhtml:h1)[1]"/>
                        </xsl:otherwise>
                    </xsl:choose>
                </xhtml:title>
            </xhtml:head>
            <xhtml:body>
                <!-- Copy body attributes -->
                <xsl:apply-templates select="/xhtml:html/xhtml:body/@*"/>
                <!-- Copy body -->
                <xsl:apply-templates select="/xhtml:html/xhtml:body/node()"/>
            </xhtml:body>
        </xhtml:html>
    </xsl:template>

    <!-- - - - - - - XForms adjustments (should probably be native) - - - - - - -->

    <!-- Populate content of loading indicator -->
    <xsl:template match="xhtml:span[tokenize(@class, ' ') = 'xforms-loading-loading']">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            Loading...
        </xsl:copy>
    </xsl:template>

    <!-- Add images to the page instead of using CSS to get around IE reloading issue -->
    <xsl:template match="xhtml:label[tokenize(@class, ' ') = 'xforms-help']" >
        <xsl:variable name="image-class" as="xs:string+" select="
            ('xforms-help-image',
            (for $c in tokenize(@class, ' ') return if ($c = 'xforms-help') then () else $c))"/>
        <xhtml:img alt="Help" title="" src="/ops/images/xforms/help.gif" class="{string-join($image-class, ' ')}"/>
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>

    <!-- - - - - - - Generic copy rules - - - - - - -->

    <!-- Copy attributes in XHTML namespace to no namespace -->
    <xsl:template match="@xhtml:*">
        <xsl:attribute name="{local-name()}">
            <xsl:value-of select="."/>
            <xsl:message>Got XHTML attribute: <xsl:value-of select="concat(local-name(), '=', .)"/></xsl:message>
        </xsl:attribute>
    </xsl:template>

    <!-- Simply copy everything that's not matched -->
    <xsl:template match="@*|node()" priority="-2">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
