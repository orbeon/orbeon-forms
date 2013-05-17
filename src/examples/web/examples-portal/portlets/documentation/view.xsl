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
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xhtml="http://www.w3.org/1999/xhtml">

    <xsl:template match="/">
        <xhtml:html>
            <xhtml:head>
                <xhtml:title><xsl:value-of select="/*/title"/> - Example Description</xhtml:title>
            </xhtml:head>
            <xhtml:body>
                <xsl:copy-of select="/*/description/node()"/>
            </xhtml:body>
        </xhtml:html>
    </xsl:template>
</xsl:stylesheet>
