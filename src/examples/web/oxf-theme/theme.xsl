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

    <xsl:include href="formatting.xsl"/>
    <xsl:include href="oxf:/inspector/xml-formatting.xsl"/>

    <xsl:variable name="request" select="doc('input:request')" as="document-node()"/>

    <xsl:template match="/">
        <xhtml:html>
            <xhtml:head>
                <!-- Handle meta elements -->
                <xsl:for-each select="/xhtml:html/xhtml:head/xhtml:meta">
                    <xsl:copy-of select="."/>
                </xsl:for-each>
                <!-- Copy user-defined links -->
                <xsl:for-each select="/xhtml:html/xhtml:head/xhtml:links">
                    <xsl:copy-of select="."/>
                </xsl:for-each>
                <!-- Standard links -->
                <xhtml:link rel="stylesheet" href="/oxf-theme/orbeon-layout.cssd" type="text/css"/>
                <!-- Copy user-defined stylesheets -->
                <xsl:for-each select="/xhtml:html/xhtml:head/xhtml:style">
                    <xsl:copy-of select="."/>
                </xsl:for-each>
                <!-- Copy user-defined scripts -->
                <xsl:for-each select="/xhtml:html/xhtml:head/xhtml:script">
                    <xsl:copy-of select="."/>
                </xsl:for-each>
                <!-- Standard scripts -->
                <xhtml:script type="text/javascript" src="/oxf-theme/javascript/wz_tooltip.js"/>
                <xhtml:script type="text/javascript" src="/oxf-theme/javascript/calendar.js"/>
                <xhtml:script type="text/javascript" src="/oxf-theme/javascript/overlib_mini.js"/>
                <xhtml:script type="text/javascript" src="/oxf-theme/javascript/time-utils.js"/>
                <xhtml:script type="text/javascript" src="/oxf-theme/javascript/sarissa.js"/>
                <xhtml:script type="text/javascript" src="/oxf-theme/javascript/xforms-style.js"/>
                <xhtml:script type="text/javascript" src="/oxf-theme/javascript/xforms.js"/>
                <!-- Title -->
                <xhtml:title>
                    <xsl:choose>
                        <xsl:when test="/xhtml:html/xhtml:head/xhtml:title != ''">
                            <xsl:value-of select="/xhtml:html/xhtml:head/xhtml:title"/>
                        </xsl:when>
                        <xsl:when test="/xhtml:html/xhtml:body/xhtml:example-header">
                            <xsl:variable name="title" select="/xhtml:html/xhtml:body/xhtml:example-header/*/title"/>
                            <xsl:value-of select="$title"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:value-of select="(/xhtml:html/xhtml:body/xhtml:h1)[1]"/>
                        </xsl:otherwise>
                    </xsl:choose>
                </xhtml:title>
            </xhtml:head>
            <xhtml:body>
                <!-- Copy attributes -->
                <xsl:apply-templates select="/xhtml:html/xhtml:body/@*"/>
                <!-- Handle optional tabs -->
                <xsl:apply-templates select="/xhtml:html/xhtml:head/f:tabs"/>
                <xsl:apply-templates select="/xhtml:html/xhtml:body/node()"/>
            </xhtml:body>
        </xhtml:html>
    </xsl:template>

    <xsl:template match="xhtml:form">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>
    
    <xsl:template match="xhtml:textarea">
        <xhtml:textarea wrap="soft">
            <xsl:apply-templates select="@*|node()"/>
        </xhtml:textarea>
    </xsl:template>
    
    <xsl:template match="xhtml:span[tokenize(@class, ' ') = 'xforms-group' and xhtml:label[not(@for)]]">
        <xhtml:fieldset>
            <xsl:apply-templates select="@*"/>
            <xhtml:legend>
                <xsl:apply-templates select="xhtml:label/@*"/>
                <xsl:value-of select="xhtml:label"/>
            </xhtml:legend>
            <xsl:apply-templates select="node() except xhtml:label"/>
        </xhtml:fieldset>
    </xsl:template>
    
    <xsl:template name="ignore-first-empty-lines">
        <xsl:param name="text"/>
        <xsl:variable name="first-line" select="substring-before($text, '&#xA;')"/>
        <xsl:choose>
            <xsl:when test="normalize-space($first-line) = ''">
                <!-- First line empty, skip it -->
                <xsl:call-template name="ignore-first-empty-lines">
                    <xsl:with-param name="text" select="substring-after($text, '&#xA;')"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:otherwise>
                <!-- Start truncating the margin -->
                <xsl:call-template name="truncate-margin">
                    <xsl:with-param name="text" select="$text"/>
                    <xsl:with-param name="width">
                        <xsl:call-template name="leading-spaces-count">
                            <xsl:with-param name="text" select="substring-before($text, '&#xA;')"/>
                        </xsl:call-template>
                    </xsl:with-param>
                </xsl:call-template>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="leading-spaces-count">
        <xsl:param name="text"/>
        <xsl:choose>
            <xsl:when test="substring($text, 1, 1) = ' '">
                <xsl:variable name="recurse">
                    <xsl:call-template name="leading-spaces-count">
                        <xsl:with-param name="text" select="substring($text, 2, string-length($text) - 1)"/>
                    </xsl:call-template>
                </xsl:variable>
                <xsl:value-of select="$recurse + 1"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="0"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="truncate-margin">
        <xsl:param name="text"/>
        <xsl:param name="width"/>
        <xsl:variable name="first-line" select="substring-before($text, '&#xA;')"/>
        <xsl:variable name="rest" select="substring-after($text, '&#xA;')"/>
        <xsl:value-of select="substring($first-line, $width + 1)"/>
        <xsl:if test="substring-after($rest, '&#xA;') != ''">
            <xsl:value-of select="'&#xA;'"/>
            <xsl:call-template name="truncate-margin">
                <xsl:with-param name="text" select="$rest"/>
                <xsl:with-param name="width" select="$width"/>
            </xsl:call-template>
        </xsl:if>
    </xsl:template>

    <!-- Copy attributes in XHTML namespace to no namespace -->
    <xsl:template match="@xhtml:*">
        <xsl:attribute name="{local-name()}">
            <xsl:value-of select="."/>
        </xsl:attribute>
    </xsl:template>

    <xsl:template match="@*|node()" priority="-2">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>

    <!-- Should this be here? -->
    <xsl:template match='xhtml:td[ @xxforms:error-cell="true" ]' >
        <xhtml:td>
            <xhtml:img src="/images/error.gif" style="margin: 5px"/>
        </xhtml:td>
    </xsl:template>

</xsl:stylesheet>
