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
<xsl:stylesheet version="1.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:f="http://orbeon.org/oxf/xml/formatting"
    xmlns:java="http://xml.apache.org/xslt/java">

    <!-- Create link to source -->
    <xsl:template match="view-source">
        <nobr><a href="/goto-source/petstore/{java:java.net.URLEncoder.encode(@src)}" f:url-type="resource"><xsl:value-of select="@src"/></a></nobr><br/>
    </xsl:template>

    <!-- Copy other tags -->
    <xsl:template match="@*|node()">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>