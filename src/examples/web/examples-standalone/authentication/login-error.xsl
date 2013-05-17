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
        <xhtml:h1>Login Error</xhtml:h1>
        <xhtml:p>
            <xhtml:font color="red">Invalid login.
                <xhtml:a href="/examples-standalone/authentication">Please try again</xhtml:a>.
            </xhtml:font>
        </xhtml:p>
    </xhtml:body>
</xhtml:html>
