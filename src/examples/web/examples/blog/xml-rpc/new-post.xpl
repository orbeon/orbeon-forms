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

    <!-- Create post document -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#params"/>
        <p:input name="config">
            <post xsl:version="2.0" xmlns:uuid="java:org.orbeon.oxf.util.UUIDUtils">
                <post-id><xsl:value-of select="uuid:createPseudoUUID()"/></post-id>
                <username><xsl:value-of select="/params/param[2]/value/string"/></username>
                <blog-id><xsl:value-of select="/params/param[1]/value/string"/></blog-id>
                <title><xsl:value-of select="/params/param[4]/value/struct/member[name = 'title']/value"/></title>
                <description>
                    <xsl:copy-of select="saxon:parse(concat('&lt;root>', /params/param[4]/value/struct/member[name = 'description']/value, '&lt;/root>'))/*/node()"/>
                </description>
                <published><xsl:value-of select="if (/params/param[5]/value/boolean = 1) then 'true' else 'false'"/></published>
                <date-created><xsl:value-of select="adjust-dateTime-to-timezone(current-dateTime(), xdt:dayTimeDuration('PT0H'))"/></date-created>
                <xsl:if test="/params/param[4]/value/struct/member[name = 'categories']/value/array/data/value/string[normalize-space(.) != '']">
                    <categories>
                        <xsl:for-each select="/params/param[4]/value/struct/member[name = 'categories']/value/array/data/value/string">
                            <category-name>
                                <xsl:value-of select="normalize-space(.)"/>
                            </category-name>
                        </xsl:for-each>
                    </categories>
                </xsl:if>
            </post>
        </p:input>
        <p:output name="data" id="post" debug="xxxnewpost"/>
    </p:processor>

    <!-- Insert it -->
    <p:processor name="oxf:xmldb-insert">
        <p:input name="datasource" href="../datasource.xml"/>
        <p:input name="query">
            <xdb:insert collection="/db/orbeon/blog-example/posts" create-collection="true"/>
        </p:input>
        <p:input name="data" href="#post"/>
    </p:processor>

    <!-- Create response -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#post"/>
        <p:input name="config">
            <params xsl:version="2.0">
                <param>
                    <value>
                        <string><xsl:value-of select="/post/post-id"/></string>
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
