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
<xhtml:html xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xsl:version="2.0">

    <xhtml:head>
        <xhtml:title>Authentication</xhtml:title>
    </xhtml:head>
    <xhtml:body>
        <f:example-header>
            <xsl:copy-of select="document('example-descriptor.xml')"/>
        </f:example-header>
        <xsl:choose>
            <!-- User has logged-in -->
            <xsl:when test="/request-security/remote-user">
                <xhtml:p>
                    You are now logged in as <b><xsl:value-of
                    select="/request-security/remote-user"/></b> <xsl:text> over a </xsl:text>
                    <b><xsl:if test="/request-security/secure = 'false'">non-</xsl:if>secure</b>
                    <xsl:text> connection.</xsl:text>
                </xhtml:p>
                <xhtml:p>
                    The "logout" button below will log you out and bring you back to the OPS
                    examples home page. If from there you come back to this authentication example,
                    you will have to login again.
                </xhtml:p>
                <xhtml:p>
                    <xforms:group>
                        <xforms:submit>
                            <xforms:label>Logout</xforms:label>
                            <xforms:setvalue ref="/action">logout</xforms:setvalue>
                        </xforms:submit>
                    </xforms:group>
                </xhtml:p>
            </xsl:when>
            <!-- User is not logged-in -->
            <xsl:otherwise>
                <xhtml:p>
                    You are not logged in. This means that you have not yet configured the
                    authentication in your application server. Please read the <xhtml:a
                    href="/doc/intro-install">documentation</xhtml:a> for more information.
                </xhtml:p>
            </xsl:otherwise>
        </xsl:choose>
        <xhtml:p>
            <xhtml:a href="/">Back</xhtml:a> to examples page
        </xhtml:p>
    </xhtml:body>
</xhtml:html>
