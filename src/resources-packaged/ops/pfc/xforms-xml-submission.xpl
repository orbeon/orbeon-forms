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
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <p:param name="default-submission" type="input"/>
    <p:param name="matcher-result" type="input"/>
    <p:param name="setvalues" type="input"/>
    <p:param name="instance" type="output"/>

    <!-- Check content type -->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config stream-type="xs:anyURI">
                <include>/request/container-type</include>
                <include>/request/content-type</include>
                <include>/request/method</include>
                <include>/request/parameters</include>
            </config>
        </p:input>
        <!--<p:output name="data" id="request-info" debug="xxxxml-submission"/>-->
        <p:output name="data" id="request-info"/>
    </p:processor>

    <p:choose href="#request-info">
        <!-- Check for noscript mode form post OR script form post for replace="all" -->
        <!-- NOTE: In portlet mode, the method and content-type are not available (not sure why), so just assume checking for the content type is enough -->
        <p:when test="(
                        (
                          lower-case(/*/method) = 'post' and (/*/content-type = 'application/x-www-form-urlencoded' or starts-with(/*/content-type, 'multipart/form-data'))
                        ) or /*/container-type = 'portlet'
                      ) and (
                        /*/parameters/parameter[name = '$noscript']/value = 'true' or /*/parameters/parameter[name = '$server-events']
                      )">
            <!-- Process submission -->
            <p:processor name="oxf:pipeline">
                <p:input name="config" href="/ops/xforms/xforms-server-submit.xpl"/>
            </p:processor>
            <!-- No submission and no default submission, return special null document to say that all processing has been handled -->
            <p:processor name="oxf:identity">
                <p:input name="data">
                    <!-- The PFC knows about this one, and that's kind of a HACK -->
                    <bypass xsi:nil="true"/>
                </p:input>
                <p:output name="data" ref="instance"/>
            </p:processor>
        </p:when>
        <!-- Check for XML post -->
        <!-- NOTE: In portlet mode, the method is not available, so just assume checking for the content type is enough -->
        <!-- We check the content-type for application/xml and text/xml using a starts-with to handle the
             case where a charset specified as part of the content-type  -->
        <p:when test="(lower-case(/*/method) = ('post', 'put') or /*/container-type = 'portlet')
                        and (starts-with(/*/content-type, 'application/xml')
                             or starts-with(/*/content-type, 'text/xml')
                             or ends-with(/*/content-type, '+xml'))">

            <!-- Extract request body -->
            <p:processor name="oxf:request">
                <p:input name="config">
                    <config stream-type="xs:anyURI">
                        <include>/request/body</include>
                    </config>
                </p:input>
                <p:output name="data" id="request-body"/>
            </p:processor>

            <p:choose href="#request-body">
                <p:when test="/request/body != ''">
                    <!-- Case where this could happen: 1) error occurs 2) temporary body file destroyed with
                         PipelineContext 3) this called to handle error pipeline -->

                    <!-- Dereference URI and return XML instance -->
                    <p:processor name="oxf:url-generator">
                        <p:input name="config" href="aggregate('config', aggregate('url', #request-body#xpointer(string(/request/body))))"/>
                        <p:output name="data" id="raw-instance"/>
                    </p:processor>

                    <!-- Update instance if needed -->
                    <p:choose href="#setvalues">
                        <p:when test="/null/@xsi:nil = 'true'">
                            <!-- No updates to perform on the instance -->
                            <p:processor name="oxf:identity">
                                <p:input name="data" href="#raw-instance"/>
                                <p:output name="data" ref="instance"/>
                            </p:processor>
                        </p:when>
                        <p:otherwise>
                            <!-- Updates to do on the instance -->
                            <p:processor name="oxf:unsafe-xslt">
                                <p:input name="data" href="#raw-instance"/>
                                <p:input name="setvalues" href="#setvalues"/>
                                <p:input name="matcher-result" href="#matcher-result"/>
                                <p:input name="request-info" href="#request-info"/>
                                <p:input name="config" href="setvalue-extract.xsl"/>
                                <p:output name="data" ref="instance"/>
                            </p:processor>
                        </p:otherwise>
                    </p:choose>
                </p:when>
                <p:otherwise>
                    <!-- Use a null instance -->
                    <p:processor name="oxf:identity">
                        <p:input name="data">
                            <null xsi:nil="true"/>
                        </p:input>
                        <p:output name="data" ref="instance"/>
                    </p:processor>
                </p:otherwise>
            </p:choose>
        </p:when>
        <p:when test="(lower-case(/*/method) = 'get' or /*/container-type = 'portlet')
                        and /*/parameters/parameter[name = '$instance']">
            <!-- Check for XML GET with $instance parameter -->

            <!-- Decode parameter -->
            <p:processor name="oxf:unsafe-xslt">
                <p:input name="data" href="#request-info"/>
                <p:input name="config">
                    <xsl:stylesheet version="2.0" xmlns:xpl="java:org.orbeon.oxf.pipeline.api.FunctionLibrary">
                        <xsl:template match="/">
                            <xsl:copy-of select="xpl:decodeXML(normalize-space(/*/parameters/parameter[name = '$instance']/value))"/>
                        </xsl:template>
                    </xsl:stylesheet>
                </p:input>
                <p:output name="data" id="raw-instance"/>
            </p:processor>

            <!-- Update instance if needed -->
            <p:choose href="#setvalues">
                <p:when test="/null/@xsi:nil = 'true'">
                    <!-- No updates to perform on the instance -->
                    <p:processor name="oxf:identity">
                        <p:input name="data" href="#raw-instance"/>
                        <p:output name="data" ref="instance"/>
                    </p:processor>
                </p:when>
                <p:otherwise>
                    <!-- Updates to do on the instance -->
                    <p:processor name="oxf:unsafe-xslt">
                        <p:input name="data" href="#raw-instance"/>
                        <p:input name="setvalues" href="#setvalues"/>
                        <p:input name="matcher-result" href="#matcher-result"/>
                        <p:input name="request-info" href="#request-info"/>
                        <p:input name="config" href="setvalue-extract.xsl"/>
                        <p:output name="data" ref="instance"/>
                    </p:processor>
                </p:otherwise>
            </p:choose>

        </p:when>
        <p:otherwise>
            <p:choose href="#default-submission">
                <p:when test="/null/@xsi:nil = 'true'">
                    <!-- No submission and no default submission, return null document -->
                    <p:processor name="oxf:identity">
                        <p:input name="data">
                            <null xsi:nil="true"/>
                        </p:input>
                        <p:output name="data" ref="instance"/>
                    </p:processor>
                </p:when>
                <p:otherwise>
                    <!-- No submission but there is a default submission which may have to be updated -->
                    <p:processor name="oxf:unsafe-xslt">
                        <p:input name="data" href="#default-submission"/>
                        <p:input name="setvalues" href="#setvalues"/>
                        <p:input name="matcher-result" href="#matcher-result"/>
                        <p:input name="request-info" href="#request-info"/>
                        <p:input name="config" href="setvalue-extract.xsl"/>
                        <p:output name="data" ref="instance"/>
                    </p:processor>
                </p:otherwise>
            </p:choose>
        </p:otherwise>
    </p:choose>

</p:config>
