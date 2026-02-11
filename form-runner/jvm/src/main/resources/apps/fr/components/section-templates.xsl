<xsl:stylesheet
    version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xf="http://www.w3.org/2002/xforms"
    xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
    xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
    xmlns:xh="http://www.w3.org/1999/xhtml"
    xmlns:xbl="http://www.w3.org/ns/xbl"
    xmlns:xxbl="http://orbeon.org/oxf/xml/xbl"
    xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:frf="java:org.orbeon.oxf.fr.FormRunner"
    xmlns:map="http://www.w3.org/2005/xpath-functions/map"
>

    <!-- Input: <xsl:with-param name="implement-section-templates" as="xs:boolean"/> -->

    <!-- Remove built-in XBL components which older versions of Orbeon Forms might inline by mistake (pre-4.0?). When
         that happened, only simple bindings were supported. See https://github.com/orbeon/orbeon-forms/issues/2395 -->
    <xsl:template match="/xh:html/xh:head/xbl:xbl/xbl:binding[starts-with(@element, 'fr|')]"/>

    <xsl:template
        match="
            /xh:html/xh:head/xbl:xbl/xbl:binding[p:has-class('fr-section-component')]/xbl:implementation[$implement-section-templates] |
                          /*/xbl:xbl/xbl:binding[p:has-class('fr-section-component')]/xbl:implementation[$implement-section-templates]">
        <xsl:variable
            name="binding"
            as="element(xbl:binding)"
            select=".."/>
        <xsl:variable
            name="section-name"
            select="substring-after($binding/@element, 'component|')"/>
        <xsl:variable
            name="model-id"
            select="concat($section-name, '-model')" as="xs:string"/>
        <xsl:variable
             name="xbl-model"
             select="xf:model"/>
        <xsl:copy>
            <xf:model id="{$model-id}">
                <xsl:copy-of select="$xbl-model/(@schema | @xxf:custom-mips)"/>

                <xf:instance id="fr-form-instance" xxbl:mirror="true" xxf:index="id">
                    <_/>
                </xf:instance>

                <xsl:apply-templates
                    select="
                        $xbl-model/(
                            xf:instance  [not(@id = ('fr-form-instance', 'readonly'))]         | (: we create these here :)
                            xf:bind      [@id = 'fr-form-binds']                               |
                            xs:schema                                                          |
                            xf:action    [ends-with(@id, '-binding')]                          |
                            xf:submission[p:classes() = ('fr-service', 'fr-database-service')] |
                            fr:action    [@version = '2018.2']                                 |
                            fr:listener  [@version = '2018.2']
                        )"/>

                <!-- Keep track of whether fields should be readonly because the node we're bound to is readonly -->
                <xf:instance id="readonly"><readonly/></xf:instance>

                <!-- This is also at the top-level in components.xsl -->
                <xf:var name="fr-params" value="xxf:instance('fr-parameters-instance')"/>
                <xf:var name="fr-mode"   value="$fr-params/mode"/>
                <!-- NOTE: Make content readonly at design time too because if the form author enters initial
                     data or adds a repeat iteration into the section template, that data will be either incorrect
                     or lost if the toolbox is reloaded. A better fix can be considered, but more complex at the
                     Form Builder level. -->
                <xf:bind
                    ref="instance('fr-form-instance')"
                    readonly="
                        fr:is-readonly-mode()         or
                        instance('readonly') = 'true' or
                        fr:is-design-time()"/>

                <!-- Section becomes relevant -->
                <xf:action event="xforms-model-construct-done" class="fr-design-time-preserve">
                    <!-- At this point, the outside instance has already been copied inside. If there are no child
                         elements, it means that the data has not yet been copied out (the section could also be
                         empty, but this is not allowed currently). See also:
                         https://github.com/orbeon/orbeon-forms/issues/786 -->
                    <xf:var name="is-empty" value="empty(instance()/*)"/>
                    <!-- If empty, initialize from the template (the mirror copies it out too) -->
                    <xf:action if="$is-empty">
                        <xf:insert
                            context="instance()"
                            origin="instance('fr-form-template')/*"/>
                        <!-- RRR with defaults -->
                        <!-- Force-`<xf:rebuild>` unneeded: structural changes above and `<xf:recalculate>` rebuilds if needed. -->
                        <xf:recalculate xxf:defaults="true"/>
                    </xf:action>
                    <!-- If not empty, update with instance where holes have been filled if necessary -->
                    <xf:action if="not($is-empty)">
                        <xf:var
                            name="simply-migrated"
                            value="
                                migration:dataMaybeWithSimpleMigration(
                                    event('xxf:absolute-targetid'),
                                    instance('fr-form-template'),
                                    instance()
                                )"
                            xmlns:migration="java:org.orbeon.oxf.fr.SimpleDataMigration"/>

                        <xf:action if="exists($simply-migrated)">
                            <xf:delete ref="instance()/*"/>
                            <xf:insert
                                context="instance()"
                                origin="$simply-migrated/*"/>
                            <!-- RRR with defaults -->
                            <!-- Force-`<xf:rebuild>` unneeded: structural changes above and `<xf:recalculate>` rebuilds if needed. -->
                            <xf:recalculate xxf:defaults="true"/>
                        </xf:action>
                    </xf:action>
                </xf:action>

                <!-- Annotate section, grid, iteration, and control elements as initially non-relevant. Then, as the controls become
                     relevant, they will remove the annotations. See https://github.com/orbeon/orbeon-forms/issues/3829. -->
                <xf:action event="xforms-model-construct-done" class="fr-design-time-preserve">
                    <xf:insert
                        iterate="migration:iterateBinds(event('xxf:absolute-targetid'), instance())"
                        context= "."
                        origin= "xf:attribute('fr:relevant', 'false')"
                        xmlns:migration="java:org.orbeon.oxf.fr.SimpleDataMigration"/>
                </xf:action>

                <!--
                    Propagate out relevance, see https://github.com/orbeon/orbeon-forms/issues/3829. We use `xxf:phantom="true"` so
                    we can catch the relevance events for the grid. See https://github.com/orbeon/orbeon-forms/issues/1947. -->
                <xf:insert
                    observer="fr-section-template-view"
                    event="xforms-disabled"
                    xxf:phantom="true"
                    if="(event('xxf:binding') instance of element()) and (event('xxf:binding')/root() is instance('fr-form-instance')/root())"

                    context="event('xxf:binding')"
                    origin="xf:attribute('fr:relevant', 'false')"/>

                <xf:delete
                    observer="fr-section-template-view"
                    event="xforms-enabled"
                    xxf:phantom="true"
                    if="(event('xxf:binding') instance of element()) and (event('xxf:binding')/root() is instance('fr-form-instance')/root())"

                    ref="event('xxf:binding')/@fr:relevant"/>

            </xf:model>
        </xsl:copy>
    </xsl:template>

    <xsl:template
        match="
            /xh:html/xh:head/xbl:xbl/xbl:binding[p:has-class('fr-section-component')]/xbl:template[$implement-section-templates] |
                          /*/xbl:xbl/xbl:binding[p:has-class('fr-section-component')]/xbl:template[$implement-section-templates]">
        <xsl:variable
            name="binding"
            as="element(xbl:binding)"
            select=".."/>
        <xsl:variable
            name="binding-id"
            select="
                if (ends-with($binding/@id, '-component')) then
                    substring($binding/@id, string-length($binding/@id) - string-length('-component'))
                else
                    error((), concat('binding id doesn''t end with ''-component'': ', $binding/@id))"/>
        <xsl:variable
            name="library-name"
            select="frf:findAppFromSectionTemplateUri(namespace-uri-for-prefix('component', $binding))"/>
        <xsl:variable
            name="section-name"
            select="substring-after($binding/@element, 'component|')"/>
        <xsl:variable
            name="model-id"
            select="concat($section-name, '-model')" as="xs:string"/>
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <!-- Implementation -->
            <!-- Set `model`, see https://github.com/orbeon/orbeon-forms/issues/3243 -->
            <xf:group appearance="xxf:internal" model="{$model-id}" id="fr-section-template-view">

                <!-- Point to the context of the current element.
                     NOTE: FB doesn't place a @ref. -->
                <xf:var name="context" id="context" value="xxf:binding-context('{$binding-id}-component')"/>

                <!-- Propagate readonly of containing section -->
                <xf:var name="readonly" as="xs:boolean" value="exf:readonly($context)">
                    <xf:setvalue
                        event="xforms-enabled xforms-value-changed"
                        ref="instance('readonly')"
                        value="exf:readonly($context)"
                        class="fr-design-time-preserve"/>
                </xf:var>

                <!-- Expose internally a variable pointing to Form Runner resources -->
                <xf:var name="fr-resources" as="element()?">
                    <xxf:value value="$fr-resources" xxbl:scope="outer"/>
                </xf:var>

                <!-- Try to match the current form language, or use the first language available if not found -->
                <!-- NOTE: Put this in the view, as the variable doesn't update properly if in the model.
                     See: https://github.com/orbeon/orbeon-forms/issues/738 -->
                <!-- NOTE: This won't be needed once all code uses xxf:r(). -->
                <xf:var
                    fr:keep-if-design-time="false"
                    name="form-resources"
                    value="instance('fr-form-resources')/(resource[@xml:lang = xxf:instance('fr-language-instance')], resource[1])[1]"
                    as="element(resource)"/>
                <xf:var
                    fr:keep-if-design-time="true"
                    name="form-resources"
                    value="instance('fr-form-resources')/(resource[@xml:lang = xxf:instance('fb-language-instance')], resource[1])[1]"
                    as="element(resource)"/>

                <xh:div fr:keep-if-design-time="true" class="fb-section-template-details">
                    <xh:i class="fa fa-fw fa-puzzle-piece"/>
                    <xsl:value-of select="concat($library-name, ' / ',  $section-name, ' / v', $binding/@data-library-version, '')"/>
                </xh:div>

                <xsl:apply-templates select="xf:group/fr:grid"/>
            </xf:group>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
