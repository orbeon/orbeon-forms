<p:config
    xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
    xmlns:xh="http://www.w3.org/1999/xhtml">

    <!-- Page detail (app, form, document, and mode) -->
    <p:param type="input" name="instance"/>
    <!-- Compiled form -->
    <p:param type="output" name="data"/>

    <!-- Extract request parameters (app, form, document, and mode) from URL -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../request-parameters.xpl"/>
        <p:output name="data" id="parameters"/>
    </p:processor>

    <!-- Unroll the form (theme, components, inclusions) -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../unroll-form.xpl"/>
        <p:input name="instance" href="#parameters"/>
        <p:input name="data" href="#instance"/>
        <p:output name="data" id="unrolled-form-definition"/>
    </p:processor>

    <p:processor name="fr:compiler">
        <p:input  name="instance" href="#parameters"/>
        <p:input  name="data" href="#unrolled-form-definition"/>
        <p:output name="data" id="zip-data"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="data"       href="#zip-data"/>
        <p:input name="parameters" href="#parameters"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                <xsl:template match="/">
                    <xh:html>
                        <xh:head>
                            <xh:meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=yes"/>
                            <xh:script src="/xforms-server/baseline.js?updates=offline" defer="defer"/>
                            <xh:link rel="stylesheet" href="/xforms-server/baseline.css?updates=offline"/>
                            <xh:script>
                                window.addEventListener('DOMContentLoaded', function() {
                                   window.ORBEON.fr.FormRunnerOffline.renderFormFromBase64(
                                       document.querySelector(".orbeon"),
                                       document.querySelector("#fr-compiled-form").content.textContent,
                                       {
                                            "appName"    : "<xsl:value-of select="doc('input:parameters')/*/app"/>",
                                            "formName"   : "<xsl:value-of select="doc('input:parameters')/*/form"/>",
                                            "formVersion": 1,
                                            "mode"       : "test"
                                       }
                                   );
                                });
                            </xh:script>
                        </xh:head>
                        <xh:body style="padding: 2em">
                            <xh:div class="orbeon"/>
                            <xh:template id="fr-compiled-form">
                                <xh:div><xsl:value-of select="/*"/></xh:div>
                            </xh:template>
                            <xh:div class="orbeon-loader loader loader-default is-active" data-text="Loading…"/>
                        </xh:body>
                    </xh:html>
                </xsl:template>

            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
