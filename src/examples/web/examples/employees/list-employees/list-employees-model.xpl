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
          xmlns:oxf="http://www.orbeon.com/oxf/processors"
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:xs="http://www.w3.org/2001/XMLSchema">

    <p:param type="input" name="instance"/>
    <p:param type="output" name="data"/>

    <p:processor name="oxf:xslt-2.0">
        <p:input name="data" href="#instance"/>
        <p:input name="config">
            <query xsl:version="2.0">
                <page-size><xsl:value-of select="if (/*/page-size castable as xs:positiveInteger) then /*/page-size else 10"/></page-size>
                <page-number><xsl:value-of select="if (/*/page-number castable as xs:positiveInteger) then /*/page-number else 1"/></page-number>
                <sort-column><xsl:value-of select="/*/sort-column"/></sort-column>
                <sort-order><xsl:value-of select="if (/*/sort-order = 'ascending') then 'asc' else 'desc'"/></sort-order>
            </query>
        </p:input>
        <p:output name="data" id="query"/>
    </p:processor>

    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../data-access/list-employees.xpl"/>
        <p:input name="query" href="#query"/>
        <p:output name="data" id="query-result"/>
    </p:processor>

    <p:processor name="oxf:request-security">
        <p:input name="config">
            <config><role>demo-user</role><role>demo-admin</role></config>
        </p:input>
        <p:output name="data" id="request-security"/>
    </p:processor>

    <p:processor name="oxf:identity">
        <p:input name="data" href="aggregate('root', #query-result, #instance, #request-security)"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
