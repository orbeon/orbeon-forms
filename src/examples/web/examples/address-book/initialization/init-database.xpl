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

    <!-- Make sure database is initialized with the tables and data we are using -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="/init-database/init-database.xpl"/>
        <p:input name="required-tables">
            <tables>
                <table><name>oxf_address_book</name></table>
            </tables>
        </p:input>
        <p:input name="init-pipeline" href="init-script.xpl"/>
    </p:processor>

</p:config>
