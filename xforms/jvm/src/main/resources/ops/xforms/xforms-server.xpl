<!--
  Copyright (C) 2010 Orbeon, Inc.

  This program is free software; you can redistribute it and/or modify it under the terms of the
  GNU Lesser General Public License as published by the Free Software Foundation; either version
  2.1 of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Lesser General Public License for more details.

  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  -->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:oxf="http://www.orbeon.com/oxf/processors"
          xmlns:xs="http://www.w3.org/2001/XMLSchema"
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <!-- Extract request body -->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config stream-type="xs:anyURI">
                <include>/request/body</include>
            </config>
        </p:input>
        <p:output name="data" id="request-body"/>
    </p:processor>

    <!-- Dereference URI and return XML -->
    <p:processor name="oxf:url-generator">
        <p:input name="config"
                 href="aggregate('config', aggregate('url', #request-body#xpointer(string(/request/body))))"/>
        <p:output name="data" id="xforms-request"/>
    </p:processor>

    <!-- Run XForms Server -->
    <p:processor name="oxf:xforms-server">
        <p:input name="request" href="#xforms-request" schema-href="xforms-server-request.rng"/>
        <p:output name="response" id="xforms-response"/>
    </p:processor>

    <p:processor name="oxf:xml-converter">
        <p:input name="config">
            <config>
                <!-- Unneeded, and also we don't want to have one if we return an empty document -->
                <omit-xml-declaration>true</omit-xml-declaration>
                <encoding>UTF-8</encoding>
                <indent>false</indent>
            </config>
        </p:input>
        <!-- Do schema validation here -->
        <p:input name="data" href="#xforms-response" schema-href="xforms-server-response.rng"/>
        <p:output name="data" id="converted"/>
    </p:processor>
    <p:processor name="oxf:http-serializer">
        <p:input name="config">
            <config>
                <cache-control>
                    <use-local-cache>false</use-local-cache>
                </cache-control>
            </config>
        </p:input>
        <p:input name="data" href="#converted"/>
    </p:processor>

</p:config>
