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
    xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
    xmlns:employee="http://orbeon.org/ops/examples/employee-demo/employee">

    <!-- Extract request body as a URI -->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config stream-type="xs:anyURI">
                <include>/request/body</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <!-- Dereference URI and return XML -->
    <p:processor name="oxf:url-generator">
        <p:input name="config" href="aggregate('config', aggregate('url', #request#xpointer(string(/request/body))))"/>
        <p:output name="data" id="file"/>
    </p:processor>

    <!-- Iterate over employees and insert them into the database -->
    <p:for-each href="#file" select="/soapenv:Envelope/soapenv:Body/employee:employees/employee:employee">
        <p:processor name="oxf:pipeline">
            <p:input name="config" href="../data-access/update-employee.xpl"/>
            <p:input name="employee" href="current()"/>
        </p:processor>
    </p:for-each>

    <!-- Return empty SOAP body -->
    <p:processor name="oxf:xml-serializer">
        <p:input name="config"><config/></p:input>
        <p:input name="data">
            <soapenv:Envelope
                xmlns:xsi="http://www.w3.org/1999/XMLSchema-instance"
                xmlns:xsd="http://www.w3.org/1999/XMLSchema">
                <soapenv:Body/>
            </soapenv:Envelope>
        </p:input>
    </p:processor>

</p:config>
