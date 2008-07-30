<?xml version="1.0" encoding="utf-8"?>
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
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:pipeline="java:org.orbeon.oxf.processor.pipeline.PipelineFunctionLibrary">

    <p:param type="input" name="instance" debug="oracle instance"/>

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
        <p:output name="data" id="request" debug="oracle request"/>
    </p:processor>
    <p:processor name="oxf:perl5-matcher">
        <p:input name="config"><config>/fr/service/oracle/crud/([^/]+)/([^/]+)/(form|data)/([^/]+)/([^/]+)</config></p:input>
        <p:input name="data" href="#request#xpointer(/request/request-path)"/>
        <p:output name="data" id="matcher-groups" debug="matcher-groups"/>
    </p:processor>

    <!-- Build a document with a description of the request -->
    <p:processor name="oxf:xslt">
        <p:input name="matcher-groups" href="#matcher-groups"/>
        <p:input name="instance" href="#instance"/>
        <p:input name="data"><dummy/></p:input>
        <p:input name="config">
            <request xsl:version="2.0">
                <xsl:variable name="matcher-groups" as="element(group)+" select="doc('input:matcher-groups')/result/group"/>
                <document>
                    <xsl:copy-of select="doc('input:instance')"/>
                </document>
                <timestamp><xsl:value-of select="current-dateTime()"/></timestamp>
                <app><xsl:value-of select="$matcher-groups[1]"/></app>
                <form><xsl:value-of select="$matcher-groups[2]"/></form>
                <type><xsl:value-of select="$matcher-groups[3]"/></type>
                <document-id><xsl:value-of select="$matcher-groups[4]"/></document-id>
                <filename><xsl:value-of select="$matcher-groups[5]"/></filename>
            </request>
        </p:input>
        <p:output name="data" id="request-description" debug="request-description"/>
    </p:processor>

    <p:processor name="oxf:null-serializer">
        <p:input name="data" href="#request-description"/>
    </p:processor>

    <!-- Discriminate based on the HTTP method and content type -->
    <p:choose href="#request">
        <!-- Handle binary and XML GET -->
        <p:when test="/*/method = 'GET'">

            <p:processor name="oxf:sql">
                <p:input name="data" href="#request-description"/>
                <p:input name="config" transform="oxf:unsafe-xslt" href="#instance">
                    <sql:config xsl:version="2.0">
                        <sql:connection>
                            <sql:datasource>
                                <xsl:value-of select="pipeline:property('oxf.fr.persistence.service.oracle.datasource')"/>
                            </sql:datasource>

                            <sql:execute>
                                <sql:query>
                                    select t.xml.getClobVal() xml from orbeon_form_data t
                                        where app = <sql:param type="xs:string" select="/request/app"/>
                                        and form = <sql:param type="xs:string" select="/request/form"/>
                                        and document_id = <sql:param type="xs:string" select="/request/document-id"/>
                                </sql:query>
                                <sql:result-set>
                                    <sql:row-iterator>
                                        <sql:get-column-value column="xml" type="odt:xmlFragment"/>
                                    </sql:row-iterator>
                                </sql:result-set>
                            </sql:execute>
                        </sql:connection>
                    </sql:config>
                </p:input>
                <p:output name="data" id="document"/>
            </p:processor>

            <!-- Convert and serialize to XML -->
            <p:processor name="oxf:xml-converter">
                <p:input name="config">
                    <config>
                        <indent>false</indent>
                        <encoding>utf-8</encoding>
                    </config>
                </p:input>
                <p:input name="data" href="#document"/>
                <p:output name="data" id="converted"/>
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
                <p:input name="data" href="#converted"/>
            </p:processor>

        </p:when>
        <p:otherwise>
            <!-- Discriminate based on the HTTP method and content type -->
            <p:choose href="#request">

                <!-- Binary PUT -->
                <p:when test="/*/method = 'PUT' and not(/*/content-type = ('application/xml', 'text/xml') or ends-with(/*/content-type, '+xml'))">

                    <!--<p:processor name="oxf:xforms-submission">-->
                        <!--<p:input name="submission">-->
                            <!-- NOTE: The <body> element contains the xs:anyURI type -->
                            <!--<xforms:submission ref="/*/body" method="put" replace="none"-->
                                    <!--serialization="application/octet-stream"-->
                                    <!--resource="{xxforms:property('oxf.fr.persistence.service.exist.uri')}/{/*/group[1]}">-->
                                <!--<xforms:action ev:event="xforms-submit-error">-->
                                    <!-- TODO: Propagate error to caller -->
                                    <!--<xforms:delete while="/*/*" nodeset="/*/*"/>-->
                                    <!--<xforms:setvalue ref="/*" value="event('response-body')"/>-->
                                    <!--<xforms:message level="xxforms:log-error"><xforms:output value="event('response-body')"/></xforms:message>-->
                                <!--</xforms:action>-->
                            <!--</xforms:submission>-->
                        <!--</p:input>-->
                        <!--<p:input name="request" href="aggregate('root', #request#xpointer(/*/body), #matcher-groups#xpointer(/*/group))"/>-->
                        <!--<p:output name="response" id="response"/>-->
                    <!--</p:processor>-->

                </p:when>
                <!-- DELETE -->
                <p:when test="/*/method = 'DELETE'">

                    <p:processor name="oxf:sql">
                        <p:input name="data" href="#request-description"/>
                        <p:input name="config" transform="oxf:unsafe-xslt" href="#instance">
                            <sql:config xsl:version="2.0">
                                <result>
                                    <sql:connection>
                                        <sql:datasource>
                                            <xsl:value-of select="pipeline:property('oxf.fr.persistence.service.oracle.datasource')"/>
                                        </sql:datasource>
                                        <sql:execute>
                                            <sql:update>
                                                delete from orbeon_form_data where
                                                    app = <sql:param type="xs:string" select="/request/app"/>
                                                    and form = <sql:param type="xs:string" select="/request/form"/>
                                                    and document_id = <sql:param type="xs:string" select="/request/document-id"/>
                                            </sql:update>
                                        </sql:execute>
                                    </sql:connection>
                                </result>
                            </sql:config>
                        </p:input>
                    </p:processor>

                </p:when>
                <!-- XML PUT -->
                <p:when test="/*/method = 'PUT'">

                    <p:processor name="oxf:sql">
                        <p:input name="data" href="#request-description"/>
                        <p:input name="config" transform="oxf:unsafe-xslt" href="#instance">
                            <sql:config xsl:version="2.0">
                                <result>
                                    <sql:connection>
                                        <sql:datasource>
                                            <xsl:value-of select="pipeline:property('oxf.fr.persistence.service.oracle.datasource')"/>
                                        </sql:datasource>
                                        <sql:execute>
                                            <sql:update>
                                                declare
                                                    row_exists number;
                                                begin
                                                    <!-- Check if we already have a row for this document -->
                                                    select count(*) into row_exists from orbeon_form_data where
                                                        app = <sql:param type="xs:string" select="/request/app"/>
                                                        and form = <sql:param type="xs:string" select="/request/form"/>
                                                        and document_id = <sql:param type="xs:string" select="/request/document-id"/>;
                                                    if row_exists = 0 then
                                                        <!-- If we don't, insert one -->
                                                        insert into orbeon_form_data (created, last_modified, app, form, document_id, xml) values (
                                                            <sql:param type="xs:dateTime" select="/request/timestamp"/>,
                                                            <sql:param type="xs:dateTime" select="request/timestamp"/>,
                                                            <sql:param type="xs:string" select="/request/app"/>,
                                                            <sql:param type="xs:string" select="/request/form"/>,
                                                            <sql:param type="xs:string" select="/request/document-id"/>,
                                                            XMLType(<sql:param type="odt:xmlFragment" sql-type="clob" select="/request/document/*"/>)
                                                        );
                                                    else
                                                        <!-- If we do, update it -->
                                                        update orbeon_form_data
                                                        set xml = XMLType(<sql:param type="odt:xmlFragment" sql-type="clob" select="/request/document/*"/>),
                                                            last_modified = <sql:param type="xs:dateTime" select="/request/timestamp"/>
                                                        where app = <sql:param type="xs:string" select="/request/app"/>
                                                            and form = <sql:param type="xs:string" select="/request/form"/>
                                                            and document_id = <sql:param type="xs:string" select="/request/document-id"/>;
                                                    end if;
                                                end;
                                            </sql:update>
                                        </sql:execute>
                                    </sql:connection>
                                </result>
                            </sql:config>
                        </p:input>
                    </p:processor>

                </p:when>
            </p:choose>

            <p:processor name="oxf:xml-converter">
                <p:input name="config">
                    <config>
                        <indent>false</indent>
                        <encoding>utf-8</encoding>
                    </config>
                </p:input>
                <p:input name="data"><dummy/></p:input>
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
        </p:otherwise>

    </p:choose>

</p:config>