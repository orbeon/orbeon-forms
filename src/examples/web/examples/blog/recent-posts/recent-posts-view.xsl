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

    <xsl:variable name="posts" select="/*/posts" as="element()"/>
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

            <xsl:for-each select="$posts/post[published = 'true']">
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
                    |
                    <a href="/blog/{doc('input:instance')/*/username}/post/{post-id}">Permalink</a>
                    |
                    <a href="/blog/{doc('input:instance')/*/username}/post/{post-id}">Comments [
                        <xsl:value-of select="count(comments/comment)"/>]
                    </a>
                </div>
            </xsl:for-each>

        </div>
    </body>
</html>
