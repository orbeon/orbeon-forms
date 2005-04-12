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
<recent-posts xsl:version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:xi="http://www.w3.org/2003/XInclude">

    <xsl:variable name="instance" select="doc('input:instance')/*" as="element()"/>
    <xsl:variable name="blog" select="doc('input:blog')/*" as="element()"/>
    <xsl:variable name="posts" select="doc('input:posts')/*" as="element()"/>
    <xsl:variable name="categories" select="doc('input:categories')/*" as="element()"/>

    <user>
        <username><xsl:value-of select="doc('input:instance')/*/username"/></username>
    </user>

    <xsl:copy-of select="$blog"/>

    <categories>
        <xsl:for-each select="$categories/category">
            <xsl:copy>
                <xsl:copy-of select="*"/>
                <link><xsl:value-of select="concat('/', $instance/username, '/', $blog/blog-id, '/', '?category=', id)"/></link>
            </xsl:copy>
        </xsl:for-each>
    </categories>

    <feeds>
        <feed>
            <name>All</name>
            <link><xsl:value-of select="concat('/', doc('input:instance')/*/username, '/', $blog/blog-id, '?format=rss')"/></link>
        </feed>
        <xsl:for-each select="$categories/category">
            <feed>
                <name><xsl:value-of select="name"/></name>
                <link><xsl:value-of select="concat('/', doc('input:instance')/*/username, '/', $blog/blog-id, '?format=rss&amp;category=', name)"/></link>
            </feed>
        </xsl:for-each>
    </feeds>

    <posts>
<!--        <xsl:for-each-group select="$posts/post[published = 'true']" group-by="substring(date-created, 1, 10)">-->
        <xsl:for-each-group select="$posts/post[published = 'true']" group-by="xs:date(xs:dateTime(date-created))">
            <xsl:sort select="xs:dateTime(date-created)" order="descending"/>
            <day>
                <date><xsl:value-of select="date-created"/></date>
                <formatted-date><xsl:value-of select="format-dateTime(date-created, '[FNn] [MNn] [D], [Y]', 'en', (), ())"/></formatted-date>

                <xsl:for-each select="current-group()">
                    <xsl:sort select="xs:dateTime(date-created)" order="descending"/>

                    <xsl:copy>
                        <xsl:copy-of select="*"/>
                        <links>
                            <fragment-name><xsl:value-of select="concat('post-', post-id)"/></fragment-name>
                            <post><xsl:value-of select="concat('/blog/', $instance/username, '/post/', post-id)"/></post>
                            <comments><xsl:value-of select="concat('/blog/', $instance/username, '/post/', post-id, '#comments')"/></comments>
                            <category></category>
                        </links>
                        <formatted-date-created><xsl:value-of select="format-dateTime(date-created, '[FNn] [MNn] [D], [Y]', 'en', (), ())"/></formatted-date-created>
                        <formatted-dateTime-created><xsl:value-of select="format-dateTime(date-created, '[MNn] [D], [Y] [H01]:[m01]:[s01] UTC', 'en', (), ())"/></formatted-dateTime-created>
                    </xsl:copy>

                </xsl:for-each>
            </day>
        </xsl:for-each-group>
    </posts>

</recent-posts>
