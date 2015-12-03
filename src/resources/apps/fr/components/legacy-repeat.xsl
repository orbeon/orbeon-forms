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
    xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
    xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
    xmlns:xh="http://www.w3.org/1999/xhtml"
    xmlns:xbl="http://www.w3.org/ns/xbl">

    <!-- Convert legacy fr:repeat to new fr:grid XBL with @repeat attribute -->
    <xsl:template match="xh:body//fr:repeat | xxf:dialog//fr:repeat | xbl:binding/xbl:template//fr:repeat">
        <fr:grid repeat="true">
            <xsl:copy-of select="@* except (@appearance, @columns, @repeat)"/>
            <xsl:copy-of select="node()"/>
        </fr:grid>
    </xsl:template>

</xsl:stylesheet>
