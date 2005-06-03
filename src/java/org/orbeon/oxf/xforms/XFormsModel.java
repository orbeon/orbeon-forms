/**
 *  Copyright (C) 2004 - 2005 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms;

import com.sun.msv.grammar.Grammar;
import com.sun.msv.grammar.IDContextProvider2;
import com.sun.msv.reader.GrammarReaderController;
import com.sun.msv.reader.util.GrammarLoader;
import com.sun.msv.util.DatatypeRef;
import com.sun.msv.util.StartTagInfo;
import com.sun.msv.util.StringRef;
import com.sun.msv.verifier.Acceptor;
import com.sun.msv.verifier.regexp.REDocumentDeclaration;
import com.sun.msv.verifier.regexp.StringToken;
import org.apache.log4j.Logger;
import org.dom4j.*;
import org.orbeon.oxf.cache.Cache;
import org.orbeon.oxf.cache.CacheKey;
import org.orbeon.oxf.cache.ObjectCache;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.event.*;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.msv.IDConstraintChecker;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.expr.XPathContextMajor;
import org.orbeon.saxon.functions.FunctionLibrary;
import org.orbeon.saxon.type.ItemType;
import org.orbeon.saxon.type.Type;
import org.orbeon.saxon.value.StringValue;
import org.orbeon.saxon.xpath.StandaloneContext;
import org.orbeon.saxon.xpath.XPathEvaluator;
import org.orbeon.saxon.xpath.XPathException;
import org.relaxng.datatype.Datatype;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;

/**
 * Represents an XForms model.
 */
public class XFormsModel implements XFormsEventTarget, Cloneable {

    private static class MSVGrammarReaderController implements GrammarReaderController {

        static private Logger logger = LoggerFactory.createLogger(MSVGrammarReaderController.class);

        private final String base;
        private final SchemaInfo schemaInfo;

        MSVGrammarReaderController(final String bs, final SchemaInfo schmInf) {
            base = bs;
            schemaInfo = schmInf;
        }

        public void warning(final Locator[] locs, final String msg) {
            if (locs == null && locs.length == 0) {
                logger.warn(msg);
            } else {
                final String frst = XMLUtils.toString(locs[0]);
                final StringBuffer sb = new StringBuffer(frst);
                for (int i = 1; i < locs.length; i++) {
                    sb.append(',');
                    final String locMsg = XMLUtils.toString(locs[i]);
                    sb.append(locMsg);
                }
                sb.append(':');
                sb.append(msg);
                final String logMsg = sb.toString();
                logger.warn(logMsg);
            }
        }

        public void error(final Locator[] locs, final String msg, final Exception ex) {
            final LocationData ld = locs.length > 0 ? new LocationData(locs[0]) : null;
            throw new ValidationException(msg, ex, ld);
        }

        public InputSource resolveEntity(final String pid, final String sid)
                throws SAXException, IOException {
            final java.net.URL u = URLFactory.createURL(base, sid);
            schemaInfo.addInclude(u);

            final String surl = u.toString();
            final InputSource ret = XMLUtils.ENTITY_RESOLVER.resolveEntity("", surl);
            return ret;
        }

    }

    private static class SchemaKey extends CacheKey {
        final int hash;
        final java.net.URL url;

        SchemaKey(final java.net.URL u) {
            setClazz(SchemaKey.class);
            url = u;
            hash = url.hashCode();
        }

        public int hashCode() {
            return hash;
        }

        public boolean equals(final Object rhsObj) {
            final boolean ret;
            if (rhsObj instanceof SchemaKey) {
                final SchemaKey rhs = (SchemaKey) rhsObj;
                ret = url.equals(rhs.url);
            } else {
                ret = false;
            }
            return ret;
        }
    }

    private static class SchemaInfo {

        private final ArrayList includes = new ArrayList(0);
        private final ArrayList modTimes = new ArrayList(0);
        private Grammar grammar;

        void addInclude(final java.net.URL u) throws java.io.IOException {
            // Get the time first.  This way if there's a problem the array lengths will remain
            // the same.
            final Long modTim = NetUtils.getLastModified(u, (Long) null);
            includes.add(u);
            modTimes.add(modTim);
        }

        boolean includesUpToDate() {
            boolean ret = true;
            final int size = includes.size();
            for (int i = 0; ret && i < size; i++) {
                final java.net.URL u = (java.net.URL) includes.get(i);
                try {
                    final Long crntTim = NetUtils.getLastModified(u, (Long) null);
                    final Long lstTim = (Long) modTimes.get(i);
                    ret = crntTim.equals(lstTim);
                } catch (final java.io.IOException e) {
                    // We won't propagate here.  Reason is that while an include may be missing 
                    // it may just be the case that it isn't included anymore _and_ it has been
                    // removed.  So, we return false and then on a reparse we will find out the 
                    // truth.
                    ret = false;
                }
            }
            return ret;
        }

        void setGrammar(final Grammar g) {
            grammar = g;
        }

        Grammar getGrammar() {
            return grammar;
        }
    }

    private static class ValidationContext implements IDContextProvider2 {
        public void onID(final Datatype dt, final String s) {
        }

        public String resolveNamespacePrefix(final String s) {
            return null;
        }

        public String getBaseUri() {
            return null;
        }

        public boolean isUnparsedEntity(final String s) {
            return false;
        }

        public boolean isNotation(final String s) {
            return false;
        }

        public void onID(final Datatype dt, final StringToken st) {
        }
    }

    private static final ValidationContext validationContext = new ValidationContext();

    private Document modelDocument;

    // Model attributes
    private String modelId;
    private Grammar schemaGrammar;

    // Instances
    private List instanceIds;
    private List instances;
    private Map instancesMap;

    private InstanceConstructListener instanceConstructListener;

    // Submission information
    private Map submissions;

    // Binds
    private List binds;
    private FunctionLibrary xformsFunctionLibrary = new XFormsFunctionLibrary(this);

    // Containing document
    private XFormsContainingDocument xFormsContainingDocument;

    public XFormsModel(Document modelDocument) {
        this.modelDocument = modelDocument;

        // Basic check trying to make sure this is an XForms model
        // TODO: should rather use schema here or when obtaining document passed to this constructor
        Element modelElement = modelDocument.getRootElement();
        String rootNamespaceURI = modelElement.getNamespaceURI();
        if (!rootNamespaceURI.equals(XFormsConstants.XFORMS_NAMESPACE_URI))
            throw new ValidationException("Root element of XForms model must be in namespace '"
                    + XFormsConstants.XFORMS_NAMESPACE_URI + "'. Found instead: '" + rootNamespaceURI + "'",
                    (LocationData) modelElement.getData());

        // Get model id (may be null)
        modelId = modelElement.attributeValue("id");

        // Extract list of instances ids
        {
            List instanceContainers = modelElement.elements(new QName("instance", XFormsConstants.XFORMS_NAMESPACE));
            instanceIds = new ArrayList(instanceContainers.size());
            if (instanceContainers.size() > 0) {
                for (Iterator i = instanceContainers.iterator(); i.hasNext();) {
                    final Element instanceContainer = (Element) i.next();
                    String instanceId = instanceContainer.attributeValue("id");
                    if (instanceId == null)
                        instanceId = "";
                    instanceIds.add(instanceId);
                }
            }
        }

        // Get <xforms:submission> elements (may be missing)
        {
            for (Iterator i = modelElement.elements(new QName("submission", XFormsConstants.XFORMS_NAMESPACE)).iterator(); i.hasNext();) {
                final Element submissionElement = (Element) i.next();
                String submissionId = submissionElement.attributeValue("id");
                if (submissionId == null)
                    submissionId = "";

                if (submissions == null)
                    submissions = new HashMap();
                submissions.put(submissionId, new XFormsModelSubmission(submissionElement, this));
            }
        }
    }

    public void setContainingDocument(XFormsContainingDocument xFormsContainingDocument) {
        this.xFormsContainingDocument = xFormsContainingDocument;
    }

    public XFormsContainingDocument getContainingDocument() {
        return xFormsContainingDocument;
    }

    /**
     * Get object with the id specified.
     */
    public Object getObjectByid(PipelineContext pipelineContext, String id) {

        // Check model itself
        if (id.equals(modelId))
            return this;

        // Search instances
        final XFormsInstance instance = (XFormsInstance) instancesMap.get(id);
        if (instance != null)
            return instance;

        // Search submissions
        if (submissions != null) {
            final XFormsModelSubmission resultSubmission = (XFormsModelSubmission) submissions.get(id);
            if (resultSubmission != null)
                return resultSubmission;
        }

        return null;
    }

    private void resetBinds() {
        binds = new ArrayList();
        handleBindContainer(modelDocument.getRootElement(), null);
    }

    /**
     * Gather xforms:bind elements information.
     */
    private void handleBindContainer(Element container, ModelBind parent) {
        for (Iterator i = container.elements(new QName("bind", XFormsConstants.XFORMS_NAMESPACE)).iterator(); i.hasNext();) {
            Element bindElement = (Element) i.next();
            ModelBind modelBind = new ModelBind(bindElement.attributeValue("id"), bindElement.attributeValue("nodeset"),
                    bindElement.attributeValue("relevant"), bindElement.attributeValue("calculate"), bindElement.attributeValue("type"),
                    bindElement.attributeValue("constraint"), bindElement.attributeValue("required"), bindElement.attributeValue("readonly"),
                    Dom4jUtils.getNamespaceContextNoDefault(bindElement), (LocationData) bindElement.getData());
            if (parent != null) {
                parent.addChild(modelBind);
                modelBind.setParent(parent);
            }
            binds.add(modelBind);
            handleBindContainer(bindElement, modelBind);
        }
    }

    private void addSchemaError(final Element elt, final String errMsg) {
        final InstanceData instDat = XFormsUtils.getLocalInstanceData(elt);
        final String em;
        if (errMsg == null) {
            // Looks like if n is an element and errMsg == null then the problem is missing 
            // character data.  No idea why MSV doesn't just give us the error msg itself.
            em = "Missing character data.";
        } else {
            em = errMsg;
        }
        instDat.addSchemaError(em);
    }

    private void addSchemaError(final Attribute att, final String errMsg) {
        // Looks like if n is an element and errMsg == null then the problem is missing character
        // data.
        final InstanceData instDat = XFormsUtils.getLocalInstanceData(att);
        instDat.addSchemaError(errMsg);
        // FIXME: The code below does nothing. Why is it here at all?
//        final Element elt = att.getParent();
//        final InstanceData eltInstDat = XFormsUtils.getLocalInstanceData(elt);
    }

    private Acceptor getChildAcceptor
            (final Element elt, final StartTagInfo si, final Acceptor acc, final StringRef sr) {
        Acceptor ret = acc.createChildAcceptor(si, null);
        if (ret == null) {
            ret = acc.createChildAcceptor(si, sr);
            addSchemaError(elt, sr.str);
        }
        return ret;
    }

    private void handleIDErrors(final IDConstraintChecker icc) {
        for (ErrorInfo errInf = icc.clearErrorInfo(); errInf != null; errInf = icc.clearErrorInfo()) {
            addSchemaError(errInf.element, errInf.message);
        }
    }

    private void validateElement (final Element elt, final Acceptor acc, final IDConstraintChecker icc) {
        final String nsURI = elt.getNamespaceURI();
        final String nam = elt.getName();
        final String qnam = elt.getQualifiedName();
        final List attLst = elt.attributes();
        final AttributesImpl atts = new AttributesImpl();
        // Note that we don't strip xxform:* atts here as doing so would cause confustion in
        // validateChildren
        for (final Iterator itr = attLst.iterator(); itr.hasNext();) {
            final Attribute att = (Attribute) itr.next();
            final String auri = att.getNamespaceURI();
            final String anam = att.getName();
            final String aQNam = att.getQualifiedName();
            final String val = att.getValue();
            atts.addAttribute(auri, anam, aQNam, null, val);
        }
        final StartTagInfo si = new StartTagInfo(nsURI, nam, qnam, atts, validationContext);

        final StringRef sr = new StringRef();
        final Acceptor chldAcc = getChildAcceptor(elt, si, acc, sr);
        icc.onNextAcceptorReady(si, chldAcc, elt);
        handleIDErrors(icc);

        final int charCare = chldAcc.getStringCareLevel();
        final DatatypeRef dref = new DatatypeRef();
        validateChildren(elt, chldAcc, si, charCare, icc, dref);
        if (!chldAcc.isAcceptState(null)) {
            chldAcc.isAcceptState(sr);
            addSchemaError(elt, sr.str);
        }
        icc.endElement(elt, dref.types);
        handleIDErrors(icc);
        if (!acc.stepForward(chldAcc, null)) {
            acc.stepForward(chldAcc, sr);
            addSchemaError(elt, sr.str);
        }
    }

    /**
     * Note that all of the attribs of elt should be in si.attributes.  If they are out of synch
     * it break the ability to access the attribs by index.
     */
    private void validateChildren
            (final Element elt, final Acceptor acc, final StartTagInfo si, final int charCare
             , final IDConstraintChecker icc, final DatatypeRef dref) {

        final int end = si.attributes.getLength();
        final StringRef sr = new StringRef();
        final DatatypeRef attDRef = new DatatypeRef();
        for (int i = 0; i < end; i++) {
            final String uri = si.attributes.getURI(i);
            if (XFormsConstants.XXFORMS_NAMESPACE_URI.equals(uri)) continue;
            final String nam = si.attributes.getLocalName(i);
            final String qNam = si.attributes.getQName(i);
            final String val = si.attributes.getValue(i);

            if (!acc.onAttribute2(uri, nam, qNam, val, si.context, null, attDRef)) {
                final Attribute att = elt.attribute(i);
                acc.onAttribute2(uri, nam, qNam, val, si.context, sr, (DatatypeRef) null);
                addSchemaError(att, sr.str);
            }
            final Attribute att = elt.attribute(i);
            icc.feedAttribute(acc, att, attDRef.types);
            handleIDErrors(icc);
        }
        if (!acc.onEndAttributes(si, null)) {
            acc.onEndAttributes(si, sr);
            addSchemaError(elt, sr.str);
        }
        for (final Iterator itr = elt.elementIterator(); itr.hasNext();) {
            final Element chld = (Element) itr.next();
            validateElement((Element) chld, acc, icc);
        }
        // If we just iterate over nodes, i.e. use nodeIterator() ) then validation of char data
        // ends up being incorrect.  Specifically elements of type xs:string end up being invalid
        // when they are empty. ( Which is wrong. )
        final String txt = elt.getText();
        switch (charCare) {
            case Acceptor.STRING_IGNORE:
                {
                    if (txt.length() > 0) {
                        addSchemaError(elt, sr.str);
                    }
                    dref.types = null;
                    break;
                }
            case Acceptor.STRING_PROHIBITED:
                {
                    final String trmd = txt.trim();
                    if (trmd.length() > 0) {
                        addSchemaError(elt, sr.str);
                    }
                    dref.types = null;
                    break;
                }
            case Acceptor.STRING_STRICT:
                {
                    if (!acc.onText2(txt, si.context, null, dref)) {
                        acc.onText2(txt, si.context, sr, null);
                        addSchemaError(elt, sr.str);
                    }
                    break;
                }
        }
    }

    /**
     * Return the default instance for this model, i.e. the first instance. Return null if there is
     * no instance in this model.
     */
    public XFormsInstance getDefaultInstance() {
        return (XFormsInstance) ((instances.size() > 0) ? instances.get(0) : null);
    }

    /**
     * Return all XFormsInstance objects for this model, in the order they appear in the model.
     */
    public List getInstances() {
        return instances;
    }

    /**
     * Return the number of instances in this model.
     */
    public int getInstanceCount() {
        return instanceIds.size();
    }

    /**
     * Return the XFormsInstance with given id, null if not found.
     */
    public XFormsInstance getInstance(String instanceId) {
        return (XFormsInstance) (instancesMap.get(instanceId));
    }

    /**
     * Return the XFormsInstance object containing the given node.
     */
    public XFormsInstance getInstanceForNode(Node node) {
        final Document document = node.getDocument();

        for (Iterator i = instances.iterator(); i.hasNext();) {
            final XFormsInstance currentInstance = (XFormsInstance) i.next();
            if (currentInstance.getDocument() == document)
                return currentInstance;
        }

        return null;
    }

    /**
     * Set an instance document for this model. There may be multiple instance documents. Each
     * instance document may have an associated id that identifies it.
     */
    public void setInstanceDocument(PipelineContext pipelineContext, int instancePosition, Document instanceDocument) {
        // Initialize containers if needed
        if (instances == null) {
            instances = Arrays.asList(new XFormsInstance[instanceIds.size()]);
            instancesMap = new HashMap(instanceIds.size());
        }
        // Prepare and set instance
        XFormsInstance newInstance = new XFormsInstance(pipelineContext, instanceDocument, this);
        instances.set(instancePosition, newInstance);

        // Create mapping instance id -> instance
        final String instanceId = (String) instanceIds.get(instancePosition);
        if (instanceId != null)
            instancesMap.put(instanceId, newInstance);
    }

    /**
     * Apply relevant and readonly binds only.
     */
    public void applyRelevantReadonlyBinds(final PipelineContext pipelineContext) {
        applyBinds(new BindRunner() {
            public void applyBind(ModelBind modelBind, DocumentWrapper documentWrapper) {
                handleRelevantReadonlyBinds(pipelineContext, modelBind, documentWrapper, this);
            }
        });
    }

    /**
     * Apply schema validation only.
     */
    private void applySchema() {
        if (!isSkipInstanceSchemaValidation() && schemaGrammar != null) {
            final REDocumentDeclaration rdd = new REDocumentDeclaration(schemaGrammar);
            final Acceptor acc = rdd.createAcceptor();
            final Element relt = getDefaultInstance().getDocument().getRootElement();
            // TODO: should probably iterate over all instances!
            final IDConstraintChecker icc = new IDConstraintChecker();

            validateElement(relt, acc, icc);
            icc.endDocument();
            handleIDErrors(icc);
        }
    }

    private static interface BindRunner {
        public void applyBind(ModelBind modelBind, DocumentWrapper documentWrapper);
    }

    /**
     * Apply binds.
     */
    private void applyBinds(BindRunner bindRunner) {

        if (binds == null)
            resetBinds();

        for (Iterator i = binds.iterator(); i.hasNext();) {
            final ModelBind modelBind = (ModelBind) i.next();
            try {
                // Create XPath evaluator for this bind
                final DocumentWrapper documentWrapper = new DocumentWrapper(getDefaultInstance().getDocument(), null);
                bindRunner.applyBind(modelBind, documentWrapper);
            } catch (final Exception e) {
                throw new ValidationException(e, modelBind.getLocationData());
            }
        }
    }

    /**
     * Load XForms model schemas.
     */
    private void loadSchemas(final PipelineContext pipelineContext, Element modelElement) {
        if (!isSkipInstanceSchemaValidation()) {
            String schemaURI = modelElement.attributeValue("schema");
            if (schemaURI != null) {
                // Resolve URI
                String systemID = ((LocationData) modelElement.getData()).getSystemID();
                try {
                    schemaURI = URLFactory.createURL(systemID, schemaURI).toString();
                } catch (MalformedURLException e) {
                    throw new OXFException(e);
                }
                // Load associated grammar
                schemaGrammar = loadGrammar(pipelineContext, schemaURI);
            }
        }
    }

    /**
     * Load and cache a Grammar for a given schema URI.
     */
    private Grammar loadGrammar(final PipelineContext pipelineContext, final String schemaURI) {
        try {
            final java.net.URL url = URLFactory.createURL(schemaURI);
            final Long modTim = NetUtils.getLastModified(url, (Long) null);

            final Cache cache = ObjectCache.instance();
            final SchemaKey schmKey = new SchemaKey(url);

            final SchemaInfo schmInf;
            {
                final Object cached = cache.findValid(pipelineContext, schmKey, modTim);
                schmInf = cached == null ? null : (SchemaInfo) cached;
            }

            // Grammar is thread safe while REDocumentDeclaration is not so cache grammar
            // instead of REDocumentDeclaration
            final Grammar grmr;
            if (schmInf == null || !schmInf.includesUpToDate()) {
                final SchemaInfo newSchmInf = new SchemaInfo();

                final InputSource is = XMLUtils.ENTITY_RESOLVER.resolveEntity("", schemaURI);
                final MSVGrammarReaderController cntrlr = new MSVGrammarReaderController(schemaURI, newSchmInf);
                final SAXParserFactory fctry = XMLUtils.createSAXParserFactory(false);

                grmr = GrammarLoader.loadSchema(is, cntrlr, fctry);
                newSchmInf.setGrammar(grmr);
                cache.add(pipelineContext, schmKey, modTim, newSchmInf);
            } else {
                grmr = schmInf.getGrammar();
            }
            return grmr;
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    private void handleRelevantReadonlyBinds(final PipelineContext pipelineContext, final ModelBind modelBind, final DocumentWrapper documentWrapper, BindRunner bindRunner) {
        // Handle relevant
        if (modelBind.getRelevant() != null) {
            iterateNodeSet(pipelineContext, documentWrapper, modelBind, new NodeHandler() {
                public void handleNode(Node node) {
                    // Evaluate "relevant" XPath expression on this node
                    String xpath = "boolean(" + modelBind.getRelevant() + ")";
                    PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext,
                            documentWrapper.wrap(node), xpath, modelBind.getNamespaceMap(), null,
                            xformsFunctionLibrary, modelBind.getLocationData().getSystemID());
                    try {
                        boolean relevant = ((Boolean) expr.evaluateSingle()).booleanValue();
                        // Mark node
                        InstanceData instanceData = XFormsUtils.getLocalInstanceData((Node) node);
                        instanceData.getRelevant().set(relevant);
                    } catch (XPathException e) {
                        throw new ValidationException(e.getMessage() + " when evaluating '" + xpath + "'", modelBind.getLocationData());
                    } finally {
                        if (expr != null)
                            expr.returnToPool();
                    }
                }
            });
        }

        // Handle readonly
        if (modelBind.getReadonly() != null) {
            // The bind has a readonly attribute
            iterateNodeSet(pipelineContext, documentWrapper, modelBind, new NodeHandler() {
                public void handleNode(Node node) {
                    // Evaluate "readonly" XPath expression on this node
                    String xpath = "boolean(" + modelBind.getReadonly() + ")";
                    PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext,
                            documentWrapper.wrap(node), xpath, modelBind.getNamespaceMap(), null,
                            xformsFunctionLibrary, modelBind.getLocationData().getSystemID());

                    try {
                        boolean readonly = ((Boolean) expr.evaluateSingle()).booleanValue();

                        // Mark node
                        InstanceData instanceData = XFormsUtils.getLocalInstanceData((Node) node);
                        instanceData.getReadonly().set(readonly);
                    } catch (XPathException e) {
                        throw new ValidationException(e.getMessage() + " when evaluating '" + xpath + "'", modelBind.getLocationData());
                    } finally {
                        if (expr != null)
                            expr.returnToPool();
                    }
                }
            });
        } else if (modelBind.getCalculate() != null) {
            // The bind doesn't have a readonly attribute, but has a calculate: set readonly to true()
            iterateNodeSet(pipelineContext, documentWrapper, modelBind, new NodeHandler() {
                public void handleNode(Node node) {
                    // Mark node
                    InstanceData instanceData = XFormsUtils.getLocalInstanceData((Node) node);
                    instanceData.getReadonly().set(true);
                }
            });
        }

        handleChildrenBinds(pipelineContext, modelBind, documentWrapper, bindRunner);
    }

    private void handleValidationBind(final PipelineContext pipelineContext, final ModelBind modelBind, final DocumentWrapper documentWrapper, BindRunner bindRunner) {

        // Handle Type constraint
        if (modelBind.getType() != null) {

            // Need an evaluator to check and convert type below
            final XPathEvaluator xpathEvaluator;
            try {
                xpathEvaluator= new XPathEvaluator(documentWrapper);
                StandaloneContext context = (StandaloneContext) xpathEvaluator.getStaticContext();
                for (Iterator j = modelBind.getNamespaceMap().keySet().iterator(); j.hasNext();) {
                    String prefix = (String) j.next();
                    context.declareNamespace(prefix, (String) modelBind.getNamespaceMap().get(prefix));
                }
            } catch (Exception e) {
                throw new OXFException(e);
            }

            iterateNodeSet(pipelineContext, documentWrapper, modelBind, new NodeHandler() {
                public void handleNode(Node node) {
                    if (XFormsUtils.getLocalInstanceData(node).getValid().get()) {

                        // Get type information
                        int requiredType = -1;
                        boolean foundType = false;
                        {
                            String type = modelBind.getType();
                            int prefixPosition = type.indexOf(':');
                            if (prefixPosition > 0) {
                                String prefix = type.substring(0, prefixPosition);
                                String namespace = (String) modelBind.getNamespaceMap().get(prefix);
                                if (namespace == null)
                                    throw new ValidationException("Namespace not declared for prefix '" + prefix + "'",
                                            modelBind.getLocationData());
                                ItemType itemType = Type.getBuiltInItemType((String) modelBind.getNamespaceMap().get(prefix),
                                        type.substring(prefixPosition + 1));
                                if (itemType != null) {
                                    requiredType = itemType.getPrimitiveType();
                                    foundType = true;
                                }
                            }
                        }
                        if (!foundType)
                            throw new ValidationException("Invalid type '" + modelBind.getType() + "'",
                                    modelBind.getLocationData());

                        // Pass-through the type value
                        InstanceData instanceData = XFormsUtils.getLocalInstanceData((Node) node);
                        instanceData.getType().set(requiredType);

                        // Try to perform casting
                        String nodeStringValue = node.getStringValue();
                        if (XFormsUtils.getLocalInstanceData(node).getRequired().get() || nodeStringValue.length() != 0) {
                            try {
                                StringValue stringValue = new StringValue(nodeStringValue);
                                XPathContext xpContext = new XPathContextMajor(stringValue, xpathEvaluator.getStaticContext().getConfiguration());
                                stringValue.convert(requiredType, xpContext);
                                markValidity(true, node, modelBind.getId());
                            } catch (XPathException e) {
                                markValidity(false, node, modelBind.getId());
                            }
                        }
                    }
                }
            });
        }

        // Handle XPath constraint
        if (modelBind.getConstraint() != null) {
            iterateNodeSet(pipelineContext, documentWrapper, modelBind, new NodeHandler() {
                public void handleNode(Node node) {
                    // Evaluate constraint
                    String xpath = "boolean(" + modelBind.getConstraint() + ")";
                    PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext,
                            documentWrapper.wrap(node), xpath, modelBind.getNamespaceMap(), null,
                            xformsFunctionLibrary, modelBind.getLocationData().getSystemID());

                    try {
                        Boolean valid = (Boolean) expr.evaluateSingle();
                        markValidity(valid.booleanValue(), node, modelBind.getId());
                    } catch (XPathException e) {
                        throw new ValidationException(e.getMessage() + " when evaluating '" + xpath + "'", modelBind.getLocationData());
                    } finally {
                        if (expr != null)
                            expr.returnToPool();
                    }
                }
            });
        }

        // Handle required
        if (modelBind.getRequired() != null) {
            iterateNodeSet(pipelineContext, documentWrapper, modelBind, new NodeHandler() {
                public void handleNode(Node node) {
                    // Evaluate "required" XPath expression on this node
                    String xpath = "boolean(" + modelBind.getRequired() + ")";
                    PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext,
                            documentWrapper.wrap(node), xpath, modelBind.getNamespaceMap(), null,
                            xformsFunctionLibrary, modelBind.getLocationData().getSystemID());

                    try {
                        boolean required = ((Boolean) expr.evaluateSingle()).booleanValue();
                        // Mark node
                        InstanceData instanceData = XFormsUtils.getLocalInstanceData((Node) node);
                        instanceData.getRequired().set(required);

                        // If required, check the string value is not empty
                        markValidity(!required || node.getStringValue().length() > 0, node, modelBind.getId());
                    } catch (XPathException e) {
                        throw new ValidationException(e.getMessage() + " when evaluating '" + xpath + "'", modelBind.getLocationData());
                    } finally {
                        if (expr != null)
                            expr.returnToPool();
                    }
                }
            });
        }

        handleChildrenBinds(pipelineContext, modelBind, documentWrapper, bindRunner);
    }

    private void handleCalculateBind(final PipelineContext pipelineContext, final ModelBind modelBind, final DocumentWrapper documentWrapper, BindRunner bindRunner) {
        if (modelBind.getCalculate() != null) {
            iterateNodeSet(pipelineContext, documentWrapper, modelBind, new NodeHandler() {
                public void handleNode(Node node) {
                    if (node instanceof Element) {
                        // Compute calculated value
                        PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext,
                                documentWrapper.wrap(node), "string(" + modelBind.getCalculate() + ")", modelBind.getNamespaceMap(), null,
                                xformsFunctionLibrary, modelBind.getLocationData().getSystemID());
                        try {
                            final Object result = expr.evaluateSingle();
                            final String stringResult = result.toString(); // even with string(), the result may not be a Java String object
                            // Place in element
                            Element elt = (Element) node;
                            Dom4jUtils.clearElementContent(elt);
                            elt.add(Dom4jUtils.createText(stringResult));
                        } catch (XPathException e) {
                            throw new ValidationException(e.getMessage() + " when evaluating '" + modelBind.getCalculate() + "'", modelBind.getLocationData());
                        } finally {
                            if (expr != null)
                                expr.returnToPool();
                        }

                    } else {
                        // Compute calculated value and place in attribute
                        String xpath = "string(" + modelBind.getCalculate() + ")";
                        PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext,
                                documentWrapper.wrap(node), xpath, modelBind.getNamespaceMap(), null,
                                xformsFunctionLibrary, modelBind.getLocationData().getSystemID());
                        try {
                            String value = (String) expr.evaluateSingle();
                            XFormsInstance.setValueForNode(node, value);
                        } catch (XPathException e) {
                            throw new ValidationException(e.getMessage() + " when evaluating '" + xpath + "'", modelBind.getLocationData());
                        } finally {
                            if (expr != null)
                                expr.returnToPool();
                        }
                    }
                }
            });
        }

        handleChildrenBinds(pipelineContext, modelBind, documentWrapper, bindRunner);
    }

    private void handleChildrenBinds(final PipelineContext pipelineContext, final ModelBind modelBind, final DocumentWrapper documentWrapper, BindRunner bindRunner) {
        // Handle children binds
        PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext,
                modelBind.getCurrentNode() == null ? documentWrapper : documentWrapper.wrap(modelBind.getCurrentNode()),
                modelBind.getNodeset(),
                modelBind.getNamespaceMap(),
                null, xformsFunctionLibrary, modelBind.getLocationData().getSystemID());
        try {
            List nodeset = expr.evaluate();
            for (Iterator j = nodeset.iterator(); j.hasNext();) {
                Node node = (Node) j.next();
                for (Iterator childIterator = modelBind.getChildrenIterator(); childIterator.hasNext();) {
                    ModelBind child = (ModelBind) childIterator.next();
                    child.setCurrentNode(node);
                    bindRunner.applyBind(child, documentWrapper);
                }
            }
        } catch (XPathException e) {
            throw new ValidationException(e.getMessage() + " when evaluating '" + modelBind.getNodeset() + "'", modelBind.getLocationData());
        } finally {
            if (expr != null)
                expr.returnToPool();
        }
    }

    private void iterateNodeSet(PipelineContext pipelineContext, DocumentWrapper documentWrapper,
                                ModelBind modelBind, NodeHandler nodeHandler) {
        PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext,
                modelBind.getCurrentNode() == null ? documentWrapper : documentWrapper.wrap(modelBind.getCurrentNode()),
                modelBind.getNodeset(),
                modelBind.getNamespaceMap(),
                null, xformsFunctionLibrary);
        try {
            List nodeset = expr.evaluate();
            for (Iterator j = nodeset.iterator(); j.hasNext();) {
                Node node = (Node) j.next();
                nodeHandler.handleNode(node);
            }
        } catch (XPathException e) {
            throw new ValidationException(e.getMessage() + " when evaluating '" + modelBind.getNodeset() + "'", modelBind.getLocationData());
        } finally {
            if (expr != null)
                expr.returnToPool();
        }

    }

    private interface NodeHandler {
        void handleNode(Node node);
    }

    /**
     * Marks the given node as invalid by:
     * <ul>
     * <li>setting invalid flag on the node InstanceData</li>
     * <li>adding an attribute xxforms:error="message"</li>
     * </ul>
     */
    private void markValidity(boolean valid, Node node, String id) {
        InstanceData instanceData = XFormsUtils.getLocalInstanceData(node);
        if (instanceData.getValid().get() || !valid) {
            instanceData.getValid().set(valid);
        }
        if (id != null && !valid)
            instanceData.setInvalidBindIds(instanceData.getInvalidBindIds() == null
                    ? id : instanceData.getInvalidBindIds() + " " + id);
    }

    public String getModelId() {
        return modelId;
    }

    public List getBindNodeset(PipelineContext pipelineContext, ModelBind bind) {
        // Get a list of parents, ordered by grandfather first
        List parents = new ArrayList();
        parents.add(bind);
        ModelBind parent = bind;
        while ((parent = parent.getParent()) != null) {
            parents.add(parent);
        }
        Collections.reverse(parents);

        // Find the final node
        final List nodeset = new ArrayList();
        final XFormsInstance defaultInstance = getDefaultInstance();
        nodeset.add(defaultInstance.getDocument());
        for (Iterator i = parents.iterator(); i.hasNext();) {
            ModelBind current = (ModelBind) i.next();
            List currentModelBindResults = new ArrayList();
            for (Iterator j = nodeset.iterator(); j.hasNext();) {
                Node node = (Node) j.next();
                // Execute XPath expresssion
                currentModelBindResults.addAll(defaultInstance.evaluateXPath(pipelineContext, node, current.getNodeset(),
                        current.getNamespaceMap(), null, xformsFunctionLibrary, current.getLocationData().getSystemID()));
            }
            nodeset.addAll(currentModelBindResults);
            // Last iteration of i: remove all except last
            if (!i.hasNext())
                nodeset.retainAll(currentModelBindResults);
        }
        return nodeset;
    }

    public ModelBind getModelBindById(String id) {

        if (binds == null)
            resetBinds();

        for (Iterator i = binds.iterator(); i.hasNext();) {
            ModelBind bind = (ModelBind) i.next();
            ModelBind result = getModelBindByIdWorker(bind, id);
            if (result != null)
                return result;
        }
        return null;
    }

    private ModelBind getModelBindByIdWorker(ModelBind parent, String id) {
        if (id.equals(parent.getId()))
            return parent;
        // Look in children
        for (Iterator j = parent.getChildrenIterator(); j.hasNext();) {
            ModelBind child = (ModelBind) j.next();
            ModelBind bind = getModelBindByIdWorker(child, id);
            if (bind != null)
                return bind;
        }
        return null;
    }

    /**
     * Return whether XForms instance validation should be skipped based on configuration
     * properties.
     */
    private boolean isSkipInstanceSchemaValidation() {
        OXFProperties.PropertySet propertySet = OXFProperties.instance().getPropertySet();
        Boolean schmVldatdObj =  propertySet.getBoolean(XFormsConstants.XFORMS_VALIDATION_PROPERTY, true);
        return !schmVldatdObj.booleanValue();
    }

    public void dispatchEvent(final PipelineContext pipelineContext, org.orbeon.oxf.xforms.event.XFormsEvent xformsEvent) {
        final String eventName = xformsEvent.getEventName();
        if (XFormsEvents.XXFORMS_INITIALIZE_STATE.equals(eventName)) {
            // Internal event to restore state

            final Element modelElement = modelDocument.getRootElement();

            loadSchemas(pipelineContext, modelElement);
            applyRelevantReadonlyBinds(pipelineContext);
            dispatchEvent(pipelineContext, new XFormsRevalidateEvent(this, false));

        } else if (XFormsEvents.XFORMS_MODEL_CONSTRUCT.equals(eventName)) {
            // 4.2.1 The xforms-model-construct Event
            // Bubbles: Yes / Cancelable: No / Context Info: None

            final Element modelElement = modelDocument.getRootElement();

            // 1. All XML Schemas loaded (throws xforms-link-exception)

            // TODO: support multiple schemas
            // Get schema URI
            loadSchemas(pipelineContext, modelElement);
            // TODO: throw exception event

            // 2. Create XPath data model from instance (inline or external) (throws xforms-link-exception)
            //    Instance may not be specified.

            // TODO: support external instance
            if (instances == null) {
                // Build initial instance document
                List instanceContainers = modelElement.elements(new QName("instance", XFormsConstants.XFORMS_NAMESPACE));
                if (instanceContainers.size() > 0) {
                    // Support multiple instances
                    int instancePosition = 0;
                    for (Iterator i = instanceContainers.iterator(); i.hasNext(); instancePosition++) {
                        Element instanceContainer = (Element) i.next();
                        Document instanceDocument = Dom4jUtils.createDocumentCopyParentNamespaces((Element) instanceContainer.elements().get(0));
                        setInstanceDocument(pipelineContext, instancePosition, instanceDocument);
                    }
                }
            }
            // TODO: throw exception event

            // Call special listener to update instance
            if (instanceConstructListener != null) {
                for (Iterator i = getInstances().iterator(); i.hasNext();) {
                    instanceConstructListener.updateInstance((XFormsInstance) i.next());
                }
            }

            // 3. P3P (N/A)
            // 4. Instance data is constructed. Evaluate binds:
            //    a. Evaluate nodeset
            //    b. Apply model item properties on nodes
            //    c. Throws xforms-binding-exception if the node has already model item property with same name
            // TODO: a, b, c xxx

            // 5. xforms-rebuild, xforms-recalculate, xforms-revalidate
            dispatchEvent(pipelineContext, new XFormsRebuildEvent(this));
            dispatchEvent(pipelineContext, new XFormsRecalculateEvent(this));
            dispatchEvent(pipelineContext, new XFormsRevalidateEvent(this, false));

        } else if (XFormsEvents.XFORMS_MODEL_CONSTRUCT_DONE.equals(eventName)) {
            // 4.2.2 The xforms-model-construct-done Event
            // Bubbles: Yes / Cancelable: No / Context Info: None

            // TODO: if instance exists (for now it does!), check that controls can bind, otherwise control must be "irrelevant"
            // TODO: implicit lazy instance construction

        } else if (XFormsEvents.XFORMS_READY.equals(eventName)) {
            // 4.2.3 The xforms-ready Event
            // Bubbles: Yes / Cancelable: No / Context Info: None
            // The default action for this event results in the following: None
        } else if (XFormsEvents.XFORMS_MODEL_DESTRUCT.equals(eventName)) {
            // 4.2.4 The xforms-model-destruct Event
            // Bubbles: No / Cancelable: No / Context Info: None
            // The default action for this event results in the following: None
        } else if (XFormsEvents.XFORMS_REBUILD.equals(eventName)) {
            // 4.3.7 The xforms-rebuild Event
            // Bubbles: Yes / Cancelable: Yes / Context Info: None

            // TODO: rebuild computational dependency data structures
        } else if (XFormsEvents.XFORMS_RECALCULATE.equals(eventName)) {
            // 4.3.6 The xforms-recalculate Event
            // Bubbles: Yes / Cancelable: Yes / Context Info: None

            applyBinds(new BindRunner() {
                public void applyBind(ModelBind modelBind, DocumentWrapper documentWrapper) {
                    handleCalculateBind(pipelineContext, modelBind, documentWrapper, this);
                }
            });

            // Here we assume that we update those after recaculate, because recalculate is always
            // called after values are changed in the instance - may have to be changed...
            applyRelevantReadonlyBinds(pipelineContext);

        } else if (XFormsEvents.XFORMS_REVALIDATE.equals(eventName)) {
            // 4.3.5 The xforms-revalidate Event
            // Bubbles: Yes / Cancelable: Yes / Context Info: None

            XFormsRevalidateEvent xformsRevalidateEvent = (XFormsRevalidateEvent) xformsEvent;

            // Clear all existing errors on instance
            for (Iterator i = instances.iterator(); i.hasNext();) {
                XFormsUtils.updateInstanceData(((XFormsInstance) i.next()).getDocument(), new XFormsUtils.InstanceWalker() {
                    public void walk(Node node, InstanceData instanceData) {
                        if (instanceData != null)
                            instanceData.clearSchemaErrors();
                    }
                });
            }

            // Run validation
            applySchema();
            applyBinds(new BindRunner() {
                public void applyBind(ModelBind modelBind, DocumentWrapper documentWrapper) {
                    handleValidationBind(pipelineContext, modelBind, documentWrapper, this);
                }
            });

            // Send events if needed
            if (xformsRevalidateEvent.isSendEvents()) {
                // TODO: dispatch events
            }

        } else if (XFormsEvents.XFORMS_REFRESH.equals(eventName)) {
            // 4.3.4 The xforms-refresh Event
            // Bubbles: Yes / Cancelable: Yes / Context Info: None

            // Must ask controls to refresh for this model
            if (xFormsContainingDocument.getXFormsControls() != null) {
                xFormsContainingDocument.getXFormsControls().refreshForModel(pipelineContext, this);
            }

        } else if (XFormsEvents.XFORMS_RESET.equals(eventName)) {
            // 4.3.8 The xforms-reset Event
            // Bubbles: Yes / Cancelable: Yes / Context Info: None

            // TODO
            // "The instance data is reset to the tree structure and values it had immediately
            // after having processed the xforms-ready event."

            // "Then, the events xforms-rebuild, xforms-recalculate, xforms-revalidate and
            // xforms-refresh are dispatched to the model element in sequence."
            dispatchEvent(pipelineContext, new XFormsRebuildEvent(XFormsModel.this));
            dispatchEvent(pipelineContext, new XFormsRecalculateEvent(XFormsModel.this));
            dispatchEvent(pipelineContext, new XFormsRevalidateEvent(XFormsModel.this, true));
            dispatchEvent(pipelineContext, new XFormsRefreshEvent(XFormsModel.this));

        } else if (XFormsEvents.XFORMS_LINK_ERROR.equals(eventName)) {
            // 4.5.2 The xforms-link-error Event
            // Bubbles: Yes / Cancelable: No / Context Info: The URI that failed to load (xsd:anyURI)

            //callEventHandlers(pipelineContext, xformsEvent, eventName, xformsEvent.getControlElement());

            // The default action for this event results in the following: None; notification event only.
            //XFormsLinkError xFormsLinkError = (XFormsLinkError) xformsEvent;

            // TODO

        } else if (XFormsEvents.XFORMS_COMPUTE_EXCEPTION.equals(eventName)) {
            // 4.5.4 The xforms-compute-exception Event
            // Bubbles: Yes / Cancelable: No / Context Info: Implementation-specific error string.
            // The default action for this event results in the following: Fatal error.

            // TODO

        } else {
            throw new OXFException("Invalid event dispatched: " + eventName);
        }
    }

    /**
     * This class is cloneable.
     */
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    public static interface InstanceConstructListener {
        public void updateInstance(XFormsInstance instance);
    }

    public void setInstanceConstructListener(InstanceConstructListener instanceConstructListener) {
        this.instanceConstructListener = instanceConstructListener;
    }
}
