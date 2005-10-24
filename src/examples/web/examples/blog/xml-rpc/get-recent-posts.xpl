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

    <!-- Get recent posts -->
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

    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../data-access/get-recent-posts.xpl"/>
        <p:input name="query" href="#query"/>
        <p:output name="posts" id="recent-posts"/>
    </p:processor>

    <!-- Call data access to get list of categories -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#params"/>
        <p:input name="config">
            <query xsl:version="2.0">
                <username><xsl:value-of select="/params/param[2]/value/string"/></username>
                <blog-id><xsl:value-of select="/params/param[1]/value/string"/></blog-id>
            </query>
        </p:input>
        <p:output name="data" id="categories-query"/>
    </p:processor>

    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../data-access/get-categories.xpl"/>
        <p:input name="query" href="#categories-query"/>
        <p:output name="categories" id="categories"/>
    </p:processor>

    <!-- Convert content -->
    <p:for-each href="#recent-posts" select="/posts/post" root="contents" id="contents">
        <p:processor name="oxf:xml-converter" xmlns:p="http://www.orbeon.com/oxf/pipeline">
             <p:input name="config">
                 <config>
                     <content-type>application/xml</content-type>
                     <encoding>utf-8</encoding>
                     <version>1.0</version>
                     <indent>true</indent>
                     <omit-xml-declaration>true</omit-xml-declaration>
                 </config>
             </p:input>
             <p:input name="data" href="current()#xpointer(/*/content)"/>
             <p:output name="data" ref="contents"/>
         </p:processor>
    </p:for-each>

    <!-- Format response -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#recent-posts"/>
        <p:input name="contents" href="#contents"/>
        <p:input name="categories" href="#categories"/>
        <p:input name="config">
            <params xsl:version="2.0">
                <xsl:variable name="contents" select="doc('input:contents')/*/*" as="element()+"/>
                <xsl:variable name="categories" select="doc('input:categories')/*/*" as="element()*"/>
                <param>
                    <value>
                        <array>
                            <data>
                                <xsl:for-each select="/posts/post">
                                    <xsl:variable name="position" select="position()"/>
                                    <xsl:variable name="content" select="$contents[$position]"/>
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
                                                <value><string><xsl:value-of select="substring($content, 10, string-length($content) - 19)"/></string></value>
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
                                                                <xsl:for-each select="categories/category-id">
                                                                    <value><string><xsl:value-of select="$categories[id = current()]/name"/></string></value>
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
