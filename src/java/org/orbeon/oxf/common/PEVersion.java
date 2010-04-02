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
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.analysis.PathMapUIDependencies;
import org.orbeon.oxf.xforms.analysis.UIDependencies;
import org.orbeon.oxf.xml.XPathUtils;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.orbeon.oxf.processor.ProcessorImpl.OUTPUT_DATA;

public class PEVersion extends Version {

    public static final String LICENSE_URL = "oxf:/config/license.xml";
    public static final String ORBEON_PUBLIC_KEY = "oxf:/config/orbeon-public.xml";

    private final LicenseInfo licenseInfo;

    public PEVersion() {
        try {
            licenseInfo = check();
            logger.info("This installation of " + getLocalVersionString() + " is licensed to: " + licenseInfo.toString());
        } catch (Exception e) {
            logger.error("License check failed for " + getLocalVersionString());
            throw new OXFException(e);
        }
    }

    private String getLocalVersionString() {
        return "Orbeon Forms " + RELEASE_NUMBER + ' ' + getCode();
    }

    private final LicenseInfo check() {
        // Connect pipeline
        final Processor licence = PipelineUtils.createURLGenerator(LICENSE_URL);
        final Processor key = PipelineUtils.createURLGenerator(ORBEON_PUBLIC_KEY);

        final SignatureVerifierProcessor verifierProcessor = new SignatureVerifierProcessor();
        PipelineUtils.connect(licence, OUTPUT_DATA, verifierProcessor, ProcessorImpl.INPUT_DATA);
        PipelineUtils.connect(key, OUTPUT_DATA, verifierProcessor, SignatureVerifierProcessor.INPUT_PUBLIC_KEY);

        final DOMSerializer serializer = new DOMSerializer();

        PipelineUtils.connect(verifierProcessor, OUTPUT_DATA, serializer, ProcessorImpl.INPUT_DATA);

        // Execute pipeline
        final PipelineContext pipelineContext = new PipelineContext();
        serializer.reset(pipelineContext);
        serializer.start(pipelineContext);

        // Gather resulting license information
        final Document licenceDocument = serializer.getDocument(pipelineContext);

        final String licensor = XPathUtils.selectStringValueNormalize(licenceDocument, "/license/licensor");
        final String licensee = XPathUtils.selectStringValueNormalize(licenceDocument, "/license/licensee");
        final String organization = XPathUtils.selectStringValueNormalize(licenceDocument, "/license/organization");
        final String email = XPathUtils.selectStringValueNormalize(licenceDocument, "/license/email");
        final String issued = XPathUtils.selectStringValueNormalize(licenceDocument, "/license/issued");
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

        if (expiration != null) {
            if (new Date().after(expiration)) {
                final String message = "License has expired on " + DateFormat.getDateInstance().format(expiration);
                logger.error(message);
                throw new OXFException(message);
            }
        }

        return new LicenseInfo(licensor, licensee, organization, email, issued, expiration);
    }

    private static class LicenseInfo {
        public final String licensor;
        public final String licensee;
        public final String organization;
        public final String email;
        public final String issued;
        public final Date expiration;

        private LicenseInfo(String licensor, String licensee, String organization, String email, String issued, Date expiration) {
            this.licensor = licensor;
            this.licensee = licensee;
            this.organization = organization;
            this.email = email;
            this.issued = issued;
            this.expiration = expiration;
        }

        @Override
        public String toString() {
            final String expires = (expiration != null) ? " and expires on " + DateFormat.getDateInstance().format(expiration) : "";
            return licensee + " / " + organization + " / " + email + expires;
        }
    }

    @Override
    public String getCode() {
        return "PE";
    }

    @Override
    public boolean isPE() {
        return licenseInfo != null;
    }

    @Override
    public boolean isPEFeatureEnabled(boolean featureRequested, String featureName) {
        return featureRequested && isPE();
    }

    @Override
    public UIDependencies createUIDependencies(XFormsContainingDocument containingDocument) {
        return isPE() ? new PathMapUIDependencies(containingDocument) : super.createUIDependencies(containingDocument);
    }
}
