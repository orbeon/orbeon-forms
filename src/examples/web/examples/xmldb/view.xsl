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
<html xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns="http://www.w3.org/1999/xhtml"
    xmlns:f="http://orbeon.org/oxf/xml/formatting"
    xsl:version="2.0">
    <head>
        <title>XML:DB Sandbox</title>
    </head>
    <body>
        <table class="gridtable">
            <tr>
                <th>
                    Input Document
                </th>
                <td style="padding: 1em">
                    <div style="margin: 1em">
                        <f:xml-source>
                            <xsl:copy-of select="/documents/input/*"/>
                        </f:xml-source>
                    </div>
                </td>
            </tr>
            <tr>
                <th>
                    XPath or XQuery
                </th>
                <td style="padding: 1em">
                    <xforms:group ref="/form">
                        <p>
                            <xforms:textarea ref="query" xhtml:style="width: 100%" xhtml:rows="10"/>
                        </p>
                        <p>
                            <xforms:submit xxforms:appearance="button">
                                <xforms:label>Update</xforms:label>
                            </xforms:submit>
                        </p>
                    </xforms:group>
                </td>
            </tr>
            <tr>
                <th>
                    Output Document
                </th>
                <td style="padding: 1em">
                    <div style="margin: 1em">
                        <f:xml-source>
                            <xsl:copy-of select="/documents/output/*"/>
                        </f:xml-source>
                    </div>
                </td>
            </tr>
        </table>
    </body>
</html>
