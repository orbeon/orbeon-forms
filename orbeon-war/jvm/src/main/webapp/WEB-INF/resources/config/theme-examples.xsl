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
<xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:f="http://orbeon.org/oxf/xml/formatting"
                xmlns:xh="http://www.w3.org/1999/xhtml"
                xmlns:xf="http://www.w3.org/2002/xforms"
                xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
                xmlns:version="java:org.orbeon.oxf.common.Version"
                xmlns:xi="http://www.w3.org/2001/XInclude"
                xmlns:xxi="http://orbeon.org/oxf/xml/xinclude"
                xmlns:p="http://www.orbeon.com/oxf/pipeline">

    <xsl:import href="theme-plain.xsl"/>

    <!-- Try to obtain a meaningful title for the example -->
    <xsl:variable name="title" select="if (/xh:html/xh:head/xh:title != '')
                                       then /xh:html/xh:head/xh:title
                                       else if (/xh:html/xh:body/xh:h1)
                                            then (/xh:html/xh:body/xh:h1)[1]
                                            else '[Untitled]'" as="xs:string"/>

    <xsl:template match="xh:head">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
            <xh:link rel="stylesheet" href="/config/theme/examples.css" type="text/css" media="all"/>
            <xh:link rel="shortcut icon" href="/ops/images/orbeon-icon-16.ico"/>
            <xh:link rel="icon" href="/ops/images/orbeon-icon-16.png" type="image/png"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="xh:body">
        <xsl:copy>
            <xsl:apply-templates select="@* except @class"/>
            <xsl:attribute name="class" select="string-join(('orbeon', @class), ' ')"/>
            <xh:div class="container">
                <xh:div class="navbar navbar-inverse">
                    <xh:div class="navbar-inner">
                        <xh:div class="container">
                            <xh:a href="http://www.orbeon.com/">
                                <xh:img src="/apps/fr/style/orbeon-navbar-logo.png" alt="Orbeon Forms"/>
                            </xh:a>
                            <xh:h1><xsl:value-of select="$title"/></xh:h1>
                        </xh:div>
                    </xh:div>
                </xh:div>
                <xsl:apply-templates select="node()"/>
            </xh:div>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
