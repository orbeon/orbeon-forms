<!--
  Copyright (C) 2017 Orbeon, Inc.

  This program is free software; you can redistribute it and/or modify it under the terms of the
  GNU Lesser General Public License as published by the Free Software Foundation; either version
  2.1 of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Lesser General Public License for more details.

  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  -->

<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>
    <xsl:import href="annotate-design-time.xsl"/>

    <xsl:template match="/*/bind">
        <xsl:copy>
            <xsl:apply-templates select="*" mode="within-model"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="/*/control">
        <xsl:copy>
            <xsl:apply-templates select="*" mode="within-body"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>