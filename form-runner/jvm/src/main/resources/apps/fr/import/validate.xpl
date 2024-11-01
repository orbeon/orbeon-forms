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
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:oxf="http://www.orbeon.com/oxf/processors"
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:xs="http://www.w3.org/2001/XMLSchema"
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:ev="http://www.w3.org/2001/xml-events"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xmlns:saxon="http://saxon.sf.net/"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
          xmlns:utils="java:org.orbeon.oxf.xml.SaxonUtils">

    <!--
        Example:

        <upload>
            <file mediatype="application/zip" filename="report.xlsx" size="15123">file:/...</file>
            <file-format>excel-named-ranges | excel-headings</file-format>
        </upload>
    -->
    <p:param type="input"  name="instance"/>
    <!--
        Form data, example:

        <form>
            ...
        </form>
    -->
    <p:param type="output" name="data"/>

    <!-- Apply unzip/XForms model/zip/result -->
    <p:processor name="oxf:pipeline">
        <p:input name="instance" href="#instance"/>
        <p:input name="config" href="apply.xpl"/>
        <p:input name="xforms-model">
            <!-- XForms model that gets inserted into Form Runner to perform validation upon initialization -->
            <xf:model
                id="fr-batch-validation-model"
                xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">

                <xf:instance src="input:data" id="fr-batch-data" xxf:readonly="true"/>
                <xf:instance id="fr-empty-data"><_/></xf:instance>
                <xf:instance id="fr-import-stats" xxf:exclude-result-prefixes="#all">
                    <stats>
                        <total>0</total>
                        <processed>0</processed>
                        <succeeded>0</succeeded>
                        <invalid-rows/>
                    </stats>
                </xf:instance>

                <xf:submission
                    id="fr-send-stats"
                    ref="instance('fr-import-stats')"
                    method="post"
                    action="echo:"
                    replace="all"/>

                <xf:action ev:event="xforms-ready" xxf:xpath-analysis="true">
                    <!-- Remember original empty data -->

                    <xf:var
                        name="format"
                        value="if (local-name(instance()) = 'rows') then 'headings' else 'named-ranges'"/>

                    <xf:insert
                        ref="instance('fr-empty-data')"
                        origin="xxf:instance('fr-form-instance')"/>

                    <xf:setvalue
                        ref="instance('fr-import-stats')/total"
                        value="
                            if ($format = 'headings') then
                                count(instance()/row) - 1
                            else
                                1"/>

                    <xf:action type="xpath">
                        xxf:set-session-attribute('org.orbeon.fr.import.total', xs:integer(instance('fr-import-stats')/total)),
                        xxf:set-session-attribute('org.orbeon.fr.import.processed', 0),
                        xxf:set-session-attribute('org.orbeon.fr.import.succeeded', 0)
                    </xf:action>

<!--                    <xf:message level="xxf:log-error" value="$format"/>-->
<!--                    <xf:message level="xxf:log-error" value="xxf:serialize(instance(), 'xml')"/>-->

                    <xf:action if="$format = 'headings'">

                        <!-- TODO: validate that headers match data -->

                        <xf:var
                            name="non-empty-headers"
                            value="instance()/row[1]/c[normalize-space(.) and @r]"/>

                        <xf:action iterate="instance()/row[position() gt 1]">
                            <xf:var name="row" value="."/>
                            <xf:var name="p"   value="position()"/>

                            <!-- Check at each iteration whether to stop -->
                            <xf:action if="not(xxf:get-session-attribute('org.orbeon.fr.import.cancel'))">

                                <!-- Start with empty data -->
                                <xf:var name="new" value="xxf:create-document()"/>
                                <xf:insert context="$new" origin="instance('fr-empty-data')"/>

                                <!-- Fill data -->
                                <xf:action iterate="$non-empty-headers">
                                    <xf:var name="r"          value="@r/string()"/>
                                    <xf:var name="raw-header" value="normalize-space(.)"/>
                                    <xf:var name="v"          value="($row/c[@r = $r]/string(), '')[1]"/>

                                    <xf:setvalue
                                        ref="$new//*[not(*) and name() = utils:makeNCName($raw-header, false())]"
                                        value="$v"/>
                                </xf:action>

                                <!-- Set filled data -->
                                <xf:insert ref="xxf:instance('fr-form-instance')" origin="$new"/>
                                <xf:refresh/>
                                <!-- Remember validity -->
                                <xf:setvalue
                                    ref="instance('fr-import-stats')/processed"
                                    value="xs:integer(.) + 1"/>
                                <xf:setvalue
                                    if="xxf:instance('fr-error-summary-instance')/valid = true()"
                                    ref="instance('fr-import-stats')/succeeded"
                                    value="xs:integer(.) + 1"/>
                                <xf:setvalue
                                    if="xxf:instance('fr-error-summary-instance')/valid != true()"
                                    ref="instance('fr-import-stats')/invalid-rows"
                                    value="if (. != '') then concat(., ' ', $p) else $p"/>

                                <xf:action type="xpath">
                                    xxf:set-session-attribute('org.orbeon.fr.import.processed', instance('fr-import-stats')/processed/xs:integer(.)),
                                    xxf:set-session-attribute('org.orbeon.fr.import.succeeded', instance('fr-import-stats')/succeeded/xs:integer(.))
                                </xf:action>
                            </xf:action>

                        </xf:action>
                    </xf:action>
                    <xf:action if="$format = 'named-ranges'">

                        <!-- In this mode, the instance contains the form data already -->
                        <xf:insert
                            ref="xxf:instance('fr-form-instance')"
                            origin="instance()/form"/>

                        <xf:refresh/>

                        <!-- Remember validity -->
                        <xf:setvalue
                            ref="instance('fr-import-stats')/processed"
                            value="xs:integer(.) + 1"/>
                        <xf:setvalue
                            if="xxf:instance('fr-error-summary-instance')/valid = true()"
                            ref="instance('fr-import-stats')/succeeded"
                            value="xs:integer(.) + 1"/>
                        <xf:setvalue
                            if="xxf:instance('fr-error-summary-instance')/valid != true()"
                            ref="instance('fr-import-stats')/invalid-rows"
                            value="1"/>

                        <xf:action type="xpath">
                            xxf:set-session-attribute('org.orbeon.fr.import.processed', instance('fr-import-stats')/processed/xs:integer(.)),
                            xxf:set-session-attribute('org.orbeon.fr.import.succeeded', instance('fr-import-stats')/succeeded/xs:integer(.))
                        </xf:action>

                        <xf:insert
                            ref="instance('fr-import-stats')/*"
                            origin="instance()/*"/>

                        <xf:insert
                            ref="instance('fr-import-stats')/*"
                            origin="xxf:instance('fr-error-summary-instance')/counts"/>

                    </xf:action>

                    <xf:action type="xpath">
                        xxf:set-session-attribute('org.orbeon.fr.import.invalid-rows', xs:string(instance('fr-import-stats')/invalid-rows))
                    </xf:action>
                    <xf:delete ref="instance('fr-import-stats')/invalid-rows"/>

                    <!-- Output resulting instance -->
                    <xf:send submission="fr-send-stats"/>

                </xf:action>
            </xf:model>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
