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
          xmlns:utils="java:org.orbeon.oxf.xml.XMLUtils">

    <p:param type="input" name="instance"/>
    <p:param type="output" name="data"/>

    <!-- Apply unzip/XForms model/zip/result -->
    <p:processor name="oxf:pipeline">
        <p:input name="instance" href="#instance"/>
        <p:input name="config" href="apply.xpl"/>
        <p:input name="xforms-model">
            <!-- XForms model that gets inserted into Form Runner to perform validation upon initialization -->
            <xf:model id="fr-batch-validation-model">

                <xf:instance src="input:data" id="fr-batch-data" xxf:readonly="true"/>
                <xf:instance id="fr-empty-data"><dummy/></xf:instance>
                <xf:instance id="fr-import-stats" xxf:exclude-result-prefixes="#all">
                    <stats>
                        <total>0</total>
                        <processed>0</processed>
                        <succeeded>0</succeeded>
                        <invalid-rows/>
                    </stats>
                </xf:instance>

                <xf:submission id="fr-send-stats" ref="instance('fr-import-stats')" method="post" action="echo:" replace="all"/>

                <xf:action ev:event="xforms-ready" xxf:xpath-analysis="true">
                    <!-- Remember original empty data -->
                    <xf:insert ref="instance('fr-empty-data')" origin="xxf:instance('fr-form-instance')"/>
                    <xxf:var name="headers" value="instance()/row[1]/c"/>

                    <!-- TODO: validate that headers match data -->

                    <xf:setvalue ref="instance('fr-import-stats')/total" value="count(instance()/row) - 1"/>

                    <!--<xf:message level="xxf:log-info" value="concat('xxx ', string-join($headers/string(), ', '))"/>-->

                    <xf:action type="xpath">
                        xxf:set-session-attribute('org.orbeon.fr.import.total', xs:integer(instance('fr-import-stats')/total)),
                        xxf:set-session-attribute('org.orbeon.fr.import.processed', 0),
                        xxf:set-session-attribute('org.orbeon.fr.import.succeeded', 0)
                    </xf:action>

                    <!-- Iterate over all rows -->
                    <xf:action xxf:iterate="instance()/row[position() gt 1]">
                        <xxf:var name="p" value="position()"/>

                        <!-- Check at each iteration whether to stop -->
                        <xf:action if="not(xxf:get-session-attribute('org.orbeon.fr.import.cancel'))">

                            <!-- Start with empty data -->
                            <xxf:var name="new" value="xxf:create-document()"/>
                            <xf:insert context="$new" origin="instance('fr-empty-data')"/>

                            <!-- Fill data -->
                            <xf:action xxf:iterate="c">
                                <xxf:var name="p" value="position()"/>
                                <xxf:var name="v" value="xs:string(.)"/>
                                <xxf:var name="raw-header" value="normalize-space($headers[$p])"/>

                                <!-- Only set value if header name is not blank -->
                                <xf:setvalue ref="$new//*[not(*) and $raw-header != '' and name() = utils:makeNCName($raw-header)]" value="$v"/>
                            </xf:action>

                            <!-- Set filled data -->
                            <xf:insert ref="xxf:instance('fr-form-instance')" origin="$new"/>
                            <xf:refresh/>
                            <!-- Remember validity -->
                            <xf:setvalue ref="instance('fr-import-stats')/processed" value="xs:integer(.) + 1"/>
                            <xf:setvalue if="xxf:instance('fr-error-summary-instance')/valid = 'true'"
                                         ref="instance('fr-import-stats')/succeeded" value="xs:integer(.) + 1"/>
                            <xf:setvalue if="xxf:instance('fr-error-summary-instance')/valid != 'true'"
                                         ref="instance('fr-import-stats')/invalid-rows" value="if (. != '') then concat(., ' ', $p) else $p"/>

                            <xf:action type="xpath">
                                xxf:set-session-attribute('org.orbeon.fr.import.processed', instance('fr-import-stats')/processed),
                                xxf:set-session-attribute('org.orbeon.fr.import.succeeded', instance('fr-import-stats')/succeeded)
                            </xf:action>
                        </xf:action>

                        <!--<xf:message level="xxf:log-info" value="concat('xxx', $p)"/>-->
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
