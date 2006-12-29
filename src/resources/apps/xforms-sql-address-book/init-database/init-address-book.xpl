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
          xmlns:xs="http://www.w3.org/2001/XMLSchema"
          xmlns:sql="http://orbeon.org/oxf/xml/sql"
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:processor name="oxf:sql">
        <p:input name="datasource" href="/config/datasource-sql.xml"/>
        <p:input name="config">
            <sql:config>
                <sql:connection>
                    <!-- Drop tables if they exist -->
                    <sql:execute>
                        <sql:update>drop table orbeon_address_book if exists</sql:update>
                    </sql:execute>
                    <!-- Create tables -->
                    <sql:execute>
                        <sql:update>
                            create table orbeon_address_book (
                                id      integer not null identity primary key,
                                first   varchar(255) not null,
                                last    varchar(255) not null,
                                phone   varchar(255) not null
                            )
                        </sql:update>
                    </sql:execute>
                    <!-- Insert initial data -->
                    <sql:execute>
                        <sql:update>insert into orbeon_address_book values (null, 'John', 'Smith', '555-123-4567')</sql:update>
                    </sql:execute>
                    <sql:execute>
                        <sql:update>insert into orbeon_address_book values (null, 'Tom', 'Washington', '555-123-4567')</sql:update>
                    </sql:execute>
                    <dummy/>
                </sql:connection>
            </sql:config>
        </p:input>
    </p:processor>

</p:config>
