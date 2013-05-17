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
    <xsl:template match="/">
        <book>
            <xsl:for-each select="book/menu">
                <xsl:if test="position() &lt; count(../menu)">
                    <section>
                        <title><xsl:value-of select="@label"/></title>
                        <xsl:call-template name="handle-file"/>
                        <xsl:for-each select="menu-item">
                            <section>
                                <xsl:call-template name="handle-file"/>
                            </section>
                        </xsl:for-each>
                    </section>
                </xsl:if>
            </xsl:for-each>
        </book>
    </xsl:template>

    <xsl:template name="handle-file">
        <xsl:if test="@href">
            <xsl:variable name="file" select="concat('oxf:/doc/pages/', @href, '.xml')"/>
            <xsl:variable name="content" select="document($file)"/>
            <title><xsl:value-of select="$content/document/header/title"/></title>
            <xsl:copy-of select="$content/document/body/*"/>
        </xsl:if>
    </xsl:template>
</xsl:stylesheet>
