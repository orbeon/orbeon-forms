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
  This stylesheet rewrites HTML or XHTML for servlets and portlets. URLs are parsed, so it must be
  made certain that the URLs are well-formed. Absolute URLs are not modified. Relative or absolute
  paths are supported, as well as the special case of a URL starting with a query string (e.g.
  "?name=value"). This last syntax is supported by most Web browsers.

  A. For portlets, it does the following:

  1. Rewrite form/@action to WSRP action URL encoding
  2. Rewrite a/@href and link/@href to WSRP render encoding
  3. Rewrite img/@src, input[@type='image']/@src and script/@src to WSRP resource URL encoding
  4. If no form/@method is supplied, force an HTTP POST
  5. Escape any wsrp_rewrite occurence in text not within a script or
     SCRIPT element to wsrp_rewritewsrp_rewrite. WSRP 1.0 does not appear to
     specify a particular escape sequence, but we use this one in PresentationServer Portal. The
     escaped sequence is recognized by the PresentationServer Portlet and restored to the original
     sequence, so it is possible to include the string wsrp_rewrite within documents.
  6. Occurrences of wsrp_rewrite found within script or SCRIPT elements, as
     well as occurrences within attributes, are left untouched. This allows them
     to be recognized by the PresentationServer Portlet and rewritten.

  Knonw issues for portlets:

  o The input document should not contain;
    o elements and attribute containing wsrp_rewrite
    o namespace URIs containing wsrp_rewrite
    o processing instructions containing wsrp_rewrite

  B. For servlets, it resrites the URLs to be absolute paths, and prepends the
     context path.

  -->
<xsl:stylesheet version="2.0"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:xhtml="http://www.w3.org/1999/xhtml"
        xmlns:f="http://orbeon.org/oxf/xml/formatting"
        xmlns:portlet="http://www.orbeon.org/oxf/portlet"
        xmlns:context="java:org.orbeon.oxf.pipeline.StaticExternalContext">

    <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>

    <xsl:variable name="container-type" select="document('oxf:container-type')/*" as="element()"/>
    <xsl:variable name="page" select="/*" as="element()"/>

    <xsl:template match="/">
        <xsl:apply-templates select="$page"/>
    </xsl:template>

    <!-- Form -->
    <xsl:template match="form | xhtml:form">
        <xsl:copy>
            <xsl:copy-of select="@*[namespace-uri() = '']"/>
            <xsl:choose>
                <xsl:when test="@action">
                    <xsl:attribute name="action">
                        <xsl:value-of select="context:rewriteActionURL(@action)"/>
                    </xsl:attribute>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:attribute name="action">
                        <xsl:value-of select="context:rewriteActionURL('')"/>
                    </xsl:attribute>
                </xsl:otherwise>
            </xsl:choose>
            <!-- Default is POST instead of GET for portlets -->
            <xsl:if test="not(@method) and $container-type/* = 'portlet'">
                <xsl:attribute name="method">post</xsl:attribute>
            </xsl:if>
            <xsl:choose>
                <xsl:when test="@portlet:is-portlet-form = 'true'">
                    <xsl:apply-templates mode="norewrite"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:apply-templates/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:copy>
    </xsl:template>
    <!-- Anchor with URL rewriting -->
    <xsl:template match="a[@href] | xhtml:a[@href]" priority="90">
        <xsl:copy>
            <xsl:copy-of select="@*[namespace-uri() = '']"/>
            <xsl:attribute name="href">
                <xsl:choose>
                    <xsl:when test="not(@f:url-type) or @f:url-type = 'render'">
                        <xsl:value-of select="context:rewriteRenderURL(@href)"/>
                    </xsl:when>
                    <xsl:when test="@f:url-type = 'action'">
                        <xsl:value-of select="context:rewriteActionURL(@href)"/>
                    </xsl:when>
                    <xsl:when test="@f:url-type = 'resource'">
                        <xsl:value-of select="context:rewriteResourceURL(@href)"/>
                    </xsl:when>
                </xsl:choose>
            </xsl:attribute>
            <xsl:apply-templates/>
        </xsl:copy>
    </xsl:template>
    <!-- Image map area with action URL -->
    <xsl:template match="area[@href] | xhtml:area[@href]">
        <xsl:copy>
            <xsl:copy-of select="@*[namespace-uri() = '']"/>
            <xsl:attribute name="href">
                <xsl:value-of select="context:rewriteActionURL(@href)"/>
            </xsl:attribute>
            <xsl:apply-templates/>
        </xsl:copy>
    </xsl:template>
    <!-- Resource URL with href -->
    <xsl:template match="link[@href] | xhtml:link[@href]">
        <xsl:copy>
            <xsl:copy-of select="@*[namespace-uri() = '']"/>
            <xsl:attribute name="href">
                <xsl:value-of select="context:rewriteResourceURL(@href)"/>
            </xsl:attribute>
            <xsl:apply-templates/>
        </xsl:copy>
    </xsl:template>
    <!-- Resource URL with src -->
    <xsl:template match="img[@src] | input[@type = 'image' and @src] | script[@src]
                       | xhtml:img[@src] | xhtml:input[@type = 'image' and @src] | xhtml:script[@src]">
        <xsl:copy>
            <xsl:copy-of select="@*[namespace-uri() = '']"/>
            <xsl:attribute name="src">
                <xsl:value-of select="context:rewriteResourceURL(@src)"/>
            </xsl:attribute>
            <xsl:apply-templates/>
        </xsl:copy>
    </xsl:template>
    <!-- Resource URL with background -->
    <xsl:template match="td[@background] | body[@background] | xhtml:td[@background] | xhtml:body[@background]">
        <xsl:copy>
            <xsl:copy-of select="@*[namespace-uri() = '']"/>
            <xsl:attribute name="background">
                <xsl:value-of select="context:rewriteResourceURL(@background)"/>
            </xsl:attribute>
            <xsl:apply-templates/>
        </xsl:copy>
    </xsl:template>
    <!-- Text -->
    <xsl:template match="text()[not(ancestor::script or ancestor::SCRIPT or ancestor::xhtml:script or ancestor::xhtml:SCRIPT)]">
        <xsl:value-of select="replace(current(), 'wsrp_rewrite', 'wsrp_rewritewsrp_rewrite')"/>
        <xsl:apply-templates/>
    </xsl:template>
    <xsl:template match="@*|node()" priority="-1">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>
    
    <!-- Explicit rewriting tags -->
    <xsl:template match="f:rewrite">
        <xsl:choose>
            <xsl:when test="@type = 'action'">
                <xsl:value-of select="context:rewriteActionURL(@url)"/>
            </xsl:when>
            <xsl:when test="@type = 'render'">
                <xsl:value-of select="context:rewriteRenderURL(@url)"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="context:rewriteResourceURL(@url)"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!-- Any element with f:url-norewrite = 'true' attribute does not get rewritten -->
    <xsl:template match="*[@f:url-norewrite = 'true']" priority="200">
        <xsl:copy>
            <xsl:copy-of select="@*[namespace-uri() = '']"/>
<!--                        <xsl:apply-templates/>-->
            <xsl:apply-templates mode="norewrite"/>
        </xsl:copy>
    </xsl:template>

    <!-- Simply copy everything without ever rewriting -->
    <xsl:template match="@*|node()" mode="norewrite">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()" mode="#current"/>
        </xsl:copy>
    </xsl:template>

    <!-- Condition to revert to rewrite mode -->
    <xsl:template match="*[@f:url-norewrite = 'false']" mode="norewrite">
        <xsl:apply-templates select="."/>
    </xsl:template>

</xsl:stylesheet>
