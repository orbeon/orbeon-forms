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
<xsl:stylesheet version="1.0"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:i18n="http://www.example.com/i18n">

    <!-- Get locale from session, default to english -->
    <xsl:variable name="locale">
        <xsl:choose>
            <xsl:when test="/root/locale != ''">
                <xsl:value-of select="/root/locale"/>
            </xsl:when>
            <xsl:otherwise>en_US</xsl:otherwise>
        </xsl:choose>
    </xsl:variable>
    <xsl:variable name="keys" select="/root/i18n[@locale = $locale]"/>

    <xsl:template match="/">
        <xsl:apply-templates select="/root/page/*"/>
    </xsl:template>

    <xsl:template name="find-value">
        <xsl:param name="key"/>

        <xsl:variable name="value" select="$keys/text[@key = $key]"/>
        <xsl:choose>
            <xsl:when test="$value">
                <xsl:copy-of select="$value/node()"/>
            </xsl:when>
            <xsl:otherwise>
                [Unknown key '<xsl:value-of select="$key"/>']
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="*[namespace-uri() = 'http://www.example.com/i18n']">
        <xsl:choose>
            <xsl:when test="namespace-uri() = 'http://www.example.com/i18n'">
                <xsl:call-template name="find-value">
                    <xsl:with-param name="key" select="@key"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:otherwise>
                <xsl:element name="{name()}" namespace="namespace-uri()">
                    <xsl:copy-of select="@*"/>
                    <xsl:apply-templates/>
                </xsl:element>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="@*[namespace-uri() = 'http://www.example.com/i18n']">
        <xsl:attribute name="{local-name(.)}">
            <xsl:call-template name="find-value">
                <xsl:with-param name="key" select="string(.)"/>
            </xsl:call-template>
        </xsl:attribute>
    </xsl:template>

    <xsl:template match="@*|node()" priority="-2">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>