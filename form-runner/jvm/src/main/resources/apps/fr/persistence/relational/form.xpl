<!--
  Copyright (C) 2011 Orbeon, Inc.

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
    xmlns:sql="http://orbeon.org/oxf/xml/sql"
    xmlns:odt="http://orbeon.org/oxf/xml/datatypes"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param name="instance" type="input"/>
    <p:param name="data"     type="output"/>

    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/request-path</include>
                <include>/request/headers/header[name = 'orbeon-datasource']</include>
                <include>/request/parameters/parameter[name = 'all-versions']</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <p:processor name="oxf:regexp">
        <p:input name="config"><config>/fr/service/(oracle|mysql|postgresql|db2|sqlserver)/form(/([^/]+)(/([^/]+))?)?</config></p:input>
        <p:input name="data" href="#request#xpointer(/request/request-path)"/>
        <p:output name="data" id="matcher-groups"/>
    </p:processor>

    <p:processor name="oxf:unsafe-xslt">
        <p:input name="matcher-groups" href="#matcher-groups"/>
        <p:input name="instance" href="#instance"/>
        <p:input name="request" href="#request"/>
        <p:input name="data"><dummy/></p:input>
        <p:input name="config">
            <request xsl:version="2.0">
                <xsl:variable name="request"        as="element(request)" select="doc('input:request')/request"/>
                <xsl:variable name="matcher-groups" as="element(group)+"  select="doc('input:matcher-groups')/result/group"/>

                <sql:datasource><xsl:value-of select="$request/headers/header[name = 'orbeon-datasource']/value/string()"/></sql:datasource>
                <xsl:copy-of select="doc('input:instance')/request/*"/>

                <provider><xsl:value-of select="$matcher-groups[1]"/></provider>
                <all-versions><xsl:value-of select="$request/parameters/parameter[name = 'all-versions']/value = 'true'"/></all-versions>
            </request>
        </p:input>
        <p:output name="data" id="request-description"/>
    </p:processor>

    <!-- Get the metadata section of each form -->
    <p:processor name="oxf:sql">
        <p:input name="data" href="#request-description"/>
        <p:input name="config" transform="oxf:unsafe-xslt" href="#request-description">
            <sql:config xsl:version="2.0">
                <sql-out>
                    <sql:connection>
                        <xsl:copy-of select="/request/sql:datasource"/>
                        <sql:execute>
                            <sql:query>
                                SELECT
                                  d.app                application_name,
                                  d.form               form_name,
                                  d.form_metadata      form_metadata,
                                  d.form_version       form_version,
                                  d.last_modified_time last_modified_time
                                FROM
                                  orbeon_form_definition d,
                                  (
                                    SELECT
                                      app,
                                      form,
                                      form_version,
                                      MAX(last_modified_time) last_modified_time
                                    FROM
                                      orbeon_form_definition
                                    <xsl:if test="/request/app != ''">
                                      WHERE
                                        app = <sql:param type="xs:string" select="/request/app"/>
                                        <xsl:if test="/request/form != ''">
                                          AND form = <sql:param type="xs:string" select="/request/form"/>
                                        </xsl:if>
                                    </xsl:if>
                                    GROUP BY
                                      app, form, form_version
                                  ) app_form_version_last_time
                                WHERE
                                  d.app                = app_form_version_last_time.app                AND
                                  d.form               = app_form_version_last_time.form               AND
                                  d.form_version       = app_form_version_last_time.form_version       AND
                                  d.last_modified_time = app_form_version_last_time.last_modified_time AND
                                  d.deleted = 'N'
                            </sql:query>
                            <sql:result-set>
                                <sql:row-iterator>
                                    <row>
                                        <application-name><sql:get-column-value column="application_name"/></application-name>
                                        <form-name><sql:get-column-value column="form_name"/></form-name>
                                        <last-modified-time><sql:get-column-value column="last_modified_time" type="xs:dateTime"/></last-modified-time>
                                        <form-version><sql:get-column-value column="form_version"/></form-version>
                                        <sql:get-column-value column="form_metadata" type="odt:xmlFragment"/>
                                    </row>
                                </sql:row-iterator>
                            </sql:result-set>
                        </sql:execute>
                    </sql:connection>
                </sql-out>
            </sql:config>
        </p:input>
        <p:output name="data" id="sql-out"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="data"                href="#sql-out"/>
        <p:input name="request-description" href="#request-description"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0" exclude-result-prefixes="#all">

                <xsl:template match="/">
                    <forms>
                        <xsl:choose>
                            <!-- Unless we have `?all-versions=true`, only keep the highest version per app/form -->
                            <!-- We should probably filter in SQL, but it makes the query very complex, so for now we do it in XSLT -->
                            <xsl:when test="doc('input:request-description')/*/all-versions = 'false'">
                                <xsl:for-each-group select="/*/row" group-by="concat(application-name, '|', form-name)">
                                    <xsl:variable name="max-form-version" select="max(current-group()/form-version/xs:integer(.))"/>
                                    <xsl:apply-templates select="current-group()[form-version = $max-form-version]"/>
                                </xsl:for-each-group>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:apply-templates select="/*/row"/>
                            </xsl:otherwise>
                        </xsl:choose>
                    </forms>
                </xsl:template>

                <xsl:template match="row">
                    <form>
                        <xsl:copy-of select="* except metadata" copy-namespaces="no"/>
                        <xsl:copy-of select="metadata/(* except (application-name, form-name))" copy-namespaces="no"/>
                    </form>
                </xsl:template>

            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
