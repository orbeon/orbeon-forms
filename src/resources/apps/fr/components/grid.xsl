<?xml version="1.0" encoding="UTF-8"?>
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
<xsl:stylesheet version="2.0"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xforms="http://www.w3.org/2002/xforms"
        xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
        xmlns:exforms="http://www.exforms.org/exf/1-0"
        xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
        xmlns:xhtml="http://www.w3.org/1999/xhtml"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xxi="http://orbeon.org/oxf/xml/xinclude"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:xbl="http://www.w3.org/ns/xbl"
        xmlns:pipeline="java:org.orbeon.oxf.processor.pipeline.PipelineFunctionLibrary">

    <!-- Grid -->
    <xsl:template match="xhtml:body//fr:grid | xbl:binding/xbl:template//fr:grid">
        <xsl:choose>
            <xsl:when test="$is-noscript and $is-noscript-table">
                <xhtml:div class="fr-grid{ if (@class) then concat(' ', @class) else ()}">
                    <!-- Grid content -->
                    <xsl:apply-templates select="xhtml:* except xforms:label" mode="grid-content"/>
                    <xsl:apply-templates select="(fr:* | xforms:* | xxforms:*) except xforms:label"/>
                </xhtml:div>
            </xsl:when>
            <xsl:otherwise>
                <xhtml:table class="fr-grid{ if (@class) then concat(' ', @class) else ()}">
                    <!-- Grid content -->
                    <xsl:apply-templates select="xhtml:* except xforms:label" mode="grid-content"/>
                    <xsl:apply-templates select="(fr:* | xforms:* | xxforms:*) except xforms:label"/>
                </xhtml:table>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!-- Grid row -->
    <xsl:template match="xhtml:body//fr:grid//fr:tr | xbl:binding/xbl:template//fr:grid//fr:tr">
        <xsl:choose>
            <xsl:when test="$is-noscript and $is-noscript-table">
                <xsl:apply-templates select="@* | node()"/>
            </xsl:when>
            <xsl:otherwise>
                <xhtml:tr>
                    <xsl:apply-templates select="@* | node()"/>
                </xhtml:tr>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!-- Grid cell -->
    <xsl:template match="xhtml:body//fr:tr/fr:td | xbl:binding/xbl:template//fr:tr/fr:td">
        <xsl:choose>
            <xsl:when test="$is-noscript and $is-noscript-table">
                <xhtml:div class="{string-join(('fr-grid-td', @class), ' ')}">
                    <xsl:apply-templates select="@*"/>
                    <xhtml:div class="fr-grid-content">
                    <xsl:apply-templates select="node()"/>
                </xhtml:div>
                </xhtml:div>
            </xsl:when>
            <xsl:otherwise>
                <xhtml:td class="{string-join(('fr-grid-td', @class), ' ')}">
                    <xsl:apply-templates select="@*"/>
                    <!-- For now don't put div if content is empty. This facilitates styling with IE 6. -->
                    <xsl:if test="exists(*) or normalize-space() != ''">
                        <xhtml:div class="fr-grid-content">
                        <xsl:apply-templates select="node()"/>
                        </xhtml:div>
                    </xsl:if>
                </xhtml:td>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!-- Grid cell (legacy) -->
    <xsl:template match="xhtml:td" mode="grid-content">
        <xsl:choose>
            <xsl:when test="$is-noscript and $is-noscript-table">
                <xhtml:div>
                    <xsl:copy-of select="@*"/>
                    <xsl:attribute name="class" select="string-join(('fr-grid-td', @class), ' ')"/>
                    <xhtml:div class="fr-grid-content">
                    <xsl:apply-templates/>
                </xhtml:div>
                </xhtml:div>
            </xsl:when>
            <xsl:otherwise>
                <xsl:copy>
                    <xsl:copy-of select="@*"/>
                    <xsl:attribute name="class" select="string-join(('fr-grid-td', @class), ' ')"/>
                    <!-- For now don't put div if content is empty. This facilitates styling with IE 6. -->
                    <xsl:if test="exists(*) or normalize-space() != ''">
                        <xhtml:div class="fr-grid-content">
                        <xsl:apply-templates/>
                        </xhtml:div>
                    </xsl:if>
                </xsl:copy>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

</xsl:stylesheet>
