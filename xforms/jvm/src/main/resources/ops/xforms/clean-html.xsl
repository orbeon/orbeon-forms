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
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:template match="/">
        <_>
            <xsl:apply-templates select="node()"/>
        </_>
    </xsl:template>

    <!-- Add safe elements to this list -->
    <xsl:template match="*:a | *:b | *:i | *:ul | *:li | *:ol | *:p | *:span | *:u | *:div | *:br | *:strong | *:em| *:pre
                            | *:img | *:h1 | *:h2 | *:h3 | *:h4 | *:h5 | *:h6 | *:font
                            | *:table | *:tbody | *:tr | *:td | *:th | *:blockquote | *:sub | *:sup" priority="2">
        <xsl:element name="{local-name()}">
            <xsl:apply-templates select="@*|node()"/>
        </xsl:element>
    </xsl:template>

    <!-- Remove unsafe scripts -->
    <xsl:template match="*:script" priority="2"/>

    <!-- Remove everything that looks like a JavaScript event handler or attribute and everything in a namespace -->
    <xsl:template match="@*[not(starts-with(local-name(), 'on')) and not(starts-with(., 'javascript:')) and namespace-uri() = '']" priority="2">
        <xsl:copy-of select="."/>
    </xsl:template>

    <!-- Remove all the other attributes -->
    <xsl:template match="@*" priority="1"/>

    <!-- Copy everything else -->
    <xsl:template match="*" priority="1">
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="text()"><xsl:value-of select="."/></xsl:template>

 </xsl:stylesheet>
