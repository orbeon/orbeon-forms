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
        xmlns:saxon="http://saxon.sf.net/"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xpl="java:org.orbeon.oxf.pipeline.api.FunctionLibrary"
        xmlns:xf="http://www.w3.org/2002/xforms"
        xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:f="http//www.orbeon.com/function"
        xmlns:frf="java:org.orbeon.oxf.fr.FormRunner">

    <!--
        Search instance, e.g.:

        <search>
            <query>free text search</query>
            <query name="title" path="details/title" type="xs:string" control="input">title</query>
            <query name="author" path="details/author" type="xs:string" control="input">author</query>
            <query name="language" path="details/language" type="xs:string" control="select1">en</query>
            <page-size>5</page-size>
            <page-number>1</page-number>
            <lang>en</lang>
        </search>
    -->
    <p:param name="instance" type="input"/>

    <!--
        Search result, e.g.:

        <documents total="2" search-total="2" page-size="5" page-number="1" query="">
            <document created="2008-07-28T18:43:59.363-07:00" last-modified="2008-07-28T18:43:59.363-07:00" name="329737EA7637FAC1BC8FD6139BDFAE97">
                <details>
                    <detail>a</detail>
                    <detail>aa</detail>
                    <detail/>
                </details>
            </document>
            <document created="2008-07-25T17:31:19.016-07:00" last-modified="2008-07-25T17:34:33.503-07:00" name="C7AEA87DA2CC07680D97BC28A87EBAF5">
                <details>
                    <detail>ab</detail>
                    <detail>ba</detail>
                    <detail/>
                </details>
            </document>
        </documents>
    -->
    <p:param name="data" type="output"/>

    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../common-search.xpl"/>
        <p:input name="search" href="#instance"/>
        <p:output name="search" id="search-input"/>
    </p:processor>

    <p:processor name="oxf:request">
        <p:input name="config">
            <config stream-type="xs:anyURI">
                <include>/request/headers/header[name = 'orbeon-datasource']</include>
                <include>/request/headers/header[name = 'orbeon-username']</include>
                <include>/request/headers/header[name = 'orbeon-group']</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#search-input"/>
        <p:input name="request" href="#request"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0">
                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>

                <!-- Annotate with username/group, so this info is available to SQL processor -->
                <xsl:template match="/search">
                    <xsl:variable name="headers" select="doc('input:request')/request/headers/header"/>
                    <xsl:copy>
                        <xsl:attribute name="orbeon-username" select="$headers[name = 'orbeon-username']/value/string()"/>
                        <xsl:attribute name="orbeon-group"    select="$headers[name = 'orbeon-group']/value/string()"/>
                        <xsl:apply-templates select="@* | node()"/>
                    </xsl:copy>
                </xsl:template>

                <!-- Support long prefixes for backward compatibility -->
                <xsl:template match="query/@path">
                    <!-- Depends on the database:
                         - In MySQL matching is done on the prefix (not the namespace URI), so for MySQL we create XPath
                           with "expression with the xh: prefix" | "expression with the xhtml: prefix"
                         - On Oracle and DB2, we can use any prefix we want, and the database goes by the associated
                           namespace URI; here we normalize the request to always use the xh prefix, so we later only
                           need to define the namespace for that prefix -->
                    <xsl:attribute name="path" select="
                        if (/search/provider = 'mysql') then
                            if (starts-with(., 'xh:')) then
                                concat('/*/', ., ' | /*/', replace(replace(., 'xh:', 'xhtml:'), 'xf:', 'xforms:'))
                            else if (starts-with(., 'xhtml:')) then
                                concat('/*/', ., ' | /*/', replace(replace(., 'xhtml:', 'xh:'), 'xforms:', 'xf:'))
                            else concat('/*/', .)
                        else
                            if (starts-with(., 'xhtml:')) then
                                concat('/*/', replace(replace(., 'xhtml:', 'xh:'), 'xforms:', 'xf:'))
                            else concat('/*/', .)"/>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="search"/>
    </p:processor>

    <p:processor name="oxf:xforms-submission">
        <p:input name="request"><dummy/></p:input>
        <p:input name="submission" transform="oxf:xslt" href="#search-input">
            <xf:submission xsl:version="2.0" method="get" replace="instance"
                               resource="/fr/service/persistence/form/{encode-for-uri(/search/app)}/{encode-for-uri(/search/form)}"/>
        </p:input>
        <p:output name="response" id="form-metadata"/>
    </p:processor>

    <!-- Run query -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="form-metadata" href="#form-metadata"/>
        <p:input name="data" href="#search"/>
        <p:input name="request" href="#request"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0">

                <xsl:import href="sql-utils.xsl"/>

                <xsl:variable name="include-drafts"           as="xs:boolean" select="(empty(/search/drafts) or /search/drafts = ('include', 'only')) and /search/@orbeon-username != ''"/>
                <xsl:variable name="include-non-drafts"       as="xs:boolean" select="(empty(/search/drafts) or /search/drafts = ('include', 'exclude'))"/>

                <xsl:variable name="permissions"                select="doc('input:form-metadata')/forms/form/permissions"/>
                <xsl:variable name="search-operations"          select="('*', 'read', 'update', 'delete')"/>
                <xsl:variable name="search-permissions"         select="$permissions/permission[p:split(@operations) = $search-operations]"/>

                <!-- Are we authorized to see all the data based because of our role? -->
                <xsl:variable name="operations-from-role"       select="frf:authorizedOperationsBasedOnRoles($permissions)"/>
                <xsl:variable name="authorized-based-on-role"   select="$operations-from-role = $search-operations"/>

                <!-- Are we authorized to see data if we are the owner / group member? -->
                <xsl:variable name="authorized-if-owner"        select="exists($search-permissions[owner])"/>
                <xsl:variable name="authorized-if-group-member" select="exists($search-permissions[group-member])"/>

                <xsl:template match="/">

                    <sql:config>
                        <documents>
                            <sql:connection>
                                <sql:datasource><xsl:value-of select="doc('input:request')/request/headers/header[name = 'orbeon-datasource']/value/string() treat as xs:string"/></sql:datasource>

                                <!-- Query that returns all the search results, which we will reuse in multiple places -->
                                <xsl:variable name="query">
                                    select
                                        d.created, d.last_modified_time, d.document_id, d.xml, d.username, d.groupname, d.draft,
                                        <xsl:if test="/search/provider = ('oracle', 'postgresql', 'db2', 'sqlserver')">row_number() over (order by d.created desc) row_number</xsl:if>
                                        <xsl:if test="/search/provider = 'mysql'">
                                            <!-- MySQL lacks row_number, see http://stackoverflow.com/a/1895127/5295 -->
                                            @rownum := @rownum + 1 row_number
                                        </xsl:if>
                                    from
                                    (
                                        <xsl:if test="$include-non-drafts">
                                            (
                                                select data.*
                                                from orbeon_form_data data,
                                                    (
                                                        select max(last_modified_time) last_modified_time, app, form, document_id
                                                        from orbeon_form_data
                                                        where
                                                            app = <sql:param type="xs:string" select="/search/app"/>
                                                            and form = <sql:param type="xs:string" select="/search/form"/>
                                                            and draft = 'N'
                                                        group by app, form, document_id
                                                    ) latest
                                                where
                                                    <!-- Merge with 'latest', to make sure we only consider the document with the most recent last_date -->
                                                    data.last_modified_time = latest.last_modified_time
                                                    and data.app = latest.app
                                                    and data.form = latest.form
                                                    and data.document_id = latest.document_id
                                                    and data.deleted = 'N'
                                                    and data.draft = 'N'
                                                    <xsl:copy-of select="f:search-conditions(/)"/>
                                                    <xsl:copy-of select="f:owner-group-condition('data')"/>
                                            )
                                        </xsl:if>
                                        <xsl:if test="$include-non-drafts and $include-drafts">
                                            union all
                                        </xsl:if>
                                        <xsl:if test="$include-drafts">
                                            (
                                                select *
                                                from orbeon_form_data d1
                                                where
                                                    app = <sql:param type="xs:string" select="/search/app"/>
                                                    and form = <sql:param type="xs:string" select="/search/form"/>
                                                    and draft = 'Y'
                                                    <xsl:if test="exists(/search/drafts/@for-document-id)">
                                                        and document_id = <sql:param type="xs:string" select="/search/drafts/@for-document-id"/>
                                                    </xsl:if>
                                                    <xsl:if test="/search/drafts/@for-never-saved-document = 'true'">
                                                        and
                                                        (
                                                            select count(*)
                                                            from orbeon_form_data d2
                                                            where
                                                                d2.app = <sql:param type="xs:string" select="/search/app"/>
                                                                and d2.form = <sql:param type="xs:string" select="/search/form"/>
                                                                and d2.draft = 'N'
                                                                and d2.document_id = d1.document_id
                                                        ) = 0
                                                    </xsl:if>
                                                    <xsl:copy-of select="f:search-conditions(/)"/>
                                                    <xsl:copy-of select="f:owner-group-condition('d1')"/>
                                            )
                                        </xsl:if>
                                    ) d
                                    <xsl:if test="/search/provider = 'mysql'">
                                        , (select @rownum := 0) r
                                        ORDER BY d.created desc
                                    </xsl:if>
                                </xsl:variable>

                                <!-- Get total number of document in collection for this app/form
                                     - the count includes drafts, whether returned or not, except for anonymous users, who can't use the draft functionality  -->
                                <sql:execute>
                                    <sql:query>
                                        SELECT
                                            (
                                                SELECT count(*)
                                                FROM   orbeon_form_data d,
                                                       (
                                                           SELECT
                                                               app, form, document_id,
                                                               max(last_modified_time) last_modified_time, draft
                                                           FROM orbeon_form_data
                                                           WHERE
                                                               app = <sql:param type="xs:string" select="/search/app"/>
                                                               and form = <sql:param type="xs:string" select="/search/form"/>
                                                               <xsl:if test="/search/@orbeon-username = ''"> and draft = 'N'</xsl:if>
                                                           GROUP BY app, form, document_id, draft
                                                       ) m
                                                WHERE  d.app = m.app
                                                       AND d.form = m.form
                                                       AND d.document_id = m.document_id
                                                       AND d.last_modified_time = m.last_modified_time
                                                       AND d.draft = m.draft
                                                       AND d.deleted = 'N'
                                                       <xsl:copy-of select="f:owner-group-condition('d')"/>
                                            ) total,
                                            (
                                                SELECT count(*)
                                                FROM   (<xsl:copy-of select="$query"/>) a
                                            ) search_total
                                        <xsl:if test="/search/provider = ('mysql', 'oracle')">FROM dual</xsl:if>
                                        <xsl:if test="/search/provider = 'db2'">FROM sysibm.sysdummy1</xsl:if>
                                    </sql:query>
                                    <sql:result-set>
                                        <sql:row-iterator>
                                            <sql:get-columns format="xml"/>
                                        </sql:row-iterator>
                                    </sql:result-set>
                                </sql:execute>

                                <!-- Get details -->
                                <sql:execute>
                                    <sql:query>
                                        SELECT created, last_modified_time, document_id, username, groupname, draft
                                            <!-- Go over detail columns and extract data from XML -->
                                            <xsl:for-each select="/search/query[@path]">
                                                <xsl:choose>
                                                    <xsl:when test="/search/provider = 'mysql'      ">, extractValue(xml, '<xsl:value-of select="f:escape-sql(f:escape-lang(@path, /*/lang))"/>')</xsl:when>
                                                    <xsl:when test="/search/provider = 'postgresql' ">, (xpath('<xsl:value-of select="f:escape-sql(f:escape-lang(@path, /*/lang))"/>/text()', xml, <xsl:value-of select="f:postgresql-namespaces(f:namespaces(., @path))"/>))[1]</xsl:when>
                                                    <xsl:when test="/search/provider = 'oracle'     ">, extractValue(xml, '<xsl:value-of select="f:escape-sql(f:escape-lang(@path, /*/lang))"/>', '<xsl:value-of select="f:oracle-namespaces(f:namespaces(., @path))"/>')</xsl:when>
                                                    <xsl:when test="/search/provider = 'db2'        ">, XMLQUERY('declare namespace xh="http://www.w3.org/1999/xhtml";declare namespace xf="http://www.w3.org/2002/xforms";$XML<xsl:value-of select="f:escape-sql(f:escape-lang(@path, /*/lang))"/>/text()')</xsl:when>
                                                    <!-- Even when there is just one value, SQL Server seems to want the (…)[1]; see http://stackoverflow.com/a/1302199/5295 -->
                                                    <xsl:when test="/search/provider = 'sqlserver'  ">, xml.value('<xsl:value-of select="f:db2-sqlserver-namespaces(f:namespaces(., @path))"/> (<xsl:value-of select="f:escape-sql(f:escape-lang(@path, /*/lang))"/>)[1]', 'nvarchar(255)')</xsl:when>
                                                </xsl:choose>
                                                detail_<xsl:value-of select="position()"/>
                                            </xsl:for-each>
                                        FROM
                                            (
                                                SELECT  *
                                                FROM    (
                                                            <xsl:copy-of select="$query"/>
                                                        ) a
                                                WHERE   row_number
                                                        <xsl:variable name="start-offset-zero-based" select="(/search/page-number - 1) * /search/page-size"/>
                                                        BETWEEN <xsl:value-of select="$start-offset-zero-based + 1"/>
                                                        AND     <xsl:value-of select="$start-offset-zero-based + /search/page-size"/>
                                            ) a
                                    </sql:query>
                                    <sql:result-set>
                                        <sql:row-iterator>
                                            <document>
                                                <created><sql:get-column-value column="created"/></created>
                                                <last-modified><sql:get-column-value column="last_modified_time"/></last-modified>
                                                <document-id><sql:get-column-value column="document_id"/></document-id>
                                                <username><sql:get-column-value column="username"/></username>
                                                <groupname><sql:get-column-value column="groupname"/></groupname>
                                                <draft><sql:get-column-value column="draft"/></draft>
                                                <xsl:for-each select="/search/query[@path]">
                                                    <detail><sql:get-column-value column="detail_{position()}"/></detail>
                                                </xsl:for-each>
                                            </document>
                                        </sql:row-iterator>
                                    </sql:result-set>
                                </sql:execute>
                            </sql:connection>
                        </documents>
                    </sql:config>
                </xsl:template>
                <!-- Condition on owner / group -->
                <xsl:function name="f:owner-group-condition">
                    <xsl:param name="table" as="xs:string"/>
                    <!-- We should 403 if we're not authorized based on role and don't have any owner permission, see:
                         https://github.com/orbeon/orbeon-forms/issues/1383#issuecomment-36953739 -->
                    <xsl:if test="not($authorized-based-on-role)">
                        and (
                            <xsl:if test="$authorized-if-owner"><xsl:value-of select="$table"/>.username = <sql:param type="xs:string" select="/search/@orbeon-username"/></xsl:if>
                            <xsl:if test="$authorized-if-owner and $authorized-if-group-member"> or </xsl:if>
                            <xsl:if test="$authorized-if-group-member"><xsl:value-of select="$table"/>.groupname = <sql:param type="xs:string" select="/search/@orbeon-group"/></xsl:if>
                        )
                    </xsl:if>
                </xsl:function>
                <!-- Search conditions -->
                <xsl:function name="f:search-conditions">
                    <xsl:param name="search" as="document-node()"/>
                    <!-- Conditions on searchable columns -->
                    <xsl:for-each select="$search/search/query[@path and normalize-space() != '']">
                        <xsl:choose>
                            <xsl:when test="@match = 'exact'">
                                <!-- Exact match -->
                                <xsl:choose>
                                    <xsl:when test="$search/search/provider = 'mysql'">
                                        and extractValue(xml, '<xsl:value-of select="f:escape-sql(f:escape-lang(@path, /*/lang))"/>') = '<xsl:value-of select="f:escape-sql(.)"/>'
                                    </xsl:when>
                                    <xsl:when test="$search/search/provider = 'postgresql'">
                                        and (xpath('<xsl:value-of select="f:escape-sql(f:escape-lang(@path, /*/lang))"/>', xml, <xsl:value-of select="f:postgresql-namespaces(f:namespaces(., @path))"/>))[1]::text = '<xsl:value-of select="f:escape-sql(.)"/>'
                                    </xsl:when>
                                    <xsl:when test="$search/search/provider = 'oracle'">
                                        and extractValue(xml, '<xsl:value-of select="f:escape-sql(f:escape-lang(@path, /*/lang))"/>', '<xsl:value-of select="f:oracle-namespaces(f:namespaces(., @path))"/>') = '<xsl:value-of select="f:escape-sql(.)"/>'
                                    </xsl:when>
                                    <xsl:when test="$search/search/provider = 'db2'">
                                        and XMLEXISTS ('<xsl:value-of select="f:db2-sqlserver-namespaces(f:namespaces(., @path))"/> $XML/*[<xsl:value-of select="f:escape-sql(f:escape-lang(@path, /*/lang))"/> = "<xsl:value-of select="f:escape-sql(.)"/>"]')
                                    </xsl:when>
                                    <xsl:when test="$search/search/provider = 'sqlserver'">
                                        and xml.value('<xsl:value-of select="f:db2-sqlserver-namespaces(f:namespaces(., @path))"/> (<xsl:value-of select="f:escape-sql(f:escape-lang(@path, /*/lang))"/>)[1]', 'nvarchar(255)') = '<xsl:value-of select="f:escape-sql(.)"/>'
                                    </xsl:when>
                                </xsl:choose>
                            </xsl:when>
                            <xsl:otherwise>
                                <!-- Substring -->
                                <xsl:choose>
                                    <xsl:when test="$search/search/provider = 'mysql'">
                                        and lower(extractValue(xml, '<xsl:value-of select="f:escape-sql(f:escape-lang(@path, /*/lang))"/>')) like '%<xsl:value-of select="lower-case(f:escape-sql(.))"/>%'
                                    </xsl:when>
                                    <xsl:when test="$search/search/provider = 'postgresql'">
                                        and lower((xpath('<xsl:value-of select="f:escape-sql(f:escape-lang(@path, /*/lang))"/>/text()', xml, <xsl:value-of select="f:postgresql-namespaces(f:namespaces(., @path))"/>))[1]::text) like '%<xsl:value-of select="lower-case(f:escape-sql(.))"/>%'
                                    </xsl:when>
                                    <xsl:when test="$search/search/provider = 'oracle'">
                                        and lower(extractValue(xml, '<xsl:value-of select="f:escape-sql(f:escape-lang(@path, /*/lang))"/>', '<xsl:value-of select="f:oracle-namespaces(f:namespaces(., @path))"/>')) like '%<xsl:value-of select="lower-case(f:escape-sql(.))"/>%'
                                    </xsl:when>
                                    <xsl:when test="$search/search/provider = 'db2'">
                                        and XMLEXISTS('<xsl:value-of select="f:db2-sqlserver-namespaces(f:namespaces(., @path))"/> $XML/*[contains(lower-case(<xsl:value-of select="f:escape-sql(f:escape-lang(@path, /*/lang))"/>),"<xsl:value-of select="f:escape-sql(lower-case(.))"/>")]')
                                    </xsl:when>
                                    <xsl:when test="$search/search/provider = 'sqlserver'">
                                        and xml.value('<xsl:value-of select="f:db2-sqlserver-namespaces(f:namespaces(., @path))"/> (<xsl:value-of select="f:escape-sql(f:escape-lang(@path, /*/lang))"/>)[1]', 'nvarchar(255)') like '%<xsl:value-of select="f:escape-sql(.)"/>%'
                                    </xsl:when>
                                </xsl:choose>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:for-each>
                    <!-- Condition for free text search -->
                    <xsl:if test="$search/search/query[empty(@path) and normalize-space() != '']">
                        <xsl:choose>
                            <xsl:when test="$search/search/provider = 'mysql'">
                                and xml like <sql:param type="xs:string" select="concat('%', /search/query[not(@path)], '%')"/>
                            </xsl:when>
                            <xsl:when test="$search/search/provider = 'postgresql'">
                                and xml::text ilike '<xsl:value-of select="f:escape-sql(concat('%', replace($search/search/query[not(@path)], '_', '\\_'), '%'))"/>'
                            </xsl:when>
                            <xsl:when test="$search/search/provider = 'oracle'">
                                and contains(xml, '<xsl:value-of select="f:escape-sql(concat('%', replace($search/search/query[not(@path)], '_', '\\_'), '%'))"/>') > 0
                            </xsl:when>
                            <xsl:when test="$search/search/provider = 'db2'">
                                and xmlexists('$XML//*[contains(upper-case(text()), upper-case($textSearch))]' passing CAST( <sql:param type="xs:string" select="concat('', /search/query[not(@path)], '')"/> AS VARCHAR(2000)) as "textSearch")
                            </xsl:when>
                            <xsl:when test="$search/search/provider = 'sqlserver'">
                                <!-- We don't do a substring search, at apparently this isn't supported by the full-text index.
                                     http://social.msdn.microsoft.com/Forums/sqlserver/en-US/02ebc411-0fcf-40ee-9963-3f64ed4409bb -->
                                and contains(xml, <sql:param type="xs:string" select="/search/query[not(@path)]"/>)
                            </xsl:when>
                        </xsl:choose>
                    </xsl:if>
                </xsl:function>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="sql-config"/>
    </p:processor>
    <p:processor name="oxf:sql">
        <p:input name="data" href="#search"/>
        <p:input name="config" href="#sql-config"/>
        <p:output name="data" id="sql-output"/>
    </p:processor>

    <!-- Transform output from SQL processor into the XML form the caller expects -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#sql-output"/>
        <p:input name="form-metadata" href="#form-metadata"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0">
                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>

                <xsl:variable name="permissions" select="doc('input:form-metadata')/forms/form/permissions"/>

                <!-- Move total and search-total as attribute -->
                <xsl:template match="documents">
                    <documents total="{total}" search-total="{search-total}">
                        <xsl:apply-templates select="document"/>
                    </documents>
                </xsl:template>

                <!-- Move created, last-modified, and name as attributes -->
                <!-- Add wrapping details element -->
                <xsl:template match="document">
                    <document created="{created}"
                              last-modified="{last-modified}"
                              draft="{if (draft = 'Y') then 'true' else if (draft = 'N') then 'false' else ''}"
                              name="{document-id}"
                              operations="{frf:xpathAllAuthorizedOperations($permissions, string(username), string(groupname))}">
                        <details>
                            <xsl:for-each select="detail">
                                <detail>
                                    <xsl:value-of select="."/>
                                </detail>
                            </xsl:for-each>
                        </details>
                    </document>
                </xsl:template>

            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>


</p:config>
