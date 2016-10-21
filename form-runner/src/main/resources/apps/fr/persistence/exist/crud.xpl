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
        xmlns:sql="http://orbeon.org/oxf/xml/sql"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xf="http://www.w3.org/2002/xforms"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:exist="http://exist.sourceforge.net/NS/exist"
        xmlns:saxon="http://saxon.sf.net/"
        xmlns:frf="java:org.orbeon.oxf.fr.FormRunner">

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
                <include>/request/headers/header[name = 'orbeon-exist-uri']</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <!-- Matches form definitions, form data and attachments -->
    <p:processor name="oxf:regexp">
        <p:input name="config"><config>/fr/service/exist/crud((?:/([^/]+)/([^/]+))/(?:(?:form)/[^/]+|((data)/[^/]+)/[^/]+))</config></p:input>
        <p:input name="data" href="#request#xpointer(/request/request-path)"/>
        <p:output name="data" id="matcher-groups"/>
    </p:processor>

    <!-- Matches a form data collection (for DELETE) -->
    <p:processor name="oxf:regexp">
        <p:input name="config"><config>/fr/service/exist/crud/(([^/]+)/([^/]+)/(data)/)</config></p:input>
        <p:input name="data" href="#request#xpointer(/request/request-path)"/>
        <p:output name="data" id="delete-matcher-groups"/>
    </p:processor>

    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#request"/>
        <p:input name="matcher-groups" href="#matcher-groups"/>
        <p:input name="delete-matcher-groups" href="#delete-matcher-groups"/>
        <p:input name="config">
            <request-description xsl:version="2.0">
                <xsl:variable name="matcher-groups"        select="doc('input:matcher-groups')/*/group"/>
                <xsl:variable name="delete-matcher-groups" select="doc('input:delete-matcher-groups')/*/group"/>

                <xsl:variable name="headers"               select="/request/headers/header"/>
                <xsl:variable name="exist-base-uri"        select="($headers[name = 'orbeon-exist-uri']/value)[1]/string()"/>
                <xsl:variable name="path"                  select="($matcher-groups[1], $delete-matcher-groups[1])[1]/string()"/>

                <xsl:copy-of select="/request/*"/>

                <exist-base-uri><xsl:value-of select="$exist-base-uri"/></exist-base-uri>

                <xsl:for-each select="p:username()">
                    <username><xsl:value-of select="."/></username>
                    <groupname><xsl:value-of select="p:user-group()"/></groupname>
                    <xsl:for-each select="p:user-organizations()">
                        <organization><xsl:value-of select="."/></organization>
                    </xsl:for-each>
                </xsl:for-each>
                <!-- /collection used for data only -->
                <xsl:if test="exists($matcher-groups)">
                    <collection><xsl:value-of select="string-join($matcher-groups[position() = (2, 3, 4)], '/')"/></collection>
                </xsl:if>
                <resource-uri>
                    <xsl:value-of select="
                        string-join(
                            (
                                if (ends-with($exist-base-uri, '/')) then
                                    substring($exist-base-uri, 1, string-length($exist-base-uri) - 1)
                                else
                                    $exist-base-uri,
                                if (starts-with($path, '/')) then
                                    substring($path, 2)
                                else
                                    $path
                            ),
                            '/'
                        )
                    "/>
                </resource-uri>
                <for-data><xsl:value-of select="$matcher-groups[5] = 'data' or $delete-matcher-groups[4] = 'data'"/></for-data>
                <app><xsl:value-of select="($matcher-groups[2], $delete-matcher-groups[2])[1]"/></app>
                <form><xsl:value-of select="($matcher-groups[3], $delete-matcher-groups[3])[1]"/></form>
            </request-description>
        </p:input>
        <p:output name="data" id="request-description"/>
    </p:processor>

    <!--
        1. We only do what follows when this is an operation on form data (not form definition).
        2. Run XQuery that:
           1. Return `<data-exists>true|false</data-exists>` telling us if there is an existing `data.xml`
              in the collection.
           2. Store `metadata.xml` when all the following conditions are met:
              a. We have a username/password to store (i.e. the user is authenticated).
              b. We have a PUT.
              c. The "current" directory is empty, i.e. this is the first time we store something there.
           3. If we're not storing a `metadata.xml`, return the existing `metadata.xml` if there is one.
    -->
    <p:choose href="#request-description">
        <p:when test="/*/for-data = 'true'">

            <p:processor name="oxf:xforms-submission">
                <p:input name="request" transform="oxf:xslt" href="#request-description">
                    <request xsl:version="2.0" xsl:exclude-result-prefixes="#all">
                        <xsl:copy-of select="/*/(exist-base-uri | collection | username | groupname | organization | method)"/>
                        <exist:query>
                            <exist:text>

                                declare variable $frPath       := request:get-path-info();
                                declare variable $collection   := request:get-parameter('collection'  , '');
                                declare variable $username     := request:get-parameter('username'    , '');
                                declare variable $groupname    := request:get-parameter('groupname'   , '');
                                declare variable $organization := request:get-parameter('organization', ());
                                declare variable $method       := request:get-parameter('method'      , '');

                                declare variable $path         := concat(
                                                                      $frPath,
                                                                      if (ends-with($frPath, '/')) then '' else '/',
                                                                      $collection
                                                                  );
                                declare function local:createPath($parts, $count) {
                                    concat('/', string-join(subsequence($parts, 1, $count), '/'))
                                };

                                let $dataExists :=
                                    let $dataURI := concat($path, '/data.xml')
                                    return element data-exists { doc-available($dataURI) }

                                let $existingMetadata :=

                                    if ($username != '' and $method = 'PUT' and not(xmldb:collection-available($path))) then

                                        (: Create the collection, since it doesn't exist :)
                                        let $dummy :=
                                            let $parts := tokenize($path, '/')[. != '']
                                            return for $i in 2 to count($parts) return
                                                let $partialPath := local:createPath($parts, $i)
                                                return if (xmldb:collection-available($partialPath)) then () else
                                                    xmldb:create-collection(local:createPath($parts, $i - 1), $parts[$i])

                                        (: Store metadata.xml :)
                                        let $metadata :=
                                                element metadata {
                                                    element username     { $username  },
                                                    element groupname    { $groupname },
                                                    if (exists($organization)) then
                                                        element organization {
                                                            for $level in $organization return
                                                                element level { $level }
                                                        }
                                                    else ()
                                                }
                                        let $dummy := xmldb:store($path, 'metadata.xml', $metadata)
                                        return ()

                                    else

                                        (: Since we're not creating a metadata.xml, return it if it exists :)
                                        let $metadataURI := concat($path, '/metadata.xml')
                                        return if (doc-available($metadataURI)) then doc($metadataURI) else ()

                                return ($dataExists, $existingMetadata)

                            </exist:text>
                        </exist:query>
                    </request>
                </p:input>
                <p:input name="submission">
                    <xf:submission method="post"
                                   ref="/*/exist:query"
                                   resource="{/*/exist-base-uri
                                                }?collection={    /*/collection
                                                }&amp;username={  /*/username
                                                }&amp;groupname={ /*/groupname
                                                }&amp;method={    /*/method
                                                }{
                                                    string-join(
                                                        for    $organization
                                                        in     /*/organization
                                                        return concat('&amp;organization=', $organization),
                                                        ''
                                                    )
                                                }">
                        <xi:include href="exist-submission-common.xml" xpointer="xpath(/root/*)"/>
                    </xf:submission>
                </p:input>
                <p:output name="response" id="exist-result"/>
            </p:processor>

            <p:processor name="oxf:unsafe-xslt">
                <p:input name="data"><dummy/></p:input>
                <p:input name="exist-result" href="#exist-result"/>
                <p:input name="request-description" href="#request-description"/>
                <p:input name="config">
                    <xsl:stylesheet version="2.0">
                        <xsl:template match="/">
                            <root>
                                <xsl:variable name="request-description" select="doc('input:request-description')/request-description"/>
                                <xsl:variable name="exist-result" select="doc('input:exist-result')/exist:result"/>
                                <xsl:copy-of select="ep:checkPermissions($request-description/app,
                                                                         $request-description/form,
                                                                         $exist-result/metadata,
                                                                         $exist-result/data-exists = 'true',
                                                                         $request-description/method)"
                                        xmlns:ep="org.orbeon.oxf.fr.persistence.existdb.Permissions"/>
                            </root>
                        </xsl:template>
                    </xsl:stylesheet>
                </p:input>
                <p:output name="data" id="check-permissions-and-set-header"/>
            </p:processor>
            <p:processor name="oxf:null-serializer">
                <p:input name="data" href="#check-permissions-and-set-header"/>
            </p:processor>
        </p:when>
    </p:choose>

    <!-- Discriminate based on the HTTP method and content type -->
    <p:choose href="#request-description">
        <!-- Handle binary and XML GET -->
        <p:when test="/*/method = 'GET'">

            <!-- Read URL -->
            <p:processor name="oxf:url-generator">
                <p:input name="config" transform="oxf:unsafe-xslt" href="#request-description">
                    <config xsl:version="2.0">
                        <url>
                            <xsl:value-of select="p:rewrite-service-uri(/*/resource-uri, true())"/>
                        </url>
                        <!-- Produce binary so we do our own XML parsing -->
                        <mode>binary</mode>
                        <xsl:for-each select="'Orbeon-Username', 'Orbeon-Group', 'Orbeon-Roles'">
                            <xsl:variable name="name" select="."/>
                            <xsl:for-each select="p:get-request-header($name)">
                                <header>
                                    <name><xsl:value-of select="$name"/></name>
                                    <value><xsl:value-of select="."/></value>
                                </header>
                            </xsl:for-each>
                        </xsl:for-each>
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

                    <p:processor name="oxf:xforms-submission">
                        <p:input name="submission">
                            <!-- NOTE: The <body> element contains the xs:anyURI type -->
                            <xf:submission ref="/*/body" method="put" replace="none"
                                    serialization="application/octet-stream"
                                    resource="{/*/resource-uri}">
                                <xi:include href="exist-submission-common.xml" xpointer="xpath(/root/*)"/>
                            </xf:submission>
                        </p:input>
                        <p:input name="request" href="#request-description"/>
                        <p:output name="response" id="response"/>
                    </p:processor>

                </p:when>
                <!-- DELETE -->
                <p:when test="/*/method = 'DELETE'">

                    <p:processor name="oxf:xforms-submission">
                        <p:input name="submission">
                            <xf:submission method="delete" replace="none" serialization="none"
                                    resource="{/*/resource-uri}">
                                <xi:include href="exist-submission-common.xml" xpointer="xpath(/root/*)"/>
                            </xf:submission>
                        </p:input>
                        <p:input name="request" href="#request-description"/>
                        <p:output name="response" id="response"/>
                    </p:processor>

                </p:when>
                <!-- XML PUT -->
                <p:when test="/*/method = 'PUT'">

                    <p:processor name="oxf:xforms-submission">
                        <p:input name="submission">
                            <xf:submission ref="/*/*[1]" method="put" replace="none"
                                    resource="{/*/request-description/resource-uri}">
                                <xi:include href="exist-submission-common.xml" xpointer="xpath(/root/*)"/>
                            </xf:submission>
                        </p:input>
                        <p:input name="request" href="aggregate('root', #instance, #request-description)"/>
                        <p:output name="response" id="response"/>
                    </p:processor>
                </p:when>
            </p:choose>

            <!-- Convert and serialize to XML -->
            <p:processor name="oxf:xml-converter">
                <p:input name="config">
                    <config>
                        <indent>false</indent>
                        <encoding>utf-8</encoding>
                    </config>
                </p:input>
                <p:input name="data" href="#response"/>
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
