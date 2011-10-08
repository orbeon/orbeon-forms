<!--
  Copyright (C) 2011 Orbeon, Inc.

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
                xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
                xmlns:saxon="http://saxon.sf.net/"
                xmlns:xpl="java:org.orbeon.oxf.pipeline.api.FunctionLibrary">

    <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
    <xsl:template match="xxforms:static-state|xxforms:dynamic-state">
        <xsl:copy>
            <xsl:apply-templates select="xpl:decodeXML(normalize-space(.))"/>
        </xsl:copy>
    </xsl:template>
    <xsl:template match="instances/instance">
        <xsl:copy>
            <xsl:copy-of select="@*"/>
            <xsl:if test="normalize-space(.) != ''">
                <xsl:copy-of select="saxon:parse(string(.))"/>
            </xsl:if>
        </xsl:copy>
    </xsl:template>
</xsl:stylesheet>
