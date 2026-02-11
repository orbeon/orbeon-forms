<p:config
    xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
>
    <p:param type="input"  name="instance"/>          <!-- Parameters: `app`, `form`, `form-version`, `document`, `mode` -->
    <p:param type="input"  name="data"/>              <!-- XHTML+FR+XForms                                               -->
    <p:param type="output" name="data"/>              <!-- XHTML+XForms                                                  -->
    <p:param type="output" name="form-runner-config"/><!-- Form Runner configuration                                     -->

    <!-- Apply project-specific theme -->
    <p:choose href="#instance">
        <p:when test="doc-available(concat('oxf:/forms/', /*/app, '/theme.xsl'))">
            <p:processor name="oxf:url-generator">
                <p:input name="config" href="aggregate('config', aggregate('url', #instance#xpointer(concat(
                                                'oxf:/forms/', /*/app, '/theme.xsl'))))"/>
                <p:output name="data" id="theme"/>
            </p:processor>
            <p:processor name="oxf:xslt">
                <p:input name="data" href="#data"/>
                <p:input name="config" href="#theme"/>
                <p:output name="data" id="themed-data"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <p:processor name="oxf:identity">
                <p:input name="data" href="#data"/>
                <p:output name="data" id="themed-data"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

    <p:processor name="fr:form-runner-config">
        <p:input  name="data"               href="#themed-data"/>
        <p:input  name="params"             href="#instance"/>
        <p:output name="data"               id="raw-form-definition"/>
        <p:output name="form-runner-config" id="form-runner-config" ref="form-runner-config"/>
    </p:processor>

    <!-- Provide section template implementation -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="config">
            <xsl:transform
                version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema">

                <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>
                <xsl:import href="components/section-templates.xsl"/>

                <!-- Used by `section-templates.xsl` -->
                <xsl:variable
                    name="implement-section-templates"
                    as="xs:boolean"
                    select="true()"/>
            </xsl:transform>
        </p:input>
        <p:input  name="data"               href="#raw-form-definition"/>
        <p:output name="data"               id="after-section-templates"/>
    </p:processor>

    <p:processor name="oxf:unsafe-xslt">
        <p:input  name="config"             href="components/components.xsl"/>
        <p:input  name="data"               href="#after-section-templates"/>
        <p:input  name="form-runner-config" href="#form-runner-config"/>
        <p:output name="data"               id="after-components"/>

        <!-- This is here just so that we can reload the form when the properties or the resources change -->
        <p:input name="properties-xforms"       href="oxf:/config/properties-xforms.xml"/>
        <p:input name="properties-form-runner"  href="oxf:/config/properties-form-runner.xml"/>
        <p:input name="properties-form-builder" href="oxf:/config/properties-form-builder.xml"/>
        <p:input name="properties-local"        href="oxf:/config/properties-local.xml"/>
    </p:processor>

    <!-- Handle XInclude -->
    <p:processor name="oxf:xinclude">
        <p:input name="config" href="#after-components"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
