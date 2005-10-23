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

    <p:param type="input" name="comment"/>

    <!-- Update comment document with comment-id -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#comment"/>
        <p:input name="config">
            <comment xsl:version="2.0" xmlns:uuid="java:org.orbeon.oxf.util.UUIDUtils">
                <comment-id><xsl:value-of select="uuid:createPseudoUUID()"/></comment-id>
                <xsl:copy-of select="/*/* except /*/comment-id"/>
            </comment>
        </p:input>
        <p:output name="data" id="updated-comment" debug="xxxupdated-comment"/>
    </p:processor>

    <!-- Insert it -->
    <p:processor name="oxf:xmldb-insert">
        <p:input name="datasource" href="../datasource.xml"/>
        <p:input name="query">
            <xdb:insert collection="/db/orbeon/blog-example/comments" create-collection="true"/>
        </p:input>
        <p:input name="data" href="#updated-comment"/>
    </p:processor>

</p:config>
