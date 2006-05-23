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
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:f="http://orbeon.org/oxf/xml/formatting"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xhtml="http://www.w3.org/1999/xhtml">

    <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>

    <xsl:template match="/">
        <xsl:message><xsl:copy-of select="doc('input:instance')"/></xsl:message>
        <xsl:apply-templates select="doc('input:instance')/*"/>
    </xsl:template>

    <xsl:template match="countries">
        <xsl:message><xsl:copy-of select="/*"/></xsl:message>
        <xsl:copy>
            <xsl:for-each select="country">
                <xsl:sort select="*[name() = /instance/sort-by]" order="{/instance/sort-order}"
                        data-type="{/instance/sort-data-type}"/>
                <xsl:copy-of select="."/>
            </xsl:for-each>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
