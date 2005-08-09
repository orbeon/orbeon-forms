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
      xmlns:ev="http://www.w3.org/2001/xml-events"
      xmlns:xi="http://www.w3.org/2003/XInclude"
      xmlns:claim="http://orbeon.org/oxf/examples/bizdoc/claim"
      xmlns="http://www.w3.org/1999/xhtml">
    <head>
        <title>Summary</title>
        <xforms:model>
            <!-- This instance is used to generate requests to the server -->
            <xforms:instance id="request-instance">
                <form xmlns="">
                    <action/>
                    <document-id/>
                </form>
            </xforms:instance>
            <!-- This instance contains the list of documents to display -->
            <xforms:instance id="document-infos-instance">
                <xsl:copy-of select="/result"/>
            </xforms:instance>
            <xforms:submission id="main-submission" ref="instance('request-instance')" method="post" action="/bizdoc2"/>
            <xforms:submission id="delete-submission" ref="instance('request-instance')" replace="instance" instance="document-infos-instance" method="post" action="/bizdoc2/delete"/>
            <xforms:submission id="import-submission" ref="instance('request-instance')" replace="instance" instance="document-infos-instance" method="post" action="/bizdoc2/import"/>
        </xforms:model>
    </head>
    <body>
        <div class="maincontent">
            <xi:include href="../view-logo.xml"/>
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
                <!--<xsl:choose>-->
                    <!--<xsl:when test="count(/result/document-info) > 0">-->
                        <xforms:repeat id="documentInfoRepeat" nodeset="instance('document-infos-instance')/document-info">
                            <tr>
                                <td>
                                    <xforms:output ref="claim:last-name"/>
                                </td>
                                <td>
                                    <xforms:output ref="claim:first-name"/>
                                </td>
                                <td>
                                    <xforms:output ref="document-id"/>
                                </td>
                                <td>
                                    <xforms:trigger>
                                        <xforms:label>Edit</xforms:label>
                                        <xforms:action ev:event="DOMActivate">
                                            <xforms:setvalue ref="instance('request-instance')/action">show-detail</xforms:setvalue>
                                            <xforms:setvalue ref="instance('request-instance')/document-id" value="instance('document-infos-instance')/document-info[index('documentInfoRepeat')]/document-id"/>
                                            <xforms:send submission="main-submission"/>
                                        </xforms:action>
                                    </xforms:trigger>
                                </td>
                                <td>
                                    <xforms:trigger>
                                        <xforms:label>XML</xforms:label>
                                        <xforms:action ev:event="DOMActivate">
                                            <xforms:setvalue ref="instance('request-instance')/action">show-xml</xforms:setvalue>
                                            <xforms:setvalue ref="instance('request-instance')/document-id" value="instance('document-infos-instance')/document-info[index('documentInfoRepeat')]/document-id"/>
                                            <xforms:send submission="main-submission"/>
                                        </xforms:action>
                                    </xforms:trigger>
                                </td>
                                <td align="center">
                                    <xforms:trigger><!--  xxforms:appearance="image" -->
<!--                                            <xxforms:img src="/images/remove.png"/>-->
                                        <xforms:label>Delete</xforms:label>
                                        <xforms:action ev:event="DOMActivate">
                                            <xforms:setvalue ref="instance('request-instance')/action">delete-documents</xforms:setvalue>
                                            <xforms:setvalue ref="instance('request-instance')/document-id" value="instance('document-infos-instance')/document-info[index('documentInfoRepeat')]/document-id"/>
                                            <xforms:send submission="delete-submission"/>
                                        </xforms:action>
                                    </xforms:trigger>
                                </td>
                            </tr>
                            <!--<xforms:switch>-->
                                <!--<xforms:case>-->
                                    <!--<tr>-->
                                        <!--<td colspan="6">-->
                                            <!--<i>-->
                                                <!--No document found.<br/>Please press the "Import Documents"-->
                                                <!--button to get started.-->
                                            <!--</i>-->
                                        <!--</td>-->
                                    <!--</tr>-->
                                <!--</xforms:case>-->
                                <!--<xforms:case>-->
                                <!--</xforms:case>-->
                            <!--</xforms:switch>-->
                        </xforms:repeat>
                    <!--</xsl:when>-->
                    <!--<xsl:otherwise>-->
                        <!--<tr>-->
                            <!--<td colspan="6"><i>No document found.<br/>Please press the "Import-->
                            <!--Documents" button to get started.</i></td>-->
                        <!--</tr>-->
                    <!--</xsl:otherwise>-->
                <!--</xsl:choose>-->
            </table>
            <hr/>
            <xforms:group ref="instance('request-instance')">
                <table>
                    <tr>
                        <td align="left" valign="bottom">
                            <xforms:trigger>
                                <xforms:label>Import Documents</xforms:label>
                                <xforms:action ev:event="DOMActivate">
                                    <xforms:setvalue ref="action">import-documents</xforms:setvalue>
                                    <xforms:send submission="import-submission"/>
                                </xforms:action>
                            </xforms:trigger>
                            <xforms:trigger>
                                <xforms:label>New Document</xforms:label>
                                <xforms:action ev:event="DOMActivate">
                                    <xforms:setvalue ref="action">new-document</xforms:setvalue>
                                    <xforms:send submission="main-submission"/>
                                </xforms:action>
                            </xforms:trigger>
                        </td>
                    </tr>
                </table>
            </xforms:group>
            <hr/>
        </div>
    </body>
</html>
