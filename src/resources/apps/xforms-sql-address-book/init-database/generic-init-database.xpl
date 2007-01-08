<!--
    Copyright 2004 Orbeon, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param name="init-pipeline" type="input"/>
    <p:param name="required-tables" type="input"/>

    <!-- List our database tables -->
    <p:processor name="oxf:java">
        <p:input name="config">
            <config sourcepath="." class="ListInitializedTables"/>
        </p:input>
        <p:input name="datasource" href="/config/datasource-sql.xml"/>
        <p:output name="data" id="found-tables"/>
    </p:processor>

    <!-- Create database if the tables are not found -->
    <p:choose href="aggregate('root', #found-tables, #required-tables)">
        <p:when test="count(for $i in /*/tables[2]/table/name return
                        if (upper-case($i) = (for $j in /*/tables[1]/table/name return upper-case($j))) then $i else ())
                   != count(/*/tables[2]/table/name)">
            <p:processor name="oxf:pipeline">
                <p:input name="config" href="#init-pipeline"/>
            </p:processor>
        </p:when>
    </p:choose>

</p:config>
