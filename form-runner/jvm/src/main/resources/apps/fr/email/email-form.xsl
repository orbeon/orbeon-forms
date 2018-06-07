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
<xsl:stylesheet
    xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xh="http://www.w3.org/1999/xhtml"
    xmlns:frf="java:org.orbeon.oxf.fr.FormRunner"
    xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
    xmlns:metadata="java:org.orbeon.oxf.fr.FormRunnerMetadata"

    version="2.0">

    <xsl:variable name="data"         select="/*"                          as="element()"/>
    <xsl:variable name="xhtml"        select="doc('input:xhtml')/*"        as="element(xh:html)"/>
    <xsl:variable name="request"      select="doc('input:request')/*"      as="element(request)"/>
    <xsl:variable name="fr-resources" select="doc('input:fr-resources')/*" as="element(resources)"/>
    <xsl:variable name="attachments"  select="doc('input:attachments')/*"  as="element(attachments)"/>

    <xsl:variable name="metadata"     select="frf:metadataInstanceRootOpt($xhtml)" as="element(metadata)"/>

    <!-- Language requested -->
    <xsl:variable
        name="request-language"
        select="$request/parameters/parameter[name = 'fr-language']/value"
        as="xs:string"/>

    <!-- App and form -->
    <xsl:variable name="app"  select="doc('input:parameters')/*/app"  as="xs:string"/>
    <xsl:variable name="form" select="doc('input:parameters')/*/form" as="xs:string"/>

    <xsl:function name="fr:find-emails" as="xs:string*">

        <xsl:param name="class-name"    as="xs:string"/>
        <xsl:param name="property-name" as="xs:string"/>

        <xsl:sequence
            select="
                distinct-values(
                    for $holder in
                        (
                            frf:searchHoldersForClassTopLevelOnly       (                $xhtml/xh:body, $data, $class-name),
                            frf:searchHoldersForClassUseSectionTemplates($xhtml/xh:head, $xhtml/xh:body, $data, $class-name),
                            p:property(string-join(($property-name, $app, $form), '.'))
                        )
                    return
                        for $raw-email in
                            tokenize(
                                $holder,
                                '(,|\s)\s*'
                            )
                        return
                            normalize-space($raw-email)[. != '']
                )
            "
        />
    </xsl:function>

    <xsl:template match="/">
        <message>

            <xsl:variable
                name="from-email-addresses"
                as="xs:string*"
                select="fr:find-emails('fr-email-sender', 'oxf.fr.email.from')[1]"/>

            <xsl:variable
                name="to-email-addresses"
                as="xs:string*"
                select="fr:find-emails('fr-email-recipient', 'oxf.fr.email.to')"/>

            <xsl:variable
                name="cc-email-addresses"
                as="xs:string*"
                select="fr:find-emails('fr-email-cc', 'oxf.fr.email.cc')"/>

            <xsl:variable
                name="bcc-email-addresses"
                as="xs:string*"
                select="fr:find-emails('fr-email-bcc', 'oxf.fr.email.bcc')"/>

            <!-- Find `fr-email-subject` at the top-level and in section templates -->
            <xsl:variable
                name="subject-values"
                as="xs:string*"
                select="
                    distinct-values(
                        for $holder in
                            (
                                frf:searchHoldersForClassTopLevelOnly       (                $xhtml/xh:body, $data, 'fr-email-subject'),
                                frf:searchHoldersForClassUseSectionTemplates($xhtml/xh:head, $xhtml/xh:body, $data, 'fr-email-subject')
                            )
                        return
                            normalize-space($holder)[. != '']
                    )
                "
            />

            <!-- SMTP outgoing server settings -->
            <smtp-host>
                <xsl:value-of select="p:property(string-join(('oxf.fr.email.smtp.host', $app, $form), '.'))"/>
            </smtp-host>
            <xsl:variable name="port" select="p:property(string-join(('oxf.fr.email.smtp.port', $app, $form), '.'))"/>
            <xsl:if test="normalize-space($port)">
                <smtp-port><xsl:value-of select="$port"/></smtp-port>
            </xsl:if>
            <encryption>
                <xsl:value-of select="p:property(string-join(('oxf.fr.email.smtp.encryption', $app, $form), '.'))"/>
            </encryption>
            <credentials>
                <username>
                    <xsl:value-of select="p:property(string-join(('oxf.fr.email.smtp.username', $app, $form), '.'))"/>
                </username>
                <password>
                    <xsl:value-of select="p:property(string-join(('oxf.fr.email.smtp.credentials', $app, $form), '.'))"/>
                </password>
            </credentials>

            <!-- Sender -->
            <!-- Take the first one only, see https://serverfault.com/questions/554520 -->
            <xsl:for-each select="$from-email-addresses[1]">
                <from>
                    <email>
                        <xsl:value-of select="."/>
                    </email>
                </from>
            </xsl:for-each>
            <!-- Recipients -->
            <xsl:for-each select="$to-email-addresses">
                <to>
                    <email>
                        <xsl:value-of select="."/>
                    </email>
                </to>
            </xsl:for-each>
            <xsl:for-each select="$cc-email-addresses">
                <cc>
                    <email>
                        <xsl:value-of select="."/>
                    </email>
                </cc>
            </xsl:for-each>
            <xsl:for-each select="$bcc-email-addresses">
                <bcc>
                    <email>
                        <xsl:value-of select="."/>
                    </email>
                </bcc>
            </xsl:for-each>
            <!-- Subject -->
            <subject>
                <xsl:choose>
                    <xsl:when test="count($subject-values) > 0">
                        <!-- Append subject values to static subject, comma-separated -->
                        <xsl:value-of select="concat($fr-resources/resource[@xml:lang = $request-language]/email/subject, ' ', string-join($subject-values, ', '))"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <!-- Just put static subject -->
                        <xsl:value-of select="$fr-resources/resource[@xml:lang = $request-language]/email/subject"/>
                    </xsl:otherwise>
                </xsl:choose>
            </subject>
            <!-- Multipart body -->
            <body content-type="multipart/related">
                <!-- Email body -->
                <part name="text" content-type="text/plain">

                    <xsl:choose>
                        <xsl:when test="exists($metadata/email/body/template) and empty($metadata/email/body/fr:param)">
                            <!-- Just a template -->
                            <xsl:value-of select="$metadata/email/body/template[@xml:lang = $request-language]"/>
                        </xsl:when>
                        <xsl:when test="exists($metadata/email/body/fr:param)">
                            <!-- Parameters to a template -->
                            <xsl:value-of
                                xmlns:map="http://www.w3.org/2005/xpath-functions/map"
                                xmlns:saxon="http://saxon.sf.net/"
                                select="
                                    p:process-template(
                                        (
                                            $metadata/email/body/template[@xml:lang = $request-language],
                                            $fr-resources/resource[@xml:lang = $request-language]/email/body
                                        )[1],
                                        'en',
                                        map:merge(
                                            for $p in $metadata/email/body/fr:param
                                            return
                                                map:entry(
                                                    $p/fr:name,
                                                    if ($p/@type = 'ExpressionParam') then
                                                        string(($data/saxon:evaluate($p/fr:expr))[1])   (: NOTE: Form Runner function library os not in scope. :)
                                                    else if ($p/@type = 'ControlValueParam') then
                                                        error()                                         (: TODO: Not implemented yet. :)
                                                    else if ($p/@type = 'AllControlValuesParam') then
                                                        metadata:findAllControlsWithValues()
                                                    else
                                                        error()
                                                )
                                        )
                                    )
                                "/>
                        </xsl:when>
                        <xsl:otherwise>
                            <!-- Use the body from resources or properties -->
                            <xsl:value-of select="$fr-resources/resource[@xml:lang = $request-language]/email/body"/>
                        </xsl:otherwise>
                    </xsl:choose>
                </part>
                <!-- XML, PDF and TIFF attachments if needed -->
                <xsl:for-each select="'xml', 'pdf', 'tiff'">

                    <xsl:variable name="type" select="."/>

                    <xsl:if test="p:property(string-join((concat('oxf.fr.email.attach-', $type), $app, $form), '.'))">

                        <xsl:variable
                            name="filename"
                            select="(frf:emailAttachmentFilename($data, $type, $app, $form), concat('form.', $type))[1]"/>

                        <part
                            name="form-{$type}"
                            content-type="{if ($type = 'tiff') then 'image/tiff' else concat('application/', $type)}"
                            content-disposition="attachment; filename=&quot;{$filename}&quot;"
                            src="input:form-{$type}"/>
                    </xsl:if>
                </xsl:for-each>

                <!-- Other attachments if needed -->
                <xsl:for-each select="$attachments/attachment">
                    <part
                        name="attachment-{position()}"
                        content-type="{@mediatype}"
                        content-disposition="attachment; filename=&quot;{@filename}&quot;"
                        src="input:attachment-{position()}"/>
                </xsl:for-each>
            </body>
        </message>

    </xsl:template>
</xsl:stylesheet>
