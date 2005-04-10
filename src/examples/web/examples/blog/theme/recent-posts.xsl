<?xml version="1.0" encoding="iso-8859-1"?>
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
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns="http://www.w3.org/1999/xhtml">

    <head>
        <title>
            <xsl:value-of select="/*/blog/name"/>
            <xsl:text> - Recent Posts</xsl:text>
        </title>
        <style type="text/css">
            .blogImage { margin: 5px; }
            body { font-family: "trebuchet ms", verdana, arial, helvetica, sans-serif }
            h1,h2,h3,h4,h5,h6 { font-weight: normal; border-bottom: 1px solid #ccc; text-align: left }
            ul { list-style-image: url('/images/bullet2.gif') }
            ul ul { list-style-image: url('/images/bullet1.gif') }
            a:hover { background: rgb(92,116,152); color: white }
        </style>
    </head>
    <body>
        <table>
            <tr>
                <td style="vertical-align: top; padding: 10px">
                    <xsl:text>Categories: </xsl:text>
                    <b>All </b>
                    <xsl:text> | </xsl:text>
                    <xsl:for-each select="/*/categories/category">
                        <xsl:if test="position() > 1">
                            <xsl:text> | </xsl:text>
                        </xsl:if>
                        <a href="{link}">
                            <xsl:value-of select="name"/>
                        </a>
                    </xsl:for-each>

                    <xsl:choose>
                        <xsl:when test="/*/posts/day/post">
                            <xsl:for-each select="/*/posts/day">
                                <a name="{links/fragment-name}"/>
                                <h2>
                                    <xsl:value-of select="formatted-date"/>
                                </h2>
                                <xsl:for-each select="post">
                                    <h3>
                                        <a href="{links/post}">
                                            <img src="/images/permalink.png" style="border: 0px"/>
                                            <xsl:text> </xsl:text>
                                            <xsl:value-of select="title"/>
                                        </a>
                                    </h3>
                                    <div style="margin-left: 2em; border: 1px solid #ccc; padding: 1em; background-color: #eee">
                                        <xsl:copy-of select="description/node()"/>
                                    </div>
                                    <div style="margin-left: 2em; padding: 1em">
                                        (<xsl:value-of select="formatted-dateTime-created"/>)
                                        <xsl:text> </xsl:text>
                                        <a href="{links/post}">Permalink</a>
                                        <xsl:text> </xsl:text>
                                        <a href="{links/comments}">Comments
                                        [<xsl:value-of select="count(comments/comment)"/>]
                                        </a>

                                        <!--
                                        <br/>
                                        Filed under: <xsl:value-of select="categories"/>
                                        — <xsl:value-of select="doc('input:instance')/*/username"/>
                                        @ <xsl:value-of select="format-dateTime(date-created, '[MNn] [D], [Y] [H01]:[m01]:[s01] UTC', 'en', (), ())"/>
                                        -->
                                    </div>
                                </xsl:for-each>
                            </xsl:for-each>
                        </xsl:when>
                        <xsl:otherwise>
                            No posts yet.
                        </xsl:otherwise>
                    </xsl:choose>
                </td>
                <td style="vertical-align: top; padding: 10px; border-left: 1px solid #cccccc">
                    <table style="border: 0px solid #ccc; margin-top: 10px; margin-bottom: 10px; width: 100%" cellpadding="5">
                        <tr>
                            <td>
                                <h2>Latest Entries</h2>
<!--                                <ol style="list-style-position: inside">-->
                                    <xsl:for-each select="/*/posts/day/post">
                                        <li style="white-space: nowrap; list-style-type: none; margin-left: 0px; padding-left: 0px">
                                            <a href="#{links/fragment-name}"><xsl:value-of select="title"/></a>
                                        </li>
                                    </xsl:for-each>
<!--                                </ol>-->
                            </td>
                        </tr>
                        <tr>
                            <td>
                                <h2>Blogroll</h2>
                                <xsl:variable name="blogroll" as="element()">
                                    <blogroll xmlns="">
                                        <entry>
                                            <feed-url>http://feeds.feedburner.com/avernet</feed-url>
                                            <page-url>http://avernet.blogspot.com/</page-url>
                                            <name>Alessandro Vernet</name>
                                        </entry>
                                        <entry>
                                            <feed-url>http://feeds.feedburner.com/scottmcmullan</feed-url>
                                            <page-url>http://www.scottmcmullan.com/blog/</page-url>
                                            <name>Scott McMullan</name>
                                        </entry>
                                        <entry>
                                            <feed-url>http://www.orbeon.com/blog/wp-rss2.php</feed-url>
                                            <page-url>http://www.orbeon.com/blog/</page-url>
                                            <name>XML-Centric Web Apps</name>
                                        </entry>
                                        <entry>
                                            <feed-url>http://feeds.feedburner.com/oss</feed-url>
                                            <page-url>http://otazi.blogspot.com/</page-url>
                                            <name>Omar Tazi</name>
                                        </entry>
                                    </blogroll>
                                </xsl:variable>
                                <xsl:for-each select="$blogroll/entry">
                                    <li style="white-space: nowrap; list-style-type: none; margin-left: 0px; padding-left: 0px">
                                        <a href="{feed-url}">
                                            <img src="/images/xml.gif" style="border: 0px; vertical-align: middle"/>
                                        </a>
                                        <xsl:text> </xsl:text>
                                        <a href="{page-url}" title="{name}" class="rBookmark0">
                                            <xsl:value-of select="name"/>
                                        </a>
                                    </li>
                                </xsl:for-each>
                            </td>
                        </tr>
                        <tr>
                            <td>
                                <h2>RSS Feeds</h2>
                                <ol>
                                    <xsl:for-each select="/*/posts/post">
                                        <li>
                                            <a href="#{links/fragment-name}"><xsl:value-of select="title"/></a>
                                        </li>
                                    </xsl:for-each>
                                </ol>
                            </td>
                        </tr>
                    </table>
                </td>
            </tr>
        </table>
    </body>
</html>
