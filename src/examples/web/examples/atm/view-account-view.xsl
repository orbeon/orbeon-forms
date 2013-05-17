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
<xhtml:html xsl:version="2.0"
            xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
            xmlns:f="http://orbeon.org/oxf/xml/formatting"
            xmlns:xhtml="http://www.w3.org/1999/xhtml"
            xmlns:xforms="http://www.w3.org/2002/xforms">
    <xhtml:head>
        <xhtml:title>View Account</xhtml:title>
    </xhtml:head>
    <xhtml:body>
        <xforms:group>
            <xhtml:p>
                <xsl:text>The current balance is: </xsl:text>
                <xsl:value-of select="/balance"/>
            </xhtml:p>
            <xhtml:p>
                <xforms:input ref="/amount"/>
                <xforms:submit>
                    <xforms:label>Withdraw</xforms:label>
                </xforms:submit>
            </xhtml:p>
            <xhtml:p style="margin-top: 3em">
                <xhtml:a href="/atm">Back</xhtml:a> to ATM home<br/>
            </xhtml:p>
        </xforms:group>
    </xhtml:body>
</xhtml:html>
