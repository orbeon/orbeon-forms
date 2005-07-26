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
<html xmlns:f="http://orbeon.org/oxf/xml/formatting"
            xmlns:xhtml="http://www.w3.org/1999/xhtml"
            xmlns:xforms="http://www.w3.org/2002/xforms"
            xmlns:ev="http://www.w3.org/2001/xml-events"
            xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
            xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
            xmlns:xs="http://www.w3.org/2001/XMLSchema"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xmlns="http://www.w3.org/1999/xhtml"
            xsl:version="2.0">
    <head>
        <title>XForms Upload</title>
        <xforms:model>
            <xforms:instance>
                <form xmlns="">
                    <action/>
                    <!-- Using xs:anyURI will cause the XForms engine to store a reference to
                         a URI instead of inlinig the content of the file -->
                    <files>
                        <file filename="" mediatype="" size=""/>
                        <file filename="" mediatype="" size=""/>
                        <file filename="" mediatype="" size=""/>
                    </files>
                </form>
            </xforms:instance>
            <xforms:bind nodeset="/form/files/file" type="xs:anyURI"/>
<!--            <xforms:submission method="post" encoding="multipart/form-data"/>-->
            <xforms:submission id="main" method="post" replace="all" action="/xforms-upload"/>
        </xforms:model>
    </head>
    <body>
        <xsl:variable name="uploaded" select="/result/urls/url != ''" as="xs:boolean"/>
        <xsl:variable name="in-portlet" select="/result/request/container-type = 'portlet'" as="xs:boolean"/>
        <xsl:if test="/result/message">
            <p style="color: red">
                <xsl:value-of select="/result/message"/>
            </p>
        </xsl:if>
        <xforms:group ref="/form">
            <table>
                <tr>
                    <td>
                        <xsl:text>Please select up to </xsl:text>
                        <xforms:output value="count(files/file)"/>
                        <xsl:if test="$uploaded"> other</xsl:if>
                        <xsl:text> JPEG images to upload:</xsl:text>
                    </td>
                </tr>
<!--                <xforms:repeat nodeset="files/file">-->
<!--                    <tr>-->
<!--                        <td>-->
<!--                            <xforms:upload ref=".">-->
<!--                                <xforms:filename ref="@filename"/>-->
<!--                                <xforms:mediatype ref="@mediatype"/>-->
<!--                                <xxforms:size ref="@size"/>-->
<!--                            </xforms:upload>-->
<!--                        </td>-->
<!--                    </tr>-->
<!--                </xforms:repeat>-->
                <tr>
                    <td>
                        <xforms:upload ref="files/file[1]">
                            <xforms:filename ref="@filename"/>
                            <xforms:mediatype ref="@mediatype"/>
                            <xxforms:size ref="@size"/>
                        </xforms:upload>
                    </td>
                </tr>
                <tr>
                    <td>
                        <xforms:upload ref="files/file[2]">
                            <xforms:filename ref="@filename"/>
                            <xforms:mediatype ref="@mediatype"/>
                            <xxforms:size ref="@size"/>
                        </xforms:upload>
                    </td>
                </tr>
                <tr>
                    <td>
                        <xforms:upload ref="files/file[3]">
                            <xforms:filename ref="@filename"/>
                            <xforms:mediatype ref="@mediatype"/>
                            <xxforms:size ref="@size"/>
                        </xforms:upload>
                    </td>
                </tr>
            </table>
            <table class="gridtable">
                <tr>
                    <th>
                        Simple file upload
                    </th>
                    <td>

                        <xforms:trigger>
                            <xsl:if test="$in-portlet">
                                <xsl:attribute name="xhtml:disabled">true</xsl:attribute>
                            </xsl:if>
                            <xforms:label>Upload</xforms:label>
                            <xforms:action ev:event="DOMActivate">
                                <xforms:setvalue ref="action">simple-upload</xforms:setvalue>
                                <xforms:send submission="main"/>
                            </xforms:action>
                        </xforms:trigger>
                    </td>
                    <td>
                        This works only outside of the portal.
                        <xsl:choose>
                            <xsl:when test="$in-portlet">
                                Click
                                <xforms:trigger xxforms:appearance="link">
                                    <xforms:label>here</xforms:label>
                                    <xforms:action ev:event="DOMActivate">
                                        <xforms:setvalue ref="action">goto-simple-upload</xforms:setvalue>
                                        <xforms:send submission="main"/>
                                    </xforms:action>
                                </xforms:trigger>
                                to try.
                            </xsl:when>
                            <xsl:otherwise>
                                You can try it now.
                            </xsl:otherwise>
                        </xsl:choose>
                    </td>
                </tr>
                <tr>
                    <th>
                        Database file upload
                    </th>
                    <td>
                        <xforms:trigger>
                            <xforms:label>Upload</xforms:label>
                            <xforms:action ev:event="DOMActivate">
                                <xforms:setvalue ref="action">db-upload</xforms:setvalue>
                                <xforms:send submission="main"/>
                            </xforms:action>
                        </xforms:trigger>
                    </td>
                    <td>
                        This uses the internal SQL database. The uploaded images must be smaller
                        than 150K.
                    </td>
                </tr>
                <tr>
                    <th>
                        Web Service file upload
                    </th>
                    <td>
                        <xforms:trigger>
                            <xforms:label>Upload</xforms:label>
                            <xforms:action ev:event="DOMActivate">
                                <xforms:setvalue ref="action">ws-upload</xforms:setvalue>
                                <xforms:send submission="main"/>
                            </xforms:action>
                        </xforms:trigger>
                    </td>
                    <td>
                        The uploaded images must be smaller than 150K.
                    </td>
                </tr>
            </table>
            <xsl:if test="$uploaded">
                <h2>Submitted XForms Instance</h2>
                <f:box>
                    <f:xml-source>
                        <xsl:copy-of select="doc('input:instance')"/>
                    </f:xml-source>
                </f:box>
                <!-- Display uploaded images (when uploaded to Web Service or database) -->
                <h2>Uploaded Images</h2>
                <xsl:for-each select="/result/urls/url">
                    <xsl:variable name="position" select="position()"/>
                    <xsl:if test=". != ''">
                        <p>Uploaded image (<xsl:value-of select="doc('input:instance')/form/files/file[$position]/@size"/> bytes):</p>
                        <p>
                            <img src="{.}"/>
                        </p>
                    </xsl:if>
                </xsl:for-each>
            </xsl:if>
        </xforms:group>
    </body>
</html>
