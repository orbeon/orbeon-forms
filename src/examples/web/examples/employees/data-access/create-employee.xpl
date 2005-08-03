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
          xmlns:xs="http://www.w3.org/2001/XMLSchema"
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:employee="http://orbeon.org/ops/examples/employee-demo/employee">

    <p:param type="input" name="employee" schema-href="../schema/employee-schema.xsd"/>

    <p:processor name="oxf:pipeline">
        <p:input name="config" href="initialization/init-database.xpl"/>
    </p:processor>

    <p:processor name="oxf:sql">
        <p:input name="data" href="#employee"/>
        <p:input name="datasource" href="../../datasource-sql.xml"/>
        <p:input name="config">
            <sql:config xmlns:sql="http://orbeon.org/oxf/xml/sql">
                <sql:connection>
                    <sql:execute>
                        <sql:update>
                            insert into oxf_employee (employee_id, firstname, lastname, phone, title, age, manager_id)
                            values (
                                (select max(employee_id) + 1 from oxf_employee),
                                <sql:param type="xs:string" select="/*/employee:firstname"/>,
                                <sql:param type="xs:string" select="/*/employee:lastname"/>,
                                <sql:param type="xs:string" select="/*/employee:phone" null-if="/*/employee:phone = ''"/>,
                                <sql:param type="xs:string" select="/*/employee:title" null-if="/*/employee:title = ''"/>,
                                <sql:param type="xs:int" select="/*/employee:age" null-if="/*/employee:age = ''"/>,
                                <sql:param type="xs:int" select="/*/employee:manager-id" null-if="/*/employee:manager-id = ''"/>
                            )
                        </sql:update>
                    </sql:execute>
                </sql:connection>
            </sql:config>
        </p:input>
    </p:processor>

</p:config>
