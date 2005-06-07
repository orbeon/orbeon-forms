<!--
    Copyright (C) 2005 Orbeon, Inc.

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
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <p:param name="instance" type="output"/>

    <!-- Check content type -->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config stream-type="xs:anyURI">
<!--                <include>/request/content-type</include>-->
<!--                <include>/request/method</include>-->
                <include>/request/*</include>
                <exclude>/request/body</exclude>
            </config>
        </p:input>
        <p:output name="data" id="request-content-type" debug="xxxrequest-content-type"/>
    </p:processor>

    <p:choose href="#request-content-type">
        <!-- Check for XML post -->
        <p:when test="lower-case(/*/method) = 'post' and (/*/content-type = ('application/xml', 'text/xml') or ends-with(/*/content-type, '+xml'))">
            <!-- Extract request body -->
            <p:processor name="oxf:request">
                <p:input name="config">
                    <config stream-type="xs:anyURI">
                        <include>/request/body</include>
                    </config>
                </p:input>
                <p:output name="data" id="request-body"/>
            </p:processor>

            <!-- Dereference URI and return XML instance -->
            <p:processor name="oxf:url-generator">
                <p:input name="config" href="aggregate('config', aggregate('url', #request-body#xpointer(string(/request/body))))"/>
                <p:output name="data" ref="instance" debug="xxxxmlsubmission"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <!-- Return null document -->
            <p:processor name="oxf:identity">
                <p:input name="data">
                    <null xsi:nil="true"/>
                </p:input>
                <p:output name="data" ref="instance"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

</p:config>
