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
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <p:param type="input" name="query" schema-href="list-employees-query.rng"/>
    <p:param type="output" name="data"/>

    <p:processor name="oxf:pipeline">
        <p:input name="config" href="initialization/init-database.xpl"/>
    </p:processor>

    <p:processor name="oxf:sql">
        <p:input name="data" href="#query"/>
        <p:input name="datasource" href="../../datasource-sql.xml"/>
        <p:input name="config">
            <sql:config xmlns:sql="http://orbeon.org/oxf/xml/sql">
                <sql:connection>
                    <employees>
                        <sql:execute>
                            <!-- The following commented-out code works with SQL Server -->
<!--                            <sql:query>-->
<!--                                select top <sql:param select="/*/page-size" type="oxf:literalString" replace="true"/> *-->
<!--                                  from oxf_employee e-->
<!--                                 where employee_id not in-->
<!--                                       (select top <sql:param select="string(number(/*/page-size) * (number(/*/page-number) - 1))" type="oxf:literalString" replace="true"/> employee_id-->
<!--                                          from oxf_employee e-->
<!--                                         order by <sql:param select="/*/sort-column" type="oxf:literalString" replace="true"/>-->
<!--                                                  <sql:param select="concat(' ', /*/sort-order)" type="oxf:literalString" replace="true"/>)-->
<!--                                 order by <sql:param select="/*/sort-column" type="oxf:literalString" replace="true"/>-->
<!--                                          <sql:param select="concat(' ', /*/sort-order)" type="oxf:literalString" replace="true"/>-->
<!--                            </sql:query>-->
                            <!-- The following code works with HSQLDB -->
                            <sql:query>
                                select limit <sql:param select="concat(string(number(/*/page-size) * (number(/*/page-number) - 1)), ' ', /*/page-size)" type="oxf:literalString" replace="true"/>
                                       *
                                  from oxf_employee e
                                 order by <sql:param select="/*/sort-column" type="oxf:literalString" replace="true"/>
                                          <sql:param select="concat(' ', /*/sort-order)" type="oxf:literalString" replace="true"/>
                            </sql:query>
                            <sql:results>
                                <sql:row-results>
                                    <employee>
                                        <sql:get-columns format="xml"/>
                                    </employee>
                                </sql:row-results>
                            </sql:results>
                        </sql:execute>
                        <sql:execute>
                            <sql:query>
                                select count(*) employee_count from oxf_employee e
                            </sql:query>
                            <sql:results>
                                <sql:row-results>
                                    <sql:get-columns format="xml"/>
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
