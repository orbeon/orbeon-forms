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
<xhtml:html xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml"
            xmlns:xf="http://www.w3.org/2002/xforms"
            xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
            xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
            xmlns:xs="http://www.w3.org/2001/XMLSchema"
            xsl:version="2.0">
    <xhtml:head><xhtml:title>XForms Upload</xhtml:title></xhtml:head>
    <xhtml:body>
        <xsl:variable name="uploaded" select="/result/urls/url != ''" as="xs:boolean"/>
        <xsl:variable name="in-portlet" select="/result/request/container-type = 'portlet'" as="xs:boolean"/>
        <xsl:if test="/result/message">
            <p style="color: red">
                <xsl:value-of select="/result/message"/>
            </p>
        </xsl:if>
        <xf:group ref="/form">
            <table>
                <tr>
                    <td rowspan="3">
                        <xsl:text>Please select up to three</xsl:text>
                        <xsl:if test="$uploaded"> other</xsl:if>
                        <xsl:text> JPEG images to upload:</xsl:text>
                    </td>
                    <td>
                        <xf:upload ref="files/file[1]">
                            <xf:filename ref="@filename"/>
                            <xf:mediatype ref="@mediatype"/>
                            <xxf:size ref="@size"/>
                        </xf:upload>
                    </td>
                </tr>
                <tr>
                    <td>
                        <xf:upload ref="files/file[2]">
                            <xf:filename ref="@filename"/>
                            <xf:mediatype ref="@mediatype"/>
                            <xxf:size ref="@size"/>
                        </xf:upload>
                    </td>
                </tr>
                <tr>
                    <td>
                        <xf:upload ref="files/file[3]">
                            <xf:filename ref="@filename"/>
                            <xf:mediatype ref="@mediatype"/>
                            <xxf:size ref="@size"/>
                        </xf:upload>
                    </td>
                </tr>
            </table>
            <table class="gridtable">
                <tr>
                    <th>
                        Simple file upload
                    </th>
                    <td>

                        <xf:submit>
                            <xsl:if test="$in-portlet">
                                <xsl:attribute name="xhtml:disabled">true</xsl:attribute>
                            </xsl:if>
                            <xf:label>Upload</xf:label>
                            <xf:setvalue ref="action">simple-upload</xf:setvalue>
                        </xf:submit>
                    </td>
                    <td>
                        This works only outside of the portal.
                        <xsl:choose>
                            <xsl:when test="$in-portlet">
                                Click
                                <xf:submit xxf:appearance="link">
                                    <xf:label>here</xf:label>
                                    <xf:setvalue ref="action">goto-simple-upload</xf:setvalue>
                                </xf:submit>
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
                        <xf:submit>
                            <xf:label>Upload</xf:label>
                            <xf:setvalue ref="action">db-upload</xf:setvalue>
                        </xf:submit>
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
                        <xf:submit>
                            <xf:label>Upload</xf:label>
                            <xf:setvalue ref="action">ws-upload</xf:setvalue>
                        </xf:submit>
                    </td>
                    <td>
                        The uploaded images must be smaller than 150K.
                    </td>
                </tr>
            </table>
            <xhtml:p>
            </xhtml:p>
            <!-- Display uploaded images (when uploaded with Web Service) -->
            <xsl:if test="$uploaded">
                <xsl:for-each select="/result/urls/url">
                    <xsl:if test=". != ''">
                        <xhtml:p>Uploaded image (<xf:output ref="files/file[{position()}]/@size"/> bytes):</xhtml:p>
                        <xhtml:p>
                            <img src="{.}"/>
                        </xhtml:p>
                    </xsl:if>
                </xsl:for-each>
            </xsl:if>
        </xf:group>
    </xhtml:body>
</xhtml:html>
