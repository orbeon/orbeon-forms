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
<html xsl:version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
      xmlns:xforms="http://www.w3.org/2002/xforms"
      xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
      xmlns:xi="http://www.w3.org/2001/XInclude"
      xmlns:xhtml="http://www.w3.org/1999/xhtml"
      xmlns:claim="http://orbeon.org/oxf/examples/bizdoc/claim"
      xmlns="http://www.w3.org/1999/xhtml">
    <head>
        <title>Summary</title>
    </head>
    <body>
        <div class="maincontent">
            <xi:include href="view-logo.xml"/>
            <xforms:group ref="/form">
                <h2 style="margin-top: 0">Available Documents</h2>
                <table class="gridtable">
                    <tr>
                        <th>Last Name</th>
                        <th>First Name</th>
                        <th>Document Identifier</th>
                        <th>Edit</th>
                        <th>XML</th>
                        <th>Delete</th>
                    </tr>
                    <xsl:choose>
                        <xsl:when test="count(/result/document-info) > 0">
                            <xsl:for-each select="/result/document-info">
                                <tr>
                                    <td>
                                        <xsl:value-of select="claim:last-name"/>
                                    </td>
                                    <td>
                                        <xsl:value-of select="claim:first-name"/>
                                    </td>
                                    <td>
                                        <xsl:value-of select="document-id"/>
                                    </td>
                                    <td>
                                        <xforms:submit>
                                            <xforms:label>Edit</xforms:label>
                                            <xforms:setvalue ref="action">show-detail</xforms:setvalue>
                                            <xforms:setvalue ref="document-id"><xsl:value-of select="document-id"/></xforms:setvalue>
                                        </xforms:submit>
                                    </td>
                                    <td>
                                        <xforms:submit>
                                            <xforms:label>XML</xforms:label>
                                            <xforms:setvalue ref="action">show-xml</xforms:setvalue>
                                            <xforms:setvalue ref="document-id"><xsl:value-of select="document-id"/></xforms:setvalue>
                                        </xforms:submit>
                                    </td>
                                    <td align="center">
                                        <xforms:submit xxforms:appearance="image">
                                            <xxforms:img src="/images/remove.png"/>
                                            <xforms:label/>
                                            <xforms:setvalue ref="action">delete-documents</xforms:setvalue>
                                            <xforms:setvalue ref="document-id"><xsl:value-of select="document-id"/></xforms:setvalue>
                                        </xforms:submit>
                                    </td>
                                </tr>
                            </xsl:for-each>
                        </xsl:when>
                        <xsl:otherwise>
                            <tr>
                                <td colspan="6"><i>No document found.<br/>Please press the "Import
                                Documents" button to get started.</i></td>
                            </tr>
                        </xsl:otherwise>
                    </xsl:choose>
                </table>

                <hr/>

                <table>
                    <tr>
                        <td align="left" valign="bottom">
                            <xforms:submit>
                                <xforms:label>Import Documents</xforms:label>
                                <xforms:setvalue ref="action">import-documents</xforms:setvalue>
                            </xforms:submit>
                            <xforms:submit>
                                <xforms:label>New Document</xforms:label>
                                <xforms:setvalue ref="action">new-document</xforms:setvalue>
                            </xforms:submit>
                        </td>
                    </tr>
                </table>

                <hr/>
            </xforms:group>
        </div>
    </body>
</html>
