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

    <p:param name="instance" type="input"/>

    <!-- Update or insert -->
    <p:choose href="#instance">
        <p:when test="/*/document/employee:employee/employee:employee-id != ''">
            <p:processor name="oxf:pipeline">
                <p:input name="config" href="../data-access/update-employee.xpl"/>
                <p:input name="employee" href="#instance#xpointer(/*/document/*)"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <p:processor name="oxf:pipeline">
                <p:input name="config" href="../data-access/create-employee.xpl"/>
                <p:input name="employee" href="#instance#xpointer(/*/document/*)"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

</p:config>
