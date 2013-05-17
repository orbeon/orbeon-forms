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
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:import href="document2html.xsl"/>

    <xsl:template match="link">
        <a>
            <xsl:attribute name="href">
                <xsl:choose>
                    <xsl:when test="starts-with(@href, 'http://')">
                        <xsl:value-of select="@href"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="concat(@href, '.html')"/>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:attribute>
            <xsl:apply-templates/>
        </a>
    </xsl:template>

</xsl:stylesheet>