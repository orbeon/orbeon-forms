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
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:oxf="http://www.orbeon.com/oxf/processors"
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:xs="http://www.w3.org/2001/XMLSchema"
          xmlns:xdb="http://orbeon.org/oxf/xml/xmldb"
          xmlns:xu="http://www.xmldb.org/xupdate">

    <p:param type="input" name="params"/>
    <p:param type="output" name="params"/>

    <!-- Format query -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#params"/>
        <p:input name="config">
            <query xsl:version="2.0">
                <username><xsl:value-of select="/params/param[2]/value/string"/></username>
                <blog-id><xsl:value-of select="/params/param[1]/value/string"/></blog-id>
                <count><xsl:value-of select="/params/param[4]/value/int"/></count>
            </query>
        </p:input>
        <p:output name="data" id="query"/>
    </p:processor>

    <!-- Call data access -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../data-access/get-recent-posts.xpl"/>
        <p:input name="query" href="#query"/>
        <p:output name="posts" id="recent-posts"/>
    </p:processor>

    <!-- Format response -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#recent-posts"/>
        <p:input name="config">
            <params xsl:version="2.0">
                <param>
                    <value>
                        <array>
                            <data>
                                <xsl:for-each select="/posts/post">
                                    <value>
                                        <struct>
                                            <member>
                                                <name>postid</name>
                                                <value><xsl:value-of select="post-id"/></value>
                                            </member>
                                            <member>
                                                <name>title</name>
                                                <value><xsl:value-of select="title"/></value>
                                            </member>
                                            <member>
                                                <name>description</name>
                                                <value><xsl:value-of select="description"/></value>
                                            </member>
                                            <member>
                                                <name>published</name>
                                                <value><xsl:value-of select="if (published = 'true') then 1 else 0"/></value>
                                            </member>
                                            <member>
                                                <name>dateCreated</name>
                                                <value>
                                                    <dateTime.iso8601><xsl:value-of select="date-created"/></dateTime.iso8601>
                                                </value>
                                            </member>
                                            <xsl:if test="categories">
                                                <member>
                                                    <name>categories</name>
                                                    <value>
                                                        <array>
                                                            <data>
                                                                <xsl:for-each select="categories/category-name">
                                                                    <value><string><xsl:value-of select="."/></string></value>
                                                                </xsl:for-each>
                                                            </data>
                                                        </array>
                                                    </value>
                                                </member>
                                            </xsl:if>
                                        </struct>
                                    </value>
                                </xsl:for-each>
                            </data>
                        </array>
                    </value>
                </param>
            </params>
        </p:input>
        <p:output name="data" ref="params"/>
    </p:processor>

</p:config>
<!--
<params>
        <param>
            <value>
                <string>2997323</string>
            </value>
        </param>
        <param>
            <value>
                <string>ebruchez</string>
            </value>
        </param>
        <param>
            <value>
                <string>ebruchez</string>
            </value>
        </param>
        <param>
            <value>
                <int>1</int>
            </value>
        </param>
    </params>
-->
