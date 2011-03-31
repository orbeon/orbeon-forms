/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.common;

import org.apache.commons.lang.StringUtils;
import org.dom4j.Document;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.DOMSerializer;
import org.orbeon.oxf.processor.Processor;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.SignatureVerifierProcessor;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.analysis.DumbXPathDependencies;
import org.orbeon.oxf.xforms.analysis.PathMapXPathDependencies;
import org.orbeon.oxf.xforms.analysis.XPathDependencies;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.StringTokenizer;

import static org.orbeon.oxf.processor.ProcessorImpl.OUTPUT_DATA;

public class PEVersion extends Version {

    public static final String LICENSE_PATH = "/config/license.xml";
    public static final String LICENSE_URL = "oxf:" + LICENSE_PATH;
    public static final String ORBEON_PUBLIC_KEY = "oxf:/config/orbeon-public.xml";

    public PEVersion() {
        try {
            final LicenseInfo licenseInfo = check();
            logger.info("This installation of " + getLocalVersionString() + " is licensed to: " + licenseInfo.toString());
        } catch (Exception e) {
            logger.error("License check failed for " + getLocalVersionString());
            throw new OXFException(e);
        }
    }

    private String getLocalVersionString() {
        return "Orbeon Forms " + VERSION_NUMBER + ' ' + getEdition();
    }

    private final LicenseInfo check() {

        final Processor key = PipelineUtils.createURLGenerator(ORBEON_PUBLIC_KEY);

        // Remove blank spaces in license file as that's the way it was signed
        final Processor licence;
        try {
            final Document rawLicenceDocument = ResourceManagerWrapper.instance().getContentAsDOM4J(LICENSE_PATH);
            final String compactString = Dom4jUtils.domToCompactString(rawLicenceDocument);
            final Document licenseDocument = Dom4jUtils.readDom4j(compactString);
            licence = new DOMGenerator(licenseDocument, "license", DOMGenerator.ZeroValidity, LICENSE_URL);
        } catch (Exception e) {
            throw new OXFException(e);
        }

        // Connect pipeline
        final SignatureVerifierProcessor verifierProcessor = new SignatureVerifierProcessor();
        PipelineUtils.connect(licence, OUTPUT_DATA, verifierProcessor, ProcessorImpl.INPUT_DATA);
        PipelineUtils.connect(key, OUTPUT_DATA, verifierProcessor, SignatureVerifierProcessor.INPUT_PUBLIC_KEY);

        final DOMSerializer serializer = new DOMSerializer();

        PipelineUtils.connect(verifierProcessor, OUTPUT_DATA, serializer, ProcessorImpl.INPUT_DATA);

        // Execute pipeline
        final Document licenceDocument;
        boolean success = false;
        final PipelineContext pipelineContext = new PipelineContext();
        try {
            serializer.reset(pipelineContext);
            serializer.start(pipelineContext);

            // Gather resulting license information
            licenceDocument = serializer.getDocument(pipelineContext);
            success = true;
        } finally {
            pipelineContext.destroy(success);
        }

        final String licensor = XPathUtils.selectStringValueNormalize(licenceDocument, "/license/licensor");
        final String licensee = XPathUtils.selectStringValueNormalize(licenceDocument, "/license/licensee");
        final String organization = XPathUtils.selectStringValueNormalize(licenceDocument, "/license/organization");
        final String email = XPathUtils.selectStringValueNormalize(licenceDocument, "/license/email");
        final String issued = XPathUtils.selectStringValueNormalize(licenceDocument, "/license/issued");
        final String version = XPathUtils.selectStringValueNormalize(licenceDocument, "/license/version");
        final Date expiration;
        try {
            final String expireStr = XPathUtils.selectStringValueNormalize(licenceDocument, "/license/expiration");
            if (StringUtils.isNotBlank(expireStr)) {
                final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
                expiration = df.parse(expireStr);
            } else {
                expiration = null;
            }
        } catch (ParseException e) {
            throw new OXFException(e);
        }

        // Check version
        if (StringUtils.isNotBlank(version)) {
            if (isVersionExpired(getVersionNumber(), version)) {
                final String message = "License version doesn't match. License version is: " + version + ", Orbeon Forms version is: " + getVersionNumber();
                logger.error(message);
                throw new OXFException(message);
            }
        }

        // Check expiration
        if (expiration != null) {
            if (new Date().after(expiration)) {
                final String message = "License has expired on " + DateFormat.getDateInstance().format(expiration);
                logger.error(message);
                throw new OXFException(message);
            }
        }

        return new LicenseInfo(licensor, licensee, organization, email, issued, version, expiration);
    }

    public static boolean isVersionExpired(String currentVersion, String licenseVersion) {
        final int[] currentVersionParts = parseVersionNumber(currentVersion);
        final int[] licenseVersionParts = parseVersionNumber(licenseVersion);

        return currentVersionParts == null
                || currentVersionParts[0] > licenseVersionParts[0]
                || (currentVersionParts[0] == licenseVersionParts[0] && currentVersionParts[1] > licenseVersionParts[1]);
    }

    private static int[] parseVersionNumber(String versionString) {
        final StringTokenizer st = new StringTokenizer(versionString, ".");
        if (st.hasMoreTokens()) {
            final int major = Integer.parseInt(st.nextToken());
            if (st.hasMoreTokens()) {
                final int minor = Integer.parseInt(st.nextToken());
                return new int[] { major, minor };
            }
        }
        return null;
    }

    private static class LicenseInfo {
        public final String licensor;
        public final String licensee;
        public final String organization;
        public final String email;
        public final String issued;
        public final String version;
        public final Date expiration;

        private LicenseInfo(String licensor, String licensee, String organization, String email, String issued, String version, Date expiration) {
            this.licensor = licensor;
            this.licensee = licensee;
            this.organization = organization;
            this.email = email;
            this.issued = issued;
            this.version = version;
            this.expiration = expiration;
        }

        @Override
        public String toString() {
            final String expires = (expiration != null) ? " and expires on " + DateFormat.getDateInstance().format(expiration) : "";
            final String version = (this.version != null) ? " for version " + this.version : "";
            return licensee + " / " + organization + " / " + email + version + expires;
        }
    }

    @Override
    public void checkPEFeature(String featureName) {}

    @Override
    public boolean isPEFeatureEnabled(boolean featureRequested, String featureName) {
        return featureRequested;
    }

    @Override
    public XPathDependencies createUIDependencies(XFormsContainingDocument containingDocument) {
        final boolean requested = containingDocument.getStaticState().isXPathAnalysis();
        return requested ? new PathMapXPathDependencies(containingDocument) : new DumbXPathDependencies();
    }
}
