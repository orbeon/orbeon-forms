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
                <post-id><xsl:value-of select="/params/param[1]/value/string"/></post-id>
            </query>
        </p:input>
        <p:output name="data" id="query"/>
    </p:processor>

    <!-- Call data access -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../data-access/get-post.xpl"/>
        <p:input name="query" href="#query"/>
        <p:output name="post" id="post"/>
    </p:processor>

    <!-- Convert content of description -->
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
         <p:input name="data" href="#post#xpointer(/*/description)"/>
         <p:output name="data" id="description"/>
     </p:processor>

    <!-- Format response -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#post"/>
        <p:input name="description" href="#description"/>
        <p:input name="config">
            <params xsl:version="2.0">
                <param>
                    <value>
                        <struct>
                            <xsl:for-each select="/*">
                                <member>
                                    <name>postid</name>
                                    <value><string><xsl:value-of select="post-id"/></string></value>
                                </member>
                                <member>
                                    <name>title</name>
                                    <value><string><xsl:value-of select="title"/></string></value>
                                </member>
                                <member>
                                    <name>description</name>
                                    <xsl:variable name="description" select="doc('input:description')/*" as="xs:string"/>
                                    <value><string><xsl:value-of select="substring($description, 14, string-length($description) - 27)"/></string></value>
                                </member>
                                <member>
                                    <name>published</name>
                                    <value><boolean><xsl:value-of select="if (published = 'true') then 1 else 0"/></boolean></value>
                                </member>
                                <member>
                                    <name>dateCreated</name>
                                    <value>
                                        <dateTime.iso8601><xsl:value-of select="date-created"/></dateTime.iso8601>
                                    </value>
                                </member>
<!--                                <member>-->
<!--                                    <name>flNotOnHomePage</name>-->
<!--                                    <value>-->
<!--                                        <boolean>0</boolean>-->
<!--                                    </value>-->
<!--                                </member>-->
<!--                                <member>-->
<!--                                    <name>permaLink</name>-->
<!--                                    <value>/jojo/stories/9/</value>-->
<!--                                </member>-->
<!--                                <member>-->
<!--                                    <name>userid</name>-->
<!--                                    <value>powerbook</value>-->
<!--                                </member>-->
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
                            </xsl:for-each>
                        </struct>
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
            <string>FF9D76D8-77C9-B858-AD8C-8877936EA081</string>
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
