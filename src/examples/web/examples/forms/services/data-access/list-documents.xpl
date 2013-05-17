<!--
    Copyright (C) 2006 Orbeon, Inc.

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
          xmlns:dmv="http://orbeon.org/oxf/examples/dmv">

    <p:param name="documents" type="output"/>

    <p:processor name="oxf:xmldb-query">
        <p:input name="datasource" href="datasource.xml"/>
        <p:input name="query">
            <xdb:query collection="/db/ops/dmv-example" create-collection="true">
                xquery version "1.0";
                <document-infos>
                    {
                        for $d in /document-info
                            order by xs:dateTime($d/document-date) descending
                            return
                            <document-info>
                                {$d/document-id, $d/document-date}
                                <document>
                                    {
                                    $d//dmv:personal-information,
                                    $d//dmv:vehicle[1]
                                    }
                                </document>
                            </document-info>
                    }
                </document-infos>
            </xdb:query>
        </p:input>
        <p:output name="data" ref="documents"/>
    </p:processor>

</p:config>
