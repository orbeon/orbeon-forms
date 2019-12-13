<!--
    Copyright (C) 2008 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<p:config
    xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param type="input" name="instance"/>

    <!-- NOTE: It's disappointing that we have to use oxf:request/oxf:regexp rather than using the page flow
         directly, but because we want to support the PUT and POST methods, this is currently the only solution. -->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config stream-type="xs:anyURI">
                <include>/request/request-path</include>
                <include>/request/content-type</include>
                <include>/request/method</include>
                <include>/request/body</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <p:processor name="oxf:regexp">
        <p:input name="config"><config>/fr/service/resource/crud/([^/]+/[^/]+/(form/[^/]+|data/[^/]+/[^/]+))</config></p:input>
        <p:input name="data" href="#request#xpointer(/request/request-path)"/>
        <p:output name="data" id="matcher-groups"/>
    </p:processor>

    <p:choose href="#request">
        <!-- Handle binary and XML GET -->
        <p:when test="/*/method = 'GET'">
            <!-- Read URL -->
            <p:processor name="oxf:url-generator">
                <p:input name="config" transform="oxf:unsafe-xslt" href="#matcher-groups">
                    <config xsl:version="2.0">
                        <url><xsl:value-of select="concat('oxf:/forms/', /*/group[1])"/></url>
                        <mode>binary</mode>
                    </config>
                </p:input>
                <p:output name="data" id="document"/>
            </p:processor>

            <!-- Serialize out as is -->
            <p:processor name="oxf:http-serializer">
                <p:input name="config">
                    <config>
                        <cache-control>
                            <use-local-cache>false</use-local-cache>
                        </cache-control>
                    </config>
                </p:input>
                <p:input name="data" href="#document"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <!-- Unsupported -->
        </p:otherwise>
    </p:choose>

</p:config>
