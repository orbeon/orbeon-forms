<!--
    Copyright (C) 2005 Orbeon, Inc.

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
    xmlns:xi="http://www.w3.org/2003/XInclude"
    xmlns:f="http://orbeon.org/oxf/xml/formatting"
    xmlns:claim="http://orbeon.org/oxf/examples/bizdoc/claim"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns="http://www.w3.org/1999/xhtml">

    <xsl:variable name="post" select="/*/post" as="element()"/>
    <xsl:variable name="categories" select="/*/categories" as="element()"/>

    <head>
        <title>Recent Posts for
            <xsl:value-of select="doc('input:instance')/*/username"/>
        </title>
    </head>
    <body>
        <div id="maincontent">

            Categories:
            <xsl:for-each select="$categories/category">
                <xsl:if test="position() > 1">
                    |
                </xsl:if>
                <a href="xxx/{id}">
                    <xsl:value-of select="name"/>
                </a>
            </xsl:for-each>

            <xsl:for-each select="$post">
                <a name="post-{post-id}"/>
                <div>
                    <h2>
                        <a href="/blog/{doc('input:instance')/*/username}/post/{post-id}">
                            <xsl:value-of select="title"/>
                        </a>
                        -
                        <xsl:value-of select="categories"/>
                    </h2>
                    <div style="margin-left: 2em">
                        <xsl:copy-of select="description/node()"/>
                    </div>
                </div>
                <div>
                    (
                    <xsl:value-of select="format-dateTime(date-created, '[MNn] [D], [Y] [H01]:[m01]:[s01] UTC', 'en', (), ())"/>)
                </div>
                <h2>Comments</h2>
                <div id="comments">
                    <xsl:choose>
                        <xsl:when test="count(comments/comment) > 0">
                            <div id="comment">
                                <xsl:for-each select="comments/comment">
                                    Posted by
                                    <a href="mailto:{email}">
                                        <xsl:value-of select="name"/>
                                    </a>
                                    on
                                    <xsl:value-of select="format-dateTime(date-created, '[MNn] [D], [Y] [H01]:[m01]:[s01] UTC', 'en', (), ())"/>
                                    <br/>
                                    <div id="comment-text">
                                        <xsl:value-of select="text"/>
                                    </div>
                                </xsl:for-each>
                            </div>
                        </xsl:when>
                        <xsl:otherwise>
                            No comments yet.
                        </xsl:otherwise>
                    </xsl:choose>
                </div>
                <h2>Post New Comment</h2>
                <xforms:group ref="/form" xxforms:show-errors="{doc('input:instance')/form/action != ''}">
                    <table>
                        <tr>
                            <th>Name:</th>
                            <td>
                                <xforms:input ref="comment/name"/>
                            </td>
                        </tr>
                        <tr>
                            <th>Email:</th>
                            <td>
                                <xforms:input ref="comment/email"/>
                            </td>
                        </tr>
                        <tr>
                            <th>URI:</th>
                            <td>
                                <xforms:input ref="comment/uri"/>
                            </td>
                        </tr>
                        <tr>
                            <th colspan="2">
                                Please enter your comment:
                            </th>
                        </tr>
                        <tr>
                            <td colspan="2">
                                <xforms:textarea ref="comment/text"/>
                            </td>
                        </tr>
                        <tr>
                            <td colspan="2">
                                <xforms:output ref="check/value1"/>
                                +
                                <xforms:output ref="check/value2"/>
                                =
                                <xforms:input ref="check/value3" xhtml:size="3">
                                    <xforms:alert>Please enter a correct check value!</xforms:alert>
                                    <xforms:hint>Please enter a check value. This is a measure about comment spam.</xforms:hint>
                                </xforms:input>
                            </td>
                        </tr>
                    </table>
                    <xforms:submit>
                        <xforms:label>Preview</xforms:label>
                        <xforms:setvalue ref="/form/action">preview</xforms:setvalue>
                    </xforms:submit>
                    <xforms:submit>
                        <xforms:label>Submit</xforms:label>
                        <xforms:setvalue ref="/form/action">save</xforms:setvalue>
                    </xforms:submit>
                </xforms:group>
            </xsl:for-each>

        </div>
    </body>
</html>
