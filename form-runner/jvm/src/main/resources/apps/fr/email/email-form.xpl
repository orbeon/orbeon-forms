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
          xmlns:oxf="http://www.orbeon.com/oxf/processors"
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:xs="http://www.w3.org/2001/XMLSchema"
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xmlns:frf="java:org.orbeon.oxf.fr.FormRunner">

    <!-- fr-form-instance -->
    <p:param type="input" name="instance"/>
    <!-- success -->
    <p:param type="output" name="data"/>

    <!-- If data is posted, store as request attribute so that persistence-model.xml picks it up -->
    <!-- 2023-07-25: We don't run XForms here after this, do we? Do we need this then? -->
    <p:choose href="#instance">
        <p:when test="not(/null/@xsi:nil = 'true')">
            <p:processor name="oxf:scope-serializer">
                <p:input name="config">
                    <config>
                        <key>fr-form-data</key>
                        <scope>request</scope>
                    </config>
                </p:input>
                <p:input name="data" href="#instance"/>
            </p:processor>
        </p:when>
    </p:choose>

    <!-- Extract page detail (app, form, document, and mode) from URL -->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/parameters/parameter</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <!-- Extract request parameters (app, form, document, and mode) from URL -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../request-parameters.xpl"/>
        <p:output name="data" id="parameters"/>
    </p:processor>

    <!-- Obtain the form definition -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../detail/read-form.xpl"/>
        <p:input name="instance" href="#parameters"/>
        <p:output name="instance" id="parameters-with-version"/>
        <p:output name="data" id="xhtml-fr-xforms"/>
    </p:processor>

    <!-- Retrieve Form Runner resources -->
    <!-- 2023-07-26: Use `oxf:xforms-submission` so we can do a POST of the form metadata to handle resources overrides.
         See https://github.com/orbeon/orbeon-forms/issues/5833 -->
    <p:processor name="oxf:xforms-submission">
        <p:input name="submission" transform="oxf:unsafe-xslt" href="#parameters-with-version">
            <xf:submission
                xsl:version="2.0"
                xmlns:xf="http://www.w3.org/2002/xforms"
                serialization="application/xml"
                method="post"
                resource="{p:rewrite-service-uri(concat('/fr/service/i18n/fr-resources/', /*/app, '/', /*/form), true())}"/>
        </p:input>
        <p:input name="request" transform="oxf:unsafe-xslt" href="#xhtml-fr-xforms">
            <xsl:stylesheet version="2.0">
                <xsl:template match="/">
                    <xsl:copy-of select="frf:metadataInstanceRootOpt(/*)"/>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="response" id="fr-resources"/>
    </p:processor>

    <!-- Obtain attachment information -->

    <!-- Provide attachments information -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data"       href="#instance"/>
        <p:input name="xhtml"      href="#xhtml-fr-xforms"/>
        <p:input name="parameters" href="#parameters-with-version"/>
        <p:input name="config">
            <attachments xsl:version="2.0">

                <xsl:variable name="data"  select="/*"                   as="element()"/>
                <xsl:variable name="xhtml" select="doc('input:xhtml')/*" as="element(xh:html)"/>

                <!-- App and form -->
                <xsl:variable name="app"  select="doc('input:parameters')/*/app"  as="xs:string"/>
                <xsl:variable name="form" select="doc('input:parameters')/*/form" as="xs:string"/>

                <xsl:variable
                    name="attach-files"
                    select="(p:property(string-join(('oxf.fr.email.attach-files', $app, $form), '.')), 'all')[1]"
                    as="xs:string"/>

                <xsl:if test="$attach-files != 'none'">

                    <xsl:variable
                        name="search-classes"
                        select="
                            string-join(
                                (
                                    'fr-attachment',
                                    if ($attach-files = 'selected') then
                                        'fr-email-attachment'
                                    else
                                        ()
                                ),
                                ' '
                            )"
                        as="xs:string"/>

                    <xsl:variable
                        name="attachment-holders"
                        as="element()*"
                        select="
                            frf:searchHoldersForClassTopLevelOnly($xhtml/xh:body, $data, $search-classes),
                            frf:searchHoldersForClassUseSectionTemplates($xhtml/xh:head, $xhtml/xh:body, $data, $search-classes)
                        "
                    />

                    <xsl:for-each
                        select="
                            $attachment-holders/(
                                self::*[@filename and normalize-space() != ''], (: single attachment    :)
                                _[@filename and normalize-space() != '']        (: multiple attachments :)
                            )">
                        <attachment filename="{@filename}" mediatype="{@mediatype}">
                            <!-- URL may be absolute or already point to persistence layer -->
                            <xsl:value-of select="p:rewrite-service-uri(normalize-space(), true())"/>
                        </attachment>
                    </xsl:for-each>

                </xsl:if>
            </attachments>
        </p:input>
        <p:output name="data" id="attachments"/>
    </p:processor>

    <!-- Build email message -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input  name="data"          href="#instance"/>
        <p:input  name="xhtml"         href="#xhtml-fr-xforms"/>
        <p:input  name="request"       href="#request"/>
        <p:input  name="parameters"    href="#parameters-with-version"/>
        <p:input  name="fr-resources"  href="#fr-resources"/>
        <p:input  name="attachments"   href="#attachments"/>
        <p:input  name="config"        href="email-form.xsl"/>
        <p:output name="data"          id="message"/>
    </p:processor>

    <!-- Obtain PDF data for attachment -->
    <!-- This is read only if oxf.fr.email.attach-pdf is true -->
    <p:processor name="oxf:url-generator">
        <p:input name="config" transform="oxf:unsafe-xslt" href="#request">
            <config xsl:version="2.0">
                <!--<url><xsl:value-of select="p:rewrite-service-uri(/*/parameters/parameter[name = 'pdf']/value)"/></url>-->
                <url><xsl:value-of select="p:rewrite-service-uri(/*/parameters/parameter[name = 'pdf']/value, true())"/></url>
                <mode>binary</mode>
                <content-type>application/pdf</content-type>
                <force-content-type>true</force-content-type>
                <always-return-status-code>false</always-return-status-code>
            </config>
        </p:input>
        <p:output name="data" id="form-pdf"/>
    </p:processor>

    <!-- Obtain TIFF data for attachment -->
    <!-- This is read only if oxf.fr.email.attach-tiff is true -->
    <p:processor name="oxf:url-generator">
        <p:input name="config" transform="oxf:unsafe-xslt" href="#request">
            <config xsl:version="2.0">
                <url><xsl:value-of select="p:rewrite-service-uri(/*/parameters/parameter[name = 'tiff']/value, true())"/></url>
                <mode>binary</mode>
                <content-type>image/tiff</content-type>
                <force-content-type>true</force-content-type>
                <always-return-status-code>false</always-return-status-code>
            </config>
        </p:input>
        <p:output name="data" id="form-tiff"/>
    </p:processor>

    <!-- Convert form data for attachment -->
    <p:processor name="oxf:xml-converter">
        <p:input name="data" href="#instance"/>
        <p:input name="config"><config/></p:input>
        <p:output name="data" id="form-xml"/>
    </p:processor>

    <!-- Connect attachments and send email -->
    <p:processor name="oxf:pipeline">
        <!-- Inputs to forward -->
        <p:input name="message"   href="#message"/>
        <p:input name="form-xml"  href="#form-xml"/>
        <p:input name="form-pdf"  href="#form-pdf"/>
        <p:input name="form-tiff" href="#form-tiff"/>
        <!-- Dynamically generate pipeline to dereference attachments and send email -->
        <p:input name="config" href="aggregate('root', #attachments, #parameters-with-version)" transform="oxf:unsafe-xslt">
            <p:config xsl:version="2.0">
                <!-- Forwarded inputs -->
                <p:param type="input" name="message"/>
                <p:param type="input" name="form-xml"/>
                <p:param type="input" name="form-pdf"/>
                <p:param type="input" name="form-tiff"/>
                <!-- Iterate over attachments -->
                <xsl:for-each select="/*/attachments/attachment">
                    <xsl:variable name="uri" select="." as="xs:string"/>
                    <!-- Create one URL generator per attachment -->
                    <p:processor name="oxf:url-generator">
                        <p:input name="config">
                            <config>
                                <url><xsl:value-of select="$uri"/></url>
                                <!-- Force binary content type, as the data is stored that way -->
                                <content-type>application/octet-stream</content-type>
                                <force-content-type>true</force-content-type>

                                <header>
                                    <name>Orbeon-Form-Definition-Version</name>
                                    <value><xsl:value-of select="/*/request/form-version"/></value>
                                </header>
                                <always-return-status-code>false</always-return-status-code>
                            </config>
                        </p:input>
                        <p:output name="data" id="attachment-{position()}"/>
                    </p:processor>
                </xsl:for-each>
                <!-- Send the email -->
                <p:processor name="oxf:email">
                    <!-- The instance contains the email message -->
                    <p:input name="data" href="#message"/>
                    <!-- The attachment contains the XML document -->
                    <p:input name="form-xml" href="#form-xml"/>
                    <!-- The attachment contains the PDF document -->
                    <p:input name="form-pdf" href="#form-pdf"/>
                    <!-- The attachment contains the TIFF document -->
                    <p:input name="form-tiff" href="#form-tiff"/>
                    <!-- Create one extra input per attachment -->
                    <xsl:for-each select="/*/attachments/attachment">
                        <p:input name="attachment-{position()}" href="#attachment-{position()}"/>
                    </xsl:for-each>
                </p:processor>
            </p:config>
        </p:input>
    </p:processor>

    <!-- If something goes wrong, an exception will be thrown before we get here -->
    <p:processor name="oxf:identity">
        <p:input name="data">
            <status>success</status>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
