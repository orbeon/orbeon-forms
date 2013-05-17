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
            xsl:version="2.0">
    <xhtml:head>
        <xhtml:title>Login</xhtml:title>
    </xhtml:head>
    <xhtml:body>
        <f:example-header>
            <xsl:copy-of select="document('example-descriptor.xml')"/>
        </f:example-header>
        <xhtml:p>
            Please enter your login and password:
        </xhtml:p>
        <xhtml:form action="/j_security_check">
            <xhtml:table>
                <xhtml:tr>
                    <xhtml:td align="right">Login:</xhtml:td>
                    <xhtml:td><input name="j_username"/></xhtml:td>
                </xhtml:tr>
                <xhtml:tr>
                    <xhtml:td align="right">Password:</xhtml:td>
                    <xhtml:td><xhtml:input type="password" name="j_password"/></xhtml:td>
                </xhtml:tr>
                <xhtml:tr>
                    <xhtml:td/><xhtml:td>
                    <xhtml:input type="submit" value="Login"/></xhtml:td>
                </xhtml:tr>
            </xhtml:table>
        </xhtml:form>
        <xhtml:p>
            <xhtml:a href="/">Back</xhtml:a> to examples page
        </xhtml:p>
    </xhtml:body>
</xhtml:html>
