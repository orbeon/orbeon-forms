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
            </query>
        </p:input>
        <p:output name="data" id="query"/>
    </p:processor>

    <!-- Call data access -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../data-access/get-categories.xpl"/>
        <p:input name="query" href="#query"/>
        <p:output name="categories" id="categories"/>
    </p:processor>

    <!-- Format response -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#categories"/>
        <p:input name="config">
            <params xsl:version="2.0">
                <param>
                    <value>
                        <array>
                            <data>
                                <xsl:for-each select="/categories/category">
                                    <value>
                                        <struct>
                                            <member>
                                                <name>description</name>
                                                <value><xsl:value-of select="name"/></value>
                                            </member>
                                            <member>
                                                <name>htmlUrl</name>
                                                <value>xxx/<xsl:value-of select="id"/></value>
                                            </member>
                                            <member>
                                                <name>rssUrl</name>
                                                <value>xxx/<xsl:value-of select="id"/></value>
                                            </member>
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
</params>
-->
