<!--
    Copyright (C) 2004 Orbeon, Inc.

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
          xmlns:xdb="http://orbeon.org/oxf/xml/xmldb"
          xmlns:xu="http://www.xmldb.org/xupdate"
          xmlns:claim="http://orbeon.org/oxf/examples/bizdoc/claim">

    <p:param name="document-list" type="output" schema-href="../../summary/summary-model.xsd"/>

    <!-- Return the ids of all the documents -->
    <p:processor name="oxf:xmldb-query">
        <p:input name="datasource" href="datasource.xml"/>
        <p:input name="query">
            <xdb:query collection="/db/orbeon/bizdoc-example" create-collection="true">
                xquery version "1.0";
                <result>
                    {
                        for $i in /document-info return
                            <document-info>
                                {$i/document-id}
                                {$i/document/claim:claim/claim:insured-info/claim:general-info/claim:name-info/claim:last-name}
                                {$i/document/claim:claim/claim:insured-info/claim:general-info/claim:name-info/claim:first-name}
                            </document-info>
                    }
                </result>
            </xdb:query>
        </p:input>
        <p:output name="data" ref="document-list"/>
    </p:processor>

</p:config>
