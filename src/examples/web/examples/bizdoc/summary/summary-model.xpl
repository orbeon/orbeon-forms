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
          xmlns:xdb="http://orbeon.org/oxf/xml/xmldb">

    <p:param name="data" type="output" schema-href="summary-model.xsd"/>

    <!-- Return the ids of all the documents -->
    <p:processor name="oxf:xmldb-query">
        <p:input name="datasource" href="../datasource.xml"/>
        <p:input name="query">
            <xdb:query collection="/db/oxf/adaptive-example" create-collection="true">
                xquery version "1.0";
                <result>
                    {/document-info/document-id}
                </result>
            </xdb:query>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
