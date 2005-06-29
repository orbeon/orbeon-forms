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
          xmlns:xdt="http://www.w3.org/2004/07/xpath-datatypes"
          xmlns:saxon="http://saxon.sf.net/"
          xmlns:xdb="http://orbeon.org/oxf/xml/xmldb"
          xmlns:xu="http://www.xmldb.org/xupdate">

    <p:param type="input" name="params"/>
    <p:param type="output" name="params"/>

    <!-- TODO: Separate data access -->

    <!-- Get post with given id  -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#params"/>
        <p:input name="config">
            <query xsl:version="2.0">
                <username><xsl:value-of select="/params/param[2]/value/string"/></username>
                <post-id><xsl:value-of select="/params/param[1]/value/string"/></post-id>
            </query>
        </p:input>
        <p:output name="data" id="post-query"/>
    </p:processor>

    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../data-access/get-post.xpl"/>
        <p:input name="query" href="#post-query"/>
        <p:output name="post" id="current-post"/>
    </p:processor>

    <!-- Call data access to get list of categories -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#current-post"/>
        <p:input name="config">
            <query xsl:version="2.0">
                <username><xsl:value-of select="/*/username"/></username>
                <blog-id><xsl:value-of select="/*/blog-id"/></blog-id>
            </query>
        </p:input>
        <p:output name="data" id="categories-query"/>
    </p:processor>

    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../data-access/get-categories.xpl"/>
        <p:input name="query" href="#categories-query"/>
        <p:output name="categories" id="categories"/>
    </p:processor>

    <!-- Create updated post document -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#params"/>
        <p:input name="current-post" href="#current-post"/>
        <p:input name="categories" href="#categories"/>
        <p:input name="config">
            <post xsl:version="2.0">
                <xsl:variable name="current-post" select="doc('input:current-post')" as="document-node()"/>
                <xsl:variable name="categories" select="doc('input:categories')/*/*" as="element()*"/>

                <xsl:copy-of select="$current-post/*/(post-id|username|blog-id)"/>
                <title><xsl:value-of select="/params/param[4]/value/struct/member[name = 'title']/value"/></title>
                <description>
                    <xsl:copy-of select="saxon:parse(concat('&lt;root>', /params/param[4]/value/struct/member[name = 'description']/value, '&lt;/root>'))/*/node()"/>
                </description>
                <published><xsl:value-of select="if (/params/param[5]/value/boolean = 1) then 'true' else 'false'"/></published>
                <date-created><xsl:value-of select="current-dateTime()"/></date-created>
                <xsl:if test="/params/param[4]/value/struct/member[name = 'categories']/value/array/data/value/string[normalize-space(.) != '']">
                    <categories>
                        <xsl:for-each select="/params/param[4]/value/struct/member[name = 'categories']/value/array/data/value/string">
                            <xsl:variable name="category-name" select="normalize-space(.)"/>
                            <category-id>
                                <xsl:value-of select="($categories[name = $category-name]/id, $categories[1]/id)[1]"/>
                            </category-id>
                        </xsl:for-each>
                    </categories>
                </xsl:if>
            </post>
        </p:input>
        <p:output name="data" id="post"/>
    </p:processor>

    <!-- Dynamically build query -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#post"/>
        <p:input name="config">
            <xdb:update collection="/db/orbeon/blog-example/posts" xsl:version="2.0">
                <xu:modifications version="1.0">
                    <xu:remove select="/post[username = '{/post/username}' and post-id = '{/post/post-id}']/*[name() != 'username' and name() != 'blog-id' and name() != 'post-id']"/>
                    <xu:append select="/post[username = '{/post/username}' and post-id = '{/post/post-id}']">
                        <xsl:copy-of select="/post/*[name() != 'username' and name() != 'blog-id' and name() != 'post-id']"/>
                    </xu:append>
                </xu:modifications>
            </xdb:update>
        </p:input>
        <p:output name="data" id="query"/>
    </p:processor>

    <!-- Run update -->
    <p:processor name="oxf:xmldb-update">
        <p:input name="datasource" href="../datasource.xml"/>
        <p:input name="query" href="#query"/>
    </p:processor>

    <!-- Create response -->
    <p:processor name="oxf:identity">
        <p:input name="data">
            <params>
                <param>
                    <value>
                        <boolean>1</boolean>
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
            <struct>
                <member>
                    <name>title</name>
                    <value>
                        <string>Test post</string>
                    </value>
                </member>
                <member>
                    <name>description</name>
                    <value>
                        <string>&lt;p&gt; This is text. &lt;/p&gt;</string>
                    </value>
                </member>
                <member>
                    <name>categories</name>
                    <value>
                        <array>
                            <data>
                                <value>
                                    <string>General</string>
                                </value>
                            </data>
                        </array>
                    </value>
                </member>
            </struct>
        </value>
    </param>
    <param>
        <value>
            <boolean>0</boolean>
        </value>
    </param>
</params>
-->
