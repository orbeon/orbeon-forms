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
            xmlns:xforms="http://www.w3.org/2002/xforms"
            xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
            xmlns:f="http://orbeon.org/oxf/xml/formatting"
            xmlns:xhtml="http://www.w3.org/1999/xhtml"
            xsl:version="2.0">
    <xhtml:head><xhtml:title>XPL Sandbox</xhtml:title></xhtml:head>
    <xhtml:body>
        <xforms:group ref="/form">
            <table class="gridtable">
                <tr>
                    <th align="right">Input Document</th>
                    <td style="padding: 1em">
                        <f:xml-source>
                            <xsl:copy-of select="doc('input.xml')"/>
                        </f:xml-source>
                    </td>
                </tr>
                <tr>
                    <th align="right">XPL</th>
                    <td style="padding: 1em">
                        <xforms:textarea ref="xpl" xhtml:style="width: 100%" xhtml:rows="23"/>
                        <p>
                            <xforms:submit>
                                <xforms:label>Submit</xforms:label>
                            </xforms:submit>
                        </p>
                    </td>
                </tr>
                <tr>
                    <th align="right">Result</th>
                    <td style="padding: 1em">
                        <f:xml-source>
                            <xsl:copy-of select="/*"/>
                        </f:xml-source>
                    </td>
                </tr>
            </table>
        </xforms:group>
    </xhtml:body>
</xhtml:html>
