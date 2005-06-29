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

    <p:param type="input" name="query" schema-href="get-recent-posts-query.rng"/>
    <p:param type="output" name="posts"/>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#query"/>
        <p:input name="config">
            <xdb:query collection="/db/orbeon/blog-example/posts" create-collection="true" xsl:version="2.0">
                xquery version "1.0";
                <posts>
                    {
                        (for $i in
                            (/post[username = '<xsl:value-of select="/query/username"/>'
                                   and blog-id = '<xsl:value-of select="/query/blog-id"/>'
                                   and ('<xsl:value-of select="/query/category"/>' = '' or categories/category-id = '<xsl:value-of select="/query/category"/>')])
                        order by xs:dateTime($i/date-created) descending
                        return $i)[position() le <xsl:value-of select="/query/count"/>]
                    }
                </posts>
            </xdb:query>
        </p:input>
        <p:output name="data" id="xmldb-query"/>
    </p:processor>

    <p:processor name="oxf:xmldb-query">
        <p:input name="datasource" href="../datasource.xml"/>
        <p:input name="query" href="#xmldb-query"/>
        <p:output name="data" ref="posts" debug="xxxposts"/>
    </p:processor>

</p:config>
