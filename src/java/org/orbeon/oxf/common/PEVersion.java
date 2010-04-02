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

public class PEVersion extends Version {

    private boolean isLicensed;

    public static final String LICENSE_URL = "oxf:/config/license.xml";
    public static final String ORBEON_PUBLIC_KEY = "oxf:/config/orbeon-public.xml";

    private PEVersion() {
        try {
            final LicenseInfo licenseInfo = check();
            isLicensed = true;
            logger.info("This copy of " + getVersionString() + " is licensed to: " + licenseInfo.toString());
        } catch (Exception e) {
            isLicensed = false;
        }
    }

    private final LicenseInfo check() {
        final Processor licence = PipelineUtils.createURLGenerator(LICENSE_URL);
        final Processor key = PipelineUtils.createURLGenerator(ORBEON_PUBLIC_KEY);

        final SignatureVerifierProcessor verifierProcessor = new SignatureVerifierProcessor();
        PipelineUtils.connect(licence, ProcessorImpl.OUTPUT_DATA, verifierProcessor, ProcessorImpl.INPUT_DATA);
        PipelineUtils.connect(key, ProcessorImpl.OUTPUT_DATA, verifierProcessor, SignatureVerifierProcessor.INPUT_PUBLIC_KEY);

        final DOMSerializer serializer = new DOMSerializer();

        PipelineUtils.connect(verifierProcessor, ProcessorImpl.OUTPUT_DATA, serializer, ProcessorImpl.INPUT_DATA);

        // Execute pipeline
        final PipelineContext pipelineContext = new PipelineContext();
        serializer.reset(pipelineContext);
        serializer.start(pipelineContext);

        final Document licenceDocument = serializer.getDocument(pipelineContext);

        final String licensor = XPathUtils.selectStringValueNormalize(licenceDocument, "/license/licensor");
        final String licensee = XPathUtils.selectStringValueNormalize(licenceDocument, "/license/licensee");
        final String company = XPathUtils.selectStringValueNormalize(licenceDocument, "/license/company");
        final String email = XPathUtils.selectStringValueNormalize(licenceDocument, "/license/email");
        final String issued = XPathUtils.selectStringValueNormalize(licenceDocument, "/license/issued");
        final Date expiration;
        try {
            final String expireStr = XPathUtils.selectStringValueNormalize(licenceDocument, "/license/expiration");
            if (expireStr != null && !expireStr.equals("")) {
                final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
                expiration = df.parse(expireStr);
            } else
                expiration = null;
        } catch (ParseException e) {
            throw new OXFException(e);
        }

        if (expiration != null) {
            if (new Date().after(expiration)) {
                final String message = "License expires on: " + DateFormat.getDateInstance().format(expiration);
                logger.error(message);
                throw new OXFException(message);
            }
        }

        return new LicenseInfo(licensor, licensee, company, email, issued, expiration);
    }

    private static class LicenseInfo {
        public final String licensor;
        public final String licensee;
        public final String company;
        public final String email;
        public final String issued;
        public final Date expiration;

        private LicenseInfo(String licensor, String licensee, String company, String email, String issued, Date expiration) {
            this.licensor = licensor;
            this.licensee = licensee;
            this.company = company;
            this.email = email;
            this.issued = issued;
            this.expiration = expiration;
        }

        @Override
        public String toString() {
            final String expires = (expiration != null) ? " and expires on " + DateFormat.getDateInstance().format(expiration) : "";
            return licensee + " / " + company + " / " + email + expires;
        }
    }

    @Override
    public String getCode() {
        return "PE";
    }

    @Override
    public boolean isPE() {
        return isLicensed;
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
