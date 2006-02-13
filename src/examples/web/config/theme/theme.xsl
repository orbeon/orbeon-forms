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
    xmlns:portlet="http://orbeon.org/oxf/xml/portlet"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms">

    <xsl:include href="formatting.xsl"/>
<!-- This contains some useful request information -->
    <xsl:variable name="request" select="doc('input:request')" as="document-node()"/>

    <!-- - - - - - - Themed page template - - - - - - -->
    <xsl:template match="/">
        <xhtml:html>
            <xhtml:head>
                <!-- Standard scripts/styles -->
                <!-- NOTE: The XForms engine may place additional scripts and stylesheets here as needed -->
                <xhtml:link rel="stylesheet" href="/config/theme/orbeon.css" type="text/css"/>
                <!-- Handle meta elements -->
                <xsl:copy-of select="/xhtml:html/xhtml:head/xhtml:meta"/>
                <!-- Copy user-defined links -->
                <xsl:copy-of select="/xhtml:html/xhtml:head/xhtml:link"/>
                <!-- Copy user-defined stylesheets -->
                <xsl:copy-of select="/xhtml:html/xhtml:head/xhtml:style"/>
                <!-- Copy user-defined scripts -->
                <xsl:copy-of select="/xhtml:html/xhtml:head/xhtml:script"/>
                <!-- Title -->
                <xhtml:title>
                    <xsl:choose>
                        <xsl:when test="/xhtml:html/xhtml:head/xhtml:title != ''">
                            <xsl:value-of select="/xhtml:html/xhtml:head/xhtml:title"/>
                        </xsl:when>
                        <xsl:when test="/xhtml:html/xhtml:body/xhtml:example-header">
                            <xsl:value-of select="/xhtml:html/xhtml:body/f:example-header/*/title"/>
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
                <!-- Template for alert messages -->
            </xhtml:body>
        </xhtml:html>
    </xsl:template>

    <!-- - - - - - - Form controls - - - - - - -->

    <xsl:template match="xhtml:form[@class = 'xforms-form']">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
        <!-- Summary section for errors -->
        <xhtml:table id="xforms-messages">
            <!-- This section starts hidden if there are not errors the first time the page is displayed -->
            <xsl:if test="count(.//xhtml:label[starts-with(@class, 'xforms-alert-active')]) = 0">
                <xsl:attribute name="style">display: none</xsl:attribute>
            </xsl:if>
            <xhtml:tr>
                <xhtml:td style="padding-left: 1em">
                    <xhtml:img src="/images/error-large.gif" alt="Error"/>
                </xhtml:td>
                <xhtml:td style="padding-right: 1em">
                    <xhtml:p>Please check form for invalid values</xhtml:p>
                    <xsl:for-each select=".//xhtml:label[starts-with(@class, 'xforms-alert-')]">
                        <xhtml:label for="{@for}" class="xforms-message">
                            <xsl:if test="@class = 'xforms-alert-inactive'">
                                <xsl:attribute name="style">display: none</xsl:attribute>
                            </xsl:if>
                            <xsl:value-of select="."/>
                        </xhtml:label>
                    </xsl:for-each>
                </xhtml:td>
            </xhtml:tr>
        </xhtml:table>
    </xsl:template>
    
    <xsl:template match="xhtml:textarea">
        <xhtml:textarea wrap="soft">
            <xsl:apply-templates select="@*|node()"/>
        </xhtml:textarea>
    </xsl:template>
    
    <!-- Generate fieldset for groups that contain a label -->
    <xsl:template match="xhtml:span[tokenize(@class, ' ') = 'xforms-group' 
            and xhtml:label[@class = 'xforms-label' and @for = ../@id]]">
        <xhtml:fieldset>
            <xsl:apply-templates select="@*"/>
            <xhtml:legend>
                <xsl:variable name="label" as="element()" select="xhtml:label[@class = 'xforms-label'][1]"/>
                <xsl:apply-templates select="$label/@*"/>
                <xsl:value-of select="$label"/>
            </xhtml:legend>
            <xsl:apply-templates select="node() except xhtml:label"/>
        </xhtml:fieldset>
    </xsl:template>
    
    <!-- Populate content of loading indicator -->
    <xsl:template match="xhtml:span[@class = 'xforms-loading-loading']">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <xhtml:img src="/images/loading.gif" style="float: left"/>
            Loading...
        </xsl:copy>
    </xsl:template>
    
    <!-- Use vertical scroll in wide-text area (no text-wrapping) -->
    <xsl:template match="xhtml:textarea[tokenize(@class, ' ') = 'wide-textarea']">
        <xsl:copy>
            <xsl:attribute name="wrap">off</xsl:attribute>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>
    
    <!-- Legacy XForms error cell styling -->
    <xsl:template match="xhtml:td[@xxforms:error-cell = 'true']" >
        <xhtml:td>
            <xhtml:img src="/images/error.gif" style="margin: 5px"/>
        </xhtml:td>
    </xsl:template>

    <!-- - - - - - - Generic copy rules - - - - - - -->

    <!-- Copy attributes in XHTML namespace to no namespace -->
    <xsl:template match="@xhtml:*">
        <xsl:attribute name="{local-name()}">
            <xsl:value-of select="."/>
        </xsl:attribute>
    </xsl:template>

    <!-- Simply copy everything that's not matched -->
    <xsl:template match="@*|node()" priority="-2">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>

    <!-- - - - - - - Make sure that portlet content is never rewritten in the theme - - - - - - -->

    <!-- Any element with portlet:is-portlet-content = 'true' attribute doesn't have theme applied -->
    <xsl:template match="xhtml:div[@portlet:is-portlet-content = 'true']" priority="200">
        <xsl:copy>
            <xsl:copy-of select="@*[namespace-uri() = '']"/>
            <xsl:copy-of select="@f:url-norewrite"/>
            <xsl:apply-templates mode="notheme"/>
        </xsl:copy>
    </xsl:template>

    <!-- Simply copy everything without ever applying theme-->
    <xsl:template match="@*|node()" mode="notheme">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()" mode="#current"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
