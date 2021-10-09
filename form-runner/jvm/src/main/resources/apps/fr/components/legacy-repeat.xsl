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
<xsl:stylesheet
    version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fr="http://orbeon.org/oxf/xml/form-runner">

    <!-- Convert legacy `fr:repeat` to `fr:grid` XBL with `@repeat` attribute -->
    <!-- 2021-10-08: This itself is now the "old" format, as the new format uses `repeat="content"`. -->
    <xsl:template match="fr:repeat" mode="within-controls within-dialogs">
        <fr:grid repeat="true">
            <xsl:apply-templates select="@* except (@appearance, @columns, @repeat)" mode="#current"/>
            <xsl:apply-templates select="node()" mode="#current"/>
        </fr:grid>
    </xsl:template>

</xsl:stylesheet>
