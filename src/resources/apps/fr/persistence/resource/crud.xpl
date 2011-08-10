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
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
        xmlns:sql="http://orbeon.org/oxf/xml/sql"
        xmlns:odt="http://orbeon.org/oxf/xml/datatypes"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xforms="http://www.w3.org/2002/xforms"
        xmlns:ev="http://www.w3.org/2001/xml-events">

    <p:param type="input" name="instance"/>

    <!-- NOTE: It's disappointing that we have to use oxf:request/oxf:perl5-matcher rather than using the page flow
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

    <p:processor name="oxf:perl5-matcher">
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
            <!-- Discriminate based on the HTTP method and content type -->
            <p:choose href="#request">

                <!-- Binary PUT -->
                <p:when test="/*/method = 'PUT' and not(/*/content-type = ('application/xml', 'text/xml') or ends-with(/*/content-type, '+xml'))">

                    <!-- TODO: fix oxf:url-serializer to support binary documents -->

                </p:when>
                <!-- XML PUT -->
                <p:when test="/*/method = 'PUT'">

                    <p:processor name="oxf:url-serializer">
                        <p:input name="config" transform="oxf:xslt" href="#matcher-groups">
                            <config>
                                <url><xsl:value-of select="concat('oxf:/forms/', /*/group[1])"/></url>
                            </config>
                        </p:input>
                        <p:input name="data" href="#instance"/>
                    </p:processor>
                </p:when>
            </p:choose>

            <!-- TODO: provide a response? -->

        </p:otherwise>
    </p:choose>

</p:config>
