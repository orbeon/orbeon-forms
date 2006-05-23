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
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xi="http://www.w3.org/2001/XInclude"
    xmlns:saxon="http://saxon.sf.net/"
    xmlns:local="http://orbeon.org/oxf/xml/local">

    <xsl:function name="local:blog-path" as="xs:string">
        <xsl:param name="username" as="xs:string"/>
        <xsl:param name="blog-id" as="xs:string"/>
        <xsl:param name="category-id" as="xs:string?"/>

        <xsl:value-of select="concat('/blog/', $username, '/', $blog-id, if ($category-id) then concat('?category=', $category-id) else '')"/>
    </xsl:function>

    <xsl:function name="local:blog-feed-path" as="xs:string">
        <xsl:param name="username" as="xs:string"/>
        <xsl:param name="blog-id" as="xs:string"/>
        <xsl:param name="format" as="xs:string"/>
        <xsl:param name="category-id" as="xs:string?"/>

        <xsl:value-of select="concat(local:blog-path($username, $blog-id, ()), '?format=', $format, if ($category-id) then concat('&amp;', 'category=', $category-id) else '')"/>
    </xsl:function>

    <xsl:function name="local:post-path" as="xs:string">
        <xsl:param name="username" as="xs:string"/>
        <xsl:param name="blog-id" as="xs:string"/>
        <xsl:param name="post-id" as="xs:string"/>

        <xsl:value-of select="concat('/blog/', $username, '/', $blog-id, '/', $post-id)"/>
    </xsl:function>

    <xsl:function name="local:comments-path" as="xs:string">
        <xsl:param name="username" as="xs:string"/>
        <xsl:param name="blog-id" as="xs:string"/>
        <xsl:param name="post-id" as="xs:string"/>

        <xsl:value-of select="concat(local:post-path($username, $blog-id, $post-id), '#comments')"/>
    </xsl:function>

    <xsl:function name="local:path-to-url" as="xs:string">
        <xsl:param name="request" as="document-node()"/>
        <xsl:param name="path" as="xs:string"/>

        <xsl:value-of select="concat($request/*/scheme, '://', $request/*/server-name, if ($request/*/server-port != 80) then concat(':', $request/*/server-port) else '', $request/*/context-path, $path)"/>
    </xsl:function>

    <xsl:function name="local:format-rss20-dateTime" as="xs:string">
        <xsl:param name="dateTime" as="xs:dateTime"/>

        <xsl:value-of select="format-dateTime($dateTime, '[FNn,*-3], [D] [MNn,*-3] [Y] [H01]:[m01]:[s01] UTC', 'en', (), ())"/>
    </xsl:function>

    <xsl:function name="local:format-dateTime-default" as="xs:string">
        <xsl:param name="dateTime" as="xs:dateTime"/>
        <xsl:param name="omit-time" as="xs:boolean"/>

        <xsl:value-of select="format-dateTime($dateTime, if ($omit-time) then '[FNn] [MNn] [D], [Y]' else '[MNn] [D], [Y] [H01]:[m01]:[s01] UTC', 'en', (), ())"/>
    </xsl:function>

</xsl:stylesheet>
