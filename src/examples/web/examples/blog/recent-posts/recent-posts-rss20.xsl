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
    xmlns:xi="http://www.w3.org/2003/XInclude"
    xmlns:saxon="http://saxon.sf.net/"
    xmlns:local="http://orbeon.org/oxf/xml/local">

    <xsl:import href="../util/blog-functions.xsl"/>

    <xsl:output method="html" omit-xml-declaration="yes" name="html-output"/>

    <xsl:variable name="request" select="doc('input:request')" as="document-node()"/>

    <xsl:template match="/">
        <rss version="2.0">

            <channel>
                <title>
                    <xsl:value-of select="/*/blog/name"/>
                    <xsl:text> - Recent Posts</xsl:text>
                </title>
                <link>
                    <xsl:value-of select="local:path-to-url($request, local:blog-path(/*/blog/username, /*/blog/blog-id, ()))"/>
                </link>
                <description>
                    <xsl:value-of select="/*/blog/name"/><!-- TODO: store this in blog as well -->
                </description>

        <!--        <language></language>-->
        <!--        <copyright></copyright>-->
                <pubDate>
                    <xsl:value-of select="local:format-rss20-dateTime((/*/posts/day/post)[1]/date-created)"/>
                </pubDate>
        <!--        <lastBuildDate></lastBuildDate>-->
        <!--        <category></category>-->
                <docs>http://blogs.law.harvard.edu/tech/rss</docs>
                <generator>Orbeon PresentationServer</generator>
        <!--        <managingEditor></managingEditor>-->
        <!--        <webMaster></webMaster>-->
        <!--        <ttl></ttl>-->
        <!--        <image>-->
        <!--            <url></url>-->
        <!--            <title></title>-->
        <!--            <link></link>-->
        <!--            <width></width>-->
        <!--            <height></height>-->
        <!--        </image>-->

                <xsl:for-each select="/*/posts/day/post">

        <!--                <date><xsl:value-of select="date-created"/></date>-->
        <!--                <formatted-date><xsl:value-of select="format-dateTime(date-created, '[FNn] [MNn] [D], [Y]', 'en', (), ())"/></formatted-date>-->

                    <item>

                        <title>
                            <xsl:value-of select="title"/>
                        </title>
                        <link>
                            <xsl:value-of select="local:path-to-url($request, local:post-path(/*/blog/username, /*/blog/blog-id, post-id))"/>
                        </link>
                        <description>
                            <xsl:value-of select="substring-before(substring-after(saxon:serialize(description, 'html-output'), '>'), '&lt;/description>')"/>
                        </description>
                        <author>
                            <xsl:value-of select="username"/><!-- TODO: should have full name information -->
                        </author>
                        <comments>
                            <xsl:value-of select="local:path-to-url($request, local:comments-path(/*/blog/username, /*/blog/blog-id, post-id))"/>
                        </comments>
                        <pubDate>
                            <xsl:value-of select="local:format-rss20-dateTime(date-created)"/>
                        </pubDate>
                        <guid isPermaLink="true">
                            <xsl:value-of select="local:path-to-url($request, local:post-path(/*/blog/username, /*/blog/blog-id, post-id))"/>
                        </guid>

        <!--                        <source></source>-->
        <!--                        <enclosure></enclosure>-->
        <!--                        <category></category>-->

                    </item>

                </xsl:for-each>

            </channel>

        </rss>
    </xsl:template>

</xsl:stylesheet>
