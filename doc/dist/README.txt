======================================================

= = = = = = = = =   Orbeon Read Me     = = = = = = = =

======================================================

Copyright 1999-2004 (C) Orbeon, Inc. All rights reserved.

This README.TXT file covers the following topics:

    1. Licenses
    2. New Features
    3. Software Prerequisites
    4. Installing OXF
    5. More Information
    6. Known Issues
    7. Third-Party Software

For more information, visit:

  http://www.orbeon.com/

************************************
1. Licenses
************************************

The source code is distributed under the terms of the GNU Lesser General Public License (LGPL).
The full text of the license is available at http://www.gnu.org/copyleft/lesser.html.

The documentation is subject to the following terms:

    * Conversion to other formats is allowed, but the actual content may not
      be altered or edited in any way.
    * You may create printed copies for your own personal use.
    * For all other uses, such as selling printed copies or using (parts of) the
      manual in another publication, prior written agreement from Orbeon, Inc.
      is required.

See section 7 for more details about the licenses of included
third-party software.

Please contact Orbeon at info@orbeon.com for more information.

************************************
2. New Features
************************************

A complete list of changes can be found online at:

  http://www.orbeon.com/oxf/doc/home-changes

or in this distribution under:

  doc/home-changes.html


************************************
3. Software Prerequisites
************************************

OXF is supported on the following application servers:

    * Apache Tomcat 4.1.30 with JDK 1.4.1
    * Apache Tomcat 5.0.25 alpha with JDK 1.4.1
    * BEA WebLogic Server 8.1
    * IBM WebSphere 5.1


************************************
4. Installing OXF
************************************

    * Download the OXF zip or tgz archive from
      http://sourceforge.net/projects/orbeon/

    * Extract the archive in a directory which we call below:
      OXF_HOME/

After extraction, you will find the following files and directories under the
root directory OXF_HOME/

    * README.txt: this document.

    * orbeon.war: the Web Archive where the software, required resources, and demo
      samples reside.
      
    * orbeon-bizdoc.war: the Web Archive containing the BizDoc tutorial application.
      For more information on the BizDoc example application, please consult the
      tutorial, Tutorial.pdf.

    * doc: this directory contains the OXF documentation as static HTML files, as
      well as the tutorial, Tutorial.pdf.

    * examples: this directory contains command line examples

    * licenses: this directory contains third-party software licenses

After extraction, you will find a WAR (Web Archive) file in the OXF_HOME/
directory. You need to deploy this WAR file to your servlet containers or
application server of choice. In the OXF Documentation, you'll find detailed
installation instructions for several servlet containers / applications servers.

More detailed information on installing OXF can be found in the OXF
Documentation under OXF_HOME/doc/ in the "Installation Instructions" section,
or directly online:

  http://www.orbeon.com/oxf/doc/intro-install


************************************
5. More Information
************************************

Here are some pointers to useful information related to this release.

    * For more information and updates, go to:

        http://www.orbeon.com/my/

    * If you have questions, comments, or difficulties with OXF, visit:

        http://www.orbeon.com/support/


************************************
6. Known Issues
************************************

For a list of known issues, consult the SourceForge issue tracking system:

  http://sourceforge.net/tracker/?atid=675660&group_id=116683&func=browse


************************************
7. Third-Party Software
************************************

This product includes software developed by the Apache Software Foundation
(http://www.apache.org/):

    * Axis (http://ws.apache.org/axis/)
    * Batik (http://xml.apache.org/batik/)
    * FOP (http://xml.apache.org/fop/)
    * Forrest (http://xml.apache.org/forrest/)
    * Jakarta Commons (http://jakarta.apache.org/commons/)
    * log4j (http://jakarta.apache.org/log4j/docs/)
    * ORO (http://jakarta.apache.org/oro/)
    * POI (http://jakarta.apache.org/poi/)
    * Taglibs (http://jakarta.apache.org/taglibs/)
    * Xalan (http://xml.apache.org/xalan-j/)
    * Xerces (http://xml.apache.org/xerces2-j/)

In addition, this product includes the following software:

    * dom4j (http://dom4j.org/)
    * eXist (http://exist.sourceforge.net/)
    * hsqldb (http://hsqldb.sourceforge.net/)
    * Jaxen (http://jaxen.org/)
    * JFreeChart (http://www.jfree.org/jfreechart/)
    * Jing (http://www.thaiopensource.com/relaxng/jing.html)
    * Joost (http://joost.sourceforge.net/)
    * JTidy (http://sourceforge.net/projects/jtidy/)
    * JUnit (http://www.junit.org/)
    * Kawa (http://www.gnu.org/software/kawa/)
    * Mondrian (http://mondrian.sourceforge.net/)
    * Saxon (http://saxon.sourceforge.net/)
    * SAXPath (http://sourceforge.net/projects/saxpath/)
    * Sun Multi-Schema XML Validator
      (http://wwws.sun.com/software/xml/developers/multischema/)
    * YMSG9 (http://sourceforge.net/projects/jymsg9/)

This software makes use of a schema for XSLT 2.0 provided under W3C
Software License. The schema is available at the following location:

    jar:oxf.jar!/org/orbeon/oxf/xml/schemas/xslt-2_0.xsd

Please consult the licenses directory for more information about
individual licenses.

Last updated: August 2004

Copyright 1999-2004 (C) Orbeon, Inc. All rights reserved.

--
