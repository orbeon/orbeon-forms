<xsl:stylesheet
    version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xf="http://www.w3.org/2002/xforms"
    xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
    xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
    xmlns:xh="http://www.w3.org/1999/xhtml"
    xmlns:xbl="http://www.w3.org/ns/xbl"
    xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:frf="java:org.orbeon.oxf.fr.FormRunner">

    <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>

    <xsl:variable
        name="sync-actions"
        select="
            $candidate-action-models/fr:synchronize-repeated-content[@version = '2018.2']"/>

    <xsl:variable
        name="sync-actions-ids"
        select="
            $sync-actions/generate-id()"/>

    <!-- Implement synchronization actions -->
    <xsl:template
        match="
            /xh:html/xh:head//
                xf:model[
                    generate-id() = $candidate-action-models-ids
                ]/*[
                    generate-id() = $sync-actions-ids
                ]">

        <xsl:variable name="left-name"  select="@left"/>
        <xsl:variable name="right-name" select="@right"/>

        <xsl:variable
            name="right-control"
            select="frf:findControlByNameXPath(., $right-name)"/>

        <!-- Pick `apply-defaults` from the destination control.
             https://github.com/orbeon/orbeon-forms/issues/4038 -->
        <xsl:variable
            name="apply-defaults"
            select="$right-control/@apply-defaults = 'true'"/>

        <!-- Initial sync, unless explicitly disabled -->
        <xsl:variable
            name="disable-initial-sync"
            select="@sync-on-form-load = 'none'"/>
        <xsl:if test="not($disable-initial-sync)">
            <xf:action event="xforms-model-construct-done">

                <xf:var
                    name="left-container"
                    value="bind(frf:bindId('{$left-name}'))"/>
                <xf:var
                    name="right-container"
                    value="bind(frf:bindId('{$right-name}'))"/>

                <xf:var
                    name="repeat-template"
                    value="instance(frf:templateId('{$right-name}'))"/>

                <xf:var
                    name="diff"
                    value="count($right-container/*) - count($left-container/*)"/>

                <!-- Remove extra iterations if any -->
                <xf:delete
                    if="$diff gt 0"
                    ref="$right-container/*[position() gt count($left-container/*)]"/>

                <!-- Insert iterations if needed  -->
                <xf:insert
                    context="$right-container"
                    if="$diff lt 0"
                    ref="*"
                    origin="
                        let $t := frf:updateTemplateFromInScopeItemsetMaps($right-container, $repeat-template)
                        return
                            for $i in (1 to -$diff)
                            return $t"
                    position="after"
                    xxf:defaults="{$apply-defaults}"/>

                <xf:action if="$diff != 0">
                    <xf:rebuild/>
                    <xf:recalculate/>
                </xf:action>

                <!-- Update all values -->
                <xf:action iterate="1 to count($left-container/*)">

                    <xf:var name="p" value="."/>

                    <xsl:for-each select="fr:map">
                        <xf:action>
                            <xf:var name="src" context="$left-container/*[$p]"  value="(.//{@left})[1]"/>
                            <xf:var name="dst" context="$right-container/*[$p]" value="(.//{@right})[1]"/>

                            <xf:setvalue
                                ref="$dst"
                                value="$src"/>
                        </xf:action>
                    </xsl:for-each>

                </xf:action>
            </xf:action>
        </xsl:if>

        <!-- Propagate value changes -->
        <xsl:for-each select="fr:map">
            <xf:action
                event="xforms-value-changed"
                observer="{@left}-control"
                target="#observer">

                <xf:var
                    name="p"
                    value="
                        for $i in event('xxf:repeat-indexes')[last()]
                        return xs:integer($i)"/>

                <xf:var
                    name="right-context"
                    value="
                        bind(
                            frf:bindId('{$right-name}')
                        )/*[$p]"/>

                <xf:setvalue
                    context="$right-context"
                    ref="(.//{@right})[1]"
                    value="event('xxf:value')"/>

            </xf:action>
        </xsl:for-each>

        <!-- NOTE: There is a lot of logic duplication here with `fr:grid` and `fr:repeater`. We need
             to consolidate this code. -->
        <!-- Propagate inserts, moves, and remove -->
        <xf:action
            event="fr-move-up fr-move-down fr-remove fr-iteration-added"
            observer="{$left-name}-grid {$left-name}-section">

            <xf:var name="repeat-template" value="instance(frf:templateId('{$right-name}'))"/>
            <xf:var name="context"         value="bind(frf:bindId('{$right-name}'))"/>
            <xf:var name="items"           value="$context/*"/>
            <xf:var name="p"               value="xs:integer(event(if (event('xxf:type') = 'fr-iteration-added') then 'index' else 'row'))"/>
            <xf:var name="source"          value="$items[$p]"/>
            <xf:var name="instance"        value="$source/root()"/>

            <xf:delete
                if="event('xxf:type') = ('fr-remove', 'fr-move-up', 'fr-move-down')"
                ref="$source"/>

            <xf:action if="event('xxf:type') = 'fr-remove'">
                <xf:action type="xpath">
                    frf:garbageCollectMetadataItemsets($instance)
                </xf:action>
            </xf:action>

            <xf:action if="event('xxf:type') = 'fr-move-up'">

                <xf:insert
                    context="$context"
                    ref="$items[$p - 1]"
                    origin="$source"
                    position="before"/>

            </xf:action>

            <xf:action if="event('xxf:type') = 'fr-move-down'">

                <xf:insert
                    context="$context"
                    ref="$items[$p + 1]"
                    origin="$source"
                    position="after"/>

            </xf:action>

            <!-- This handles inserting above, below, and the "+" button -->
            <xf:action if="event('xxf:type') = 'fr-iteration-added'">

                <xf:insert
                    context="$context"
                    ref="$items[$p - 1]"
                    origin="frf:updateTemplateFromInScopeItemsetMaps($context, $repeat-template)"
                    position="after"
                    xxf:defaults="{$apply-defaults}"/>

            </xf:action>

            <xsl:if test="exists(fr:map)">
                <xf:action if="event('xxf:type') = 'fr-iteration-added'">

                    <xf:rebuild/>
                    <xf:recalculate/>

                    <xf:var
                        name="new-p"
                        value="$p"/>

                    <xf:var
                        name="left-container"
                        value="bind(frf:bindId('{$left-name}'))"/>
                    <xf:var
                        name="right-container"
                        value="bind(frf:bindId('{$right-name}'))"/>

                    <xsl:for-each select="fr:map">
                        <xf:action>
                            <xf:var name="src" context="$left-container/*[$new-p]"  value="bind(frf:bindId('{@left}'))"/>
                            <xf:var name="dst" context="$right-container/*[$new-p]" value="bind(frf:bindId('{@right}'))"/>

                            <xf:setvalue
                                ref="$dst"
                                value="$src"/>
                        </xf:action>
                    </xsl:for-each>

                </xf:action>
            </xsl:if>

        </xf:action>

    </xsl:template>

</xsl:stylesheet>
