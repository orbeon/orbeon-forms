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

    <p:param type="input" name="query" schema-href="find-employee-query.rng"/>
    <p:param type="output" name="data"/>

    <p:processor name="oxf:sql">
        <p:input name="data" href="#query"/>
        <p:input name="config">
            <sql:config xmlns:sql="http://orbeon.org/oxf/xml/sql">
                <sql:connection>
                    <sql:datasource>db</sql:datasource>
                    <employees>
                        <sql:execute>
                            <sql:query>
                                select *
                                  from demo_employee e
                                 where manager_id = <sql:param select="/*/employee-id" type="xs:int"/>
                                 order by lastname, firstname, age, title, phone
                            </sql:query>
                            <sql:results>
                                <sql:row-results>
                                    <employee:employee>
                                        <sql:get-columns format="xml" prefix="employee" all-elements="true"/>
                                    </employee:employee>
                                </sql:row-results>
                            </sql:results>
                        </sql:execute>
                        <sql:execute>
                            <sql:query>
                                select *
                                  from demo_employee e
                                 where manager_id in (select employee_id
                                                        from demo_employee e
                                                       where manager_id = <sql:param select="/*/employee-id" type="xs:int"/>)
                                 order by manager_id, lastname, firstname, age, title, phone
                            </sql:query>
                            <sql:results>
                                <sql:row-results>
                                    <employee:employee>
                                        <sql:get-columns format="xml" prefix="employee" all-elements="true"/>
                                    </employee:employee>
                                </sql:row-results>
                            </sql:results>
                        </sql:execute>
                    </employees>
                </sql:connection>
            </sql:config>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
