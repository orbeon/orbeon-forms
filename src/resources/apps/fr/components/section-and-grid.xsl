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
<xsl:stylesheet
    version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
    xmlns:xh="http://www.w3.org/1999/xhtml"
    xmlns:xbl="http://www.w3.org/ns/xbl"
    xmlns:frf="java:org.orbeon.oxf.fr.FormRunner">

    <!-- NOTE: This won't be needed once XBL components properties can be inherited at the form level -->
    <xsl:template match="xh:body//fr:section | xbl:binding/xbl:template//fr:section">
        <xsl:copy>
            <xsl:if test="empty(@collapse)">
                <xsl:attribute name="collapse" select="$is-section-collapse"/>
            </xsl:if>
            <xsl:if test="empty(@animate) ">
                <xsl:attribute name="animate"  select="$is-animate-sections"/>
            </xsl:if>
            <!-- Set repeat appearance if available and needed -->
            <xsl:if
                test="
                    frf:isRepeat(.)                    and
                    empty(@appearance)                 and
                    exists($section-repeat-appearance) and
                    $section-repeat-appearance != 'full'">
                <xsl:attribute name="appearance" select="$section-repeat-appearance"/>
            </xsl:if>
            <xsl:apply-templates select="@*"/>
            <xsl:apply-templates select="node()"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="xh:body//fr:grid[frf:isRepeat(.)] | xbl:binding/xbl:template//fr:grid[frf:isRepeat(.)]">
        <xsl:copy>
            <!-- Set repeat appearance if available and needed -->
            <xsl:if
                test="
                    empty(@appearance)              and
                    exists($grid-repeat-appearance) and
                    $grid-repeat-appearance != 'full'">
                <xsl:attribute name="appearance" select="$grid-repeat-appearance"/>
            </xsl:if>
            <xsl:apply-templates select="@*"/>
            <xsl:apply-templates select="node()"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
