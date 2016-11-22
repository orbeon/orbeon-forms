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
    Given a set of paths (under /root/paths) and the result of a match (under /root/result), this will return the paths
    replacing the ${1}, etc with the corresponding group in the match result.
-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
    <xsl:template match="/">
        <config>
            <xsl:call-template name="rewrite">
                <xsl:with-param name="groups" select="/root/result/group"/>
                <xsl:with-param name="path" select="string(/root/config/url)"/>
            </xsl:call-template>
        </config>
    </xsl:template>

    <xsl:template name="rewrite">
        <xsl:param name="groups"/>
        <xsl:param name="path"/>
        <xsl:param name="index" select="1"/>

        <xsl:choose>
            <xsl:when test="$index > count($groups)">
                <xsl:value-of select="$path"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:variable name="placeholder" select="concat('${', $index, '}')"/>
                <xsl:variable name="new-path">
                    <xsl:choose>
                        <xsl:when test="contains($path, $placeholder)">
                            <xsl:value-of select="substring-before($path, $placeholder)"/>
                            <xsl:value-of select="string($groups[$index])"/>
                            <xsl:value-of select="substring-after($path, $placeholder)"/>
                        </xsl:when>
                        <xsl:otherwise><xsl:value-of select="$path"/></xsl:otherwise>
                    </xsl:choose>
                </xsl:variable>
                <xsl:call-template name="rewrite">
                    <xsl:with-param name="groups" select="/root/result/group"/>
                    <xsl:with-param name="index" select="$index + 1"/>
                    <xsl:with-param name="path" select="$new-path"/>
                </xsl:call-template>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
</xsl:stylesheet>
