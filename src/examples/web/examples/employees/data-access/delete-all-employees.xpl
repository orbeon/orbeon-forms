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
          xmlns:employee="http://orbeon.org/ops/examples/employee-demo/employee">

    <p:processor name="oxf:pipeline">
        <p:input name="config" href="initialization/init-database.xpl"/>
    </p:processor>

    <p:processor name="oxf:sql-output">
        <p:input name="config">
            <sql:config xmlns:sql="http://orbeon.org/oxf/xml/sql">
                <sql:connection>
                    <sql:datasource>db</sql:datasource>
                    <sql:execute>
                        <sql:update>
                            delete from demo_employee
                        </sql:update>
                    </sql:execute>
                    <dummy/>
                </sql:connection>
            </sql:config>
        </p:input>
        <p:output name="output" id="dummy"/>
    </p:processor>

    <p:processor name="oxf:null-serializer">
        <p:input name="data" href="#dummy"/>
    </p:processor>

</p:config>
