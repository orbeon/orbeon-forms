<!--
    Copyright (C) 2004 Orbeon, Inc.
  
    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.
  
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.
  
    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param type="output" name="data"/>

    <p:choose href="../database/type.xml">

        <p:when test="/type = 'file'">

            <p:processor name="oxf:pipeline">
                <p:input name="config" href="../database/database.xpl"/>
                <p:output name="data" id="db"/>
            </p:processor>

            <p:processor name="oxf:xslt">
                <p:input name="data" href="#db"/>
                <p:input name="config">
                     <xsl:stylesheet version="1.0">
                         <xsl:template match="/">
                             <Categories>
                                 <xsl:for-each select="/Populate/Catalog/Categories/Category">
                                     <xsl:copy>
                                         <id><xsl:value-of select="@id"/></id>
                                         <xsl:copy-of select="CategoryDetails"/>
                                     </xsl:copy>
                                 </xsl:for-each>
                             </Categories>
                         </xsl:template>

                     </xsl:stylesheet>
                 </p:input>
                 <p:output name="data" id="database"/>
            </p:processor>
        </p:when>

        <p:when test="/type = 'db'">
            <p:processor name="oxf:pipeline">
                 <p:input name="config" href="../database/locale.xpl"/>
                 <p:output name="locale" id="locale"/>
             </p:processor>

            <p:processor name="oxf:sql">
                 <p:input name="data" href="#locale"/>
                 <p:input name="config">
                   <sql:config  xmlns:sql="http://orbeon.org/oxf/xml/sql">
                       <sql:connection>
                           <sql:datasource>db</sql:datasource>
                           <Categories>
                               <sql:execute>
                                   <sql:query>
                                     select * from category, category_details
                                        where category.catid = category_details.catid and
                                              category_details.locale=<sql:parameter type="xs:string" select="/locale"/>
                                   </sql:query>
                                   <sql:results>
                                        <sql:row-results>
                                            <Category>
                                                <id><sql:get-column type="xs:string" column="catid"/></id>
                                                <CategoryDetails>
                                                    <Name><sql:get-column type="xs:string" column="name"/></Name>
                                                </CategoryDetails>
                                            </Category>
                                        </sql:row-results>
                                   </sql:results>
                               </sql:execute>
                           </Categories>
                       </sql:connection>
                   </sql:config>
                 </p:input>
                 <p:output name="data" id="database"/>
             </p:processor>
        </p:when>
    </p:choose>


    <p:processor name="oxf:xslt">
        <p:input name="data" href="#database"/>
        <p:input name="config" href="sidebar.xsl"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
