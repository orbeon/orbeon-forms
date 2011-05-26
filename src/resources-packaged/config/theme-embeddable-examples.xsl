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
    Simple portlet theme. Inserts orbeon.css and a link back to the portlet home.
-->
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:f="http://orbeon.org/oxf/xml/formatting"
    xmlns:context="java:org.orbeon.oxf.pipeline.StaticExternalContext">

    <xsl:template match="/">
        <xhtml:div class="orbeon-portlet-div">
            <!-- Styles and scripts -->
            <xhtml:link rel="stylesheet" href="/config/theme/orbeon.css" type="text/css"/>
            <!-- Handle head elements except scripts -->
            <xsl:for-each select="/xhtml:html/xhtml:head/(xhtml:meta | xhtml:link | xhtml:style)">
                <xsl:element name="xhtml:{local-name()}" namespace="{namespace-uri()}">
                    <xsl:copy-of select="@*"/>
                    <xsl:apply-templates/>
                </xsl:element>
            </xsl:for-each>
            <!-- Try to get a title and set it on the portlet -->
            <xsl:if test="normalize-space(/xhtml:html/xhtml:head/xhtml:title)">
                <xsl:value-of select="context:setTitle(normalize-space(/xhtml:html/xhtml:head/xhtml:title))"/>
            </xsl:if>
            <!-- Handle head scripts if present -->
            <xsl:apply-templates select="/xhtml:html/xhtml:head/xhtml:script"/>
            <!-- Body -->
            <xhtml:div id="orbeon" class="orbeon-portlet-body orbeon">
                <xhtml:div class="maincontent">
                    <xsl:apply-templates select="/xhtml:html/xhtml:body/node()"/>
                </xhtml:div>
            </xhtml:div>
            <!-- This is for demo purposes only -->
            <xhtml:div class="orbeon-portlet-home">
                <xhtml:a xmlns:f="http://orbeon.org/oxf/xml/formatting" href="/" f:portlet-mode="view">Home</xhtml:a>
            </xhtml:div>
            <!-- Handle post-body scripts if present. They can be placed here by oxf:resources-aggregator -->
            <xsl:apply-templates select="/xhtml:html/xhtml:script"/>
        </xhtml:div>
    </xsl:template>

    <!-- Remember that we are embeddable -->
    <xsl:template match="xhtml:form">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <xhtml:input type="hidden" name="orbeon-embeddable" value="true"/>
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
