======================================================

= = = =    Orbeon Presentation Server Read Me    = = =

======================================================

Copyright 1999-2005 (C) Orbeon, Inc. All rights reserved.

This README.TXT file covers the following topics:

    1. About Orbeon Presentation Server
    2. Licenses
    3. New Features
    4. Software Prerequisites
    5. Installing Presentation Server
    6. Compiling Presentation Server
    7. More Information
    8. Known Issues
    9. Third-Party Software

For more information, visit:

  http://www.orbeon.com/


************************************
1. About Orbeon Presentation Server
************************************

Orbeon Presentation Server is a J2EE-based MVC framework for building
Web applications that present and capture XML using XForms, XSLT, and
Web Services. Presentation Server benefits from standardized forms
processing using XForms, rich controller semantics, and full-featured
XML pipelines. Presentation Server is in fact built around Orbeon's
optimized XML pipeline engine, a mature, high-performance engine for
pipeline processing of XML. Presentation Server is ideal for building
Composite Applications in a Service Oriented Architecture (SOA), an can
be used on its own, or within the OIS suite.


************************************
2. Licenses
************************************

The source code is distributed under the terms of the GNU Lesser General
Public License (LGPL). The full text of the license is available at
http://www.gnu.org/copyleft/lesser.html.

Some examples are distributed under the terms of the Apache License,
Version 2.0. The full text of the license is available at:
http://www.apache.org/licenses/LICENSE-2.0.

Please refer to file headers to identify which license governs the
distribution of a particular file.

This software is OSI Certified Open Source Software.
OSI Certified is a certification mark of the Open Source Initiative.

The documentation is subject to the following terms:

    * Conversion to other formats is allowed, but the actual content
      may not be altered or edited in any way.

    * You may create printed copies for your own personal use.

    * For all other uses, such as selling printed copies or using
      (parts of) the manual in another publication, prior written
      agreement from Orbeon, Inc. is required.

See section 9 for more details about the licenses of included
third-party software.


************************************
3. New Features
************************************

A complete list of changes can be found online at:

  http://www.orbeon.com/ois/doc/home-changes

or in this distribution under:

  doc/home-changes.html


************************************
4. Software Prerequisites
************************************

Orbeon Presentation Server is supported on the following
application servers:

    * Apache Tomcat 4.1.30 with JDK 1.4.2
    * Apache Tomcat 5.0.25 alpha with JDK 1.4.2
    * Apache Tomcat 5.5.4 with JDK 1.4.2 and JDK 1.5.0
    * BEA WebLogic Server 8.1
    * IBM WebSphere 5.1


************************************
5. Installing Presentation Server
************************************

    * Download the Presentation Server zip or tgz archive from
      http://forge.objectweb.org/projects/ops

    * Extract the archive in a directory that we call below:
      OIS_HOME/

After extraction, you will find the following files and directories
under the root directory OIS_HOME/:

    * README.txt: this document.

    * orbeon.war: the Web Archive where the software, required
      resources, and demo samples reside.

    * orbeon-bizdoc.war: the Web Archive containing the BizDoc
      tutorial application.  For more information on the BizDoc
      example application, please consult the tutorial, Tutorial.pdf.

    * doc: this directory contains the Presentation Server
      documentation as static HTML files, as well as the tutorial,
      Tutorial.pdf.

    * examples: this directory contains command line examples

    * licenses: this directory contains third-party software licenses

You then need to deploy orbeon.war file to your servlet containers or
application server of choice. In the Presentation Server
documentation, you will find detailed installation instructions for
several servlet containers / applications servers.

More detailed information on installing Presentation Server can be
found in the documentation under OIS_HOME/doc/ in the "Installation
Instructions" section, or directly online:

  http://www.orbeon.com/ois/doc/intro-install


************************************
6. Compiling Presentation Server
************************************

For information about compiling Presentation Server, please visit:

  http://www.orbeon.com/community/getting-involved

************************************
7. More Information
************************************

Here are some pointers to useful information related to this release.

    * For more information and updates, go to:

        http://www.orbeon.com/software/presentation-server

    * If you have questions, comments, or difficulties with the
      Presentation Server, please subscribe to the ObjectWeb user mailing-list:

        http://www.objectweb.org/wws/info/ops-users

    * For commercial support and licensing alternatives, please contact:

        info@orbeon.com


************************************
8. Known Issues
************************************

For a list of known issues, consult the ObjectWeb issue tracking system:

  http://forge.objectweb.org/tracker/?atid=350207&group_id=168&func=browse


************************************
9. Third-Party Software
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

This software makes use of a schema for XSLT 2.0 provided under W3C
Software License. The schema is available at the following location:

    jar:orbeon.jar!/org/orbeon/oxf/xml/schemas/xslt-2_0.xsd

Please consult the third-party-licenses directory for more information
about individual licenses.

Last updated: January 2005

Copyright 1999-2005 (C) Orbeon, Inc. All rights reserved.

--
