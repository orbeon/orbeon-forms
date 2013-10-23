<!--
  Copyright (C) 2012 Orbeon, Inc.

  This program is free software; you can redistribute it and/or modify it under the terms of the
  GNU Lesser General Public License as published by the Free Software Foundation; either version
  2.1 of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Lesser General Public License for more details.

  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  -->
<xsl:stylesheet xmlns:xh="http://www.w3.org/1999/xhtml"
      xmlns:xf="http://www.w3.org/2002/xforms"
      xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
      xmlns:ev="http://www.w3.org/2001/xml-events"
      xmlns:xs="http://www.w3.org/2001/XMLSchema"
      xmlns:xpl="java:org.orbeon.oxf.pipeline.api.FunctionLibrary"
      xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
      version="2.0">

    <xsl:variable name="is-portlet" select="xpl:isPortlet()"/>

    <xsl:template match="/examples">
        <xh:html>
            <xh:head>
                <xh:title><xsl:value-of select="@title"/></xh:title>
                <xh:link rel="stylesheet" href="/home/home.css" type="text/css"/>
                <xh:script type="text/javascript" src="/ops/jquery/jquery-1.11.0.min.js"/>
                <xh:script type="text/javascript" src="/ops/jquery/jquery-migrate-1.2.1.min.js"/>
            </xh:head>
            <xh:body>
                <xh:ul class="thumbnails">
                    <xsl:apply-templates select="*"/>
                </xh:ul>
                <xh:div class="license">
                    Images on this pages are licensed by their respective author under Creative Commons, and are,
                    in order of appearance, from the following sources:
                    <xsl:for-each select="example">
                        <xsl:value-of select="if (position() > 1) then ', ' else ''"/>
                        <xh:a href="{@source}"><xsl:value-of select="position()"/></xh:a>
                    </xsl:for-each>.
                </xh:div>
            </xh:body>
        </xh:html>
    </xsl:template>

    <xsl:template match="example[not($is-portlet and @portlet-exclude = 'true')]">
        <xsl:variable name="is-first" select="position() = 1"/>
        <xh:li class="thumbnail span{@size}">
            <xh:a href="{@href}">
                <!-- Image ratio must be 620x230 (larger) or 300x230 (smaller) -->
                <xh:img src="{@img}" alt=""/>
                <xh:h2><xsl:value-of select="@title"/></xh:h2>
            </xh:a>
            <xh:p><xsl:copy-of select="node()"/></xh:p>
        </xh:li>
    </xsl:template>

    <xsl:template match="banner">
        <xh:li class="thumbnail span12">
            <xh:p>
                <xsl:copy-of select="node()"/>
            </xh:p>
        </xh:li>
    </xsl:template>

    <!-- Filter everything else -->
    <xsl:template match="node() | @*"/>

</xsl:stylesheet>

