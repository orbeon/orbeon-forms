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
package org.orbeon.oxf.xforms;

import org.orbeon.oxf.common.OrbeonLocationException;
import org.orbeon.oxf.common.Version;
import org.orbeon.oxf.externalcontext.ExternalContext;
import org.orbeon.oxf.logging.LifecycleLogger;
import org.orbeon.oxf.util.SecureUtils;
import org.orbeon.oxf.xforms.action.XFormsAPI;
import org.orbeon.oxf.xforms.analysis.DumbXPathDependencies;
import org.orbeon.oxf.xforms.analysis.PathMapXPathDependencies;
import org.orbeon.oxf.xforms.analysis.XPathDependencies;
import org.orbeon.oxf.xforms.control.Controls;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl;
import org.orbeon.oxf.xforms.processor.XFormsURIResolver;
import org.orbeon.oxf.xforms.state.ControlState;
import org.orbeon.oxf.xforms.state.DynamicState;
import org.orbeon.oxf.xforms.state.XFormsState;
import org.orbeon.oxf.xforms.state.XFormsStaticStateCache;
import org.orbeon.oxf.xforms.submission.AsynchronousSubmissionManager;
import org.orbeon.oxf.xforms.xbl.Scope;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.saxon.functions.FunctionLibrary;
import scala.Option;
import scala.collection.Seq;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents an XForms containing document.
 *
 * The containing document:
 *
 * - Is the container for root XForms models (including multiple instances)
 * - Contains XForms controls
 * - Handles event handlers hierarchy
 */
public class XFormsContainingDocument extends XFormsContainingDocumentSupport {

    private String uuid;        // UUID of this document
    private long sequence = 1;  // sequence number of changes to this document

    private SAXStore lastAjaxResponse; // last Ajax response for retry feature

    // Global XForms function library
    private FunctionLibrary functionLibrary = null;

    // Whether this document is currently being initialized
    private boolean initializing;

    // Transient URI resolver for initialization
    private XFormsURIResolver uriResolver;

    // Transient OutputStream for xf:submission[@replace = 'all'], or null if not available
    private ExternalContext.Response response;

    // Asynchronous submission manager
    private AsynchronousSubmissionManager asynchronousSubmissionManager;

    // A document refers to the static state and controls
    private final XFormsStaticState staticState;
    private final StaticStateGlobalOps staticOps;
    private XFormsControls xformsControls;

    // Other state
    private Set<String> pendingUploads;

    // Client state
    private Set<String> controlsStructuralChanges;

    private final XPathDependencies xpathDependencies;

    /**
     * Return the global function library.
     */
    public FunctionLibrary getFunctionLibrary() {
        return functionLibrary;
    }

    /**
     * Create an XFormsContainingDocument from an XFormsStaticState object.
     *
     * Used by XFormsToXHTML.
     *
     * @param staticState static state object
     * @param uriResolver URIResolver for loading instances during initialization (and possibly more, such as schemas and "GET" submissions upon initialization)
     * @param response    optional response for handling replace="all" during initialization
     * @param initialize  initialize document (false for testing only)
     */
    public XFormsContainingDocument(XFormsStaticState staticState, XFormsURIResolver uriResolver, ExternalContext.Response response, boolean initialize) {
        super(false);

        // Create UUID for this document instance
        this.uuid = SecureUtils.randomHexId();

        // Initialize request information
        initializeRequestInformation();
        initializePathMatchers();

        // Initialize function library
        this.functionLibrary = staticState.functionLibrary();

        indentedLogger().startHandleOperation("initialization", "creating new ContainingDocument (static state object provided).", "uuid", this.uuid);
        {
            // Remember static state
            this.staticState = staticState;
            this.staticOps = new StaticStateGlobalOps(staticState.topLevelPart());

            if (! isNoUpdatesStatic())  // attempt to ignore oxf:xforms-submission
                LifecycleLogger.eventAssumingRequestJava("xforms", "new form session", new String[] { "uuid", uuid });

            this.xpathDependencies =
                (Version.isPE() && this.staticState.isXPathAnalysis()) ? new PathMapXPathDependencies(this) : new DumbXPathDependencies();

            // Remember parameters used during initialization
            this.uriResolver = uriResolver;
            this.response = response;
            this.initializing = true;

            // Initialize the containing document
            if (initialize) {
                try {
                    initialize();
                } catch (Exception e) {
                    throw OrbeonLocationException.wrapException(e, new ExtendedLocationData(null, "initializing XForms containing document"));
                }
            }
        }
        indentedLogger().endHandleOperation();
    }

    // This is called upon the first creation of the XForms engine
    private void initialize() {

        // Scope the containing document for the XForms API
        XFormsAPI.withContainingDocumentJava(this, new Runnable() {
            public void run() {
                // Create XForms controls and models
                createControlsAndModels();

                // Group all xforms-model-construct-done and xforms-ready events within a single outermost action handler in
                // order to optimize events
                // Perform deferred updates only for xforms-ready
                startOutermostActionHandler();
                {
                    // Initialize models
                    initializeModels();

                    // After initialization, some async submissions might be running
                    processCompletedAsynchronousSubmissions(true, true);
                }
                // End deferred behavior
                endOutermostActionHandler();

                processDueDelayedEvents(false);
            }
        });
    }

    /**
     * Restore an XFormsContainingDocument from XFormsState only.
     *
     * Used by XFormsStateManager.
     *
     * @param xformsState       XFormsState containing static and dynamic state
     * @param disableUpdates    whether to disable updates (for recreating initial document upon browser back)
     */
    public XFormsContainingDocument(XFormsState xformsState, boolean disableUpdates, boolean forceEncryption) {
        super(disableUpdates);

        // 1. Restore the static state
        {
            final scala.Option<String> staticStateDigest = xformsState.staticStateDigest();

            if (staticStateDigest.isDefined()) {
                final XFormsStaticState cachedState = XFormsStaticStateCache.getDocumentJava(staticStateDigest.get());
                if (cachedState != null) {
                    // Found static state in cache
                    indentedLogger().logDebug("", "found static state by digest in cache");
                    this.staticState = cachedState;
                } else {
                    // Not found static state in cache, create static state from input
                    indentedLogger().logDebug("", "did not find static state by digest in cache");
                    indentedLogger().startHandleOperation("initialization", "restoring static state");
                    this.staticState = XFormsStaticStateImpl.restore(staticStateDigest, xformsState.staticState().get(), forceEncryption);
                    indentedLogger().endHandleOperation();

                    // Store in cache
                    XFormsStaticStateCache.storeDocument(this.staticState);
                }

                assert this.staticState.isServerStateHandling();
            } else {
                // Not digest provided, create static state from input
                indentedLogger().logDebug("", "did not find static state by digest in cache");
                this.staticState = XFormsStaticStateImpl.restore(staticStateDigest, xformsState.staticState().get(), forceEncryption);

                assert this.staticState.isClientStateHandling();
            }

            this.staticOps = new StaticStateGlobalOps(staticState.topLevelPart());
            this.xpathDependencies =
                (Version.isPE() && this.staticState.isXPathAnalysis()) ? new PathMapXPathDependencies(this) : new DumbXPathDependencies();

            this.functionLibrary = staticState.functionLibrary();
        }

        // 2. Restore the dynamic state
        indentedLogger().startHandleOperation("initialization", "restoring containing document");
        try {
            restoreDynamicState(xformsState.dynamicState().get());
        } catch (Exception e) {
            throw OrbeonLocationException.wrapException(e, new ExtendedLocationData(null, "re-initializing XForms containing document"));
        }
        indentedLogger().endHandleOperation();
    }

    private void restoreDynamicState(final DynamicState dynamicState) {

        this.uuid = dynamicState.uuid();
        this.sequence = dynamicState.sequence();

        indentedLogger().logDebug("initialization", "restoring dynamic state for UUID", "UUID", this.uuid, "sequence", Long.toString(this.sequence));

        // Restore request information
        restoreRequestInformation(dynamicState);
        restorePathMatchers(dynamicState);

        // Restore other encoded objects
        restoreDelayedEvents(dynamicState);
        this.pendingUploads = new HashSet<String>(dynamicState.decodePendingUploadsJava()); // make copy as must be mutable
        this.lastAjaxResponse = dynamicState.decodeLastAjaxResponseJava();

        // Scope the containing document for the XForms API
        XFormsAPI.withContainingDocumentJava(this, new Runnable() {
            public void run() {
                Controls.withDynamicStateToRestoreJava(dynamicState.decodeInstancesControls(), new Runnable() {
                    public void run() {
                        // Restore models state
                        // Create XForms controls and models
                        createControlsAndModels();

                        // Restore top-level models state, including instances
                        restoreModelsState(false);

                        // Restore controls state
                        // Store serialized control state for retrieval later
                        xformsControls.createControlTree(Controls.restoringControls());

                        // Once the control tree is rebuilt, restore focus if needed
                        if (dynamicState.decodeFocusedControlJava() != null)
                            xformsControls.setFocusedControl(xformsControls.getCurrentControlTree().findControl(dynamicState.decodeFocusedControlJava()));
                    }
                });
            }
        });
    }

    @Override
    public PartAnalysis partAnalysis() {
        return staticState.topLevelPart();
    }

    public XFormsURIResolver getURIResolver() {
        return uriResolver;
    }

    public String getUUID() {
        return uuid;
    }

    public void updateChangeSequence() {
        sequence++;
    }

    public SAXStore getLastAjaxResponse() {
        return lastAjaxResponse;
    }

    public boolean isInitializing() {
        return initializing;
    }

    /**
     * Whether the document is currently in a mode where it must remember differences. This is the case when:
     *
     * - the document is currently handling an update (as opposed to initialization)
     * - the property "no-updates" is false (the default)
     * - the document is
     *
     * @return  true iif the document must handle differences
     */
    public boolean isHandleDifferences() {
        return ! initializing && supportUpdates();
    }

    /**
     * Return the controls.
     */
    public XFormsControls getControls() {
        return xformsControls;
    }

    public XFormsControl getControlByEffectiveId(String effectiveId) {
        return xformsControls.getObjectByEffectiveId(effectiveId);
    }

    public scala.Option<XFormsControl> findControlByEffectiveId(String effectiveId) {
        return scala.Option.apply(getControlByEffectiveId(effectiveId));
    }

    /**
     * Return dependencies implementation.
     */
    public final XPathDependencies getXPathDependencies() {
        return xpathDependencies;
    }

    /**
     * Whether the document is dirty since the last request.
     *
     * @return  whether the document is dirty since the last request
     */
    public boolean isDirtySinceLastRequest() {
        return xformsControls.isDirtySinceLastRequest();
    }

    /**
     * Return the static state of this document.
     */
    public XFormsStaticState getStaticState() {
        return staticState;
    }

    public StaticStateGlobalOps getStaticOps() {
        return staticOps;
    }

    /**
     * Get object with the effective id specified.
     *
     * @param effectiveId   effective id of the target
     * @return              object, or null if not found
     */
    @Override
    public Option<XFormsObject> findObjectByEffectiveId(String effectiveId) {

        // Search in controls first because that's the fast way
        {
            final XFormsObject resultObject = getControlByEffectiveId(effectiveId);
            if (resultObject != null)
                return scala.Option.apply(resultObject);
        }

        // Search in parent (models and this)
        {
            final scala.Option<XFormsObject> resultObject = super.findObjectByEffectiveId(effectiveId);
            if (resultObject.isDefined())
                return resultObject;
        }

        // Check container id
        // TODO: This should no longer be needed since we have a root control, right? In which case, the document would
        // no longer need to be an XFormsObject.
        if (effectiveId.equals(getEffectiveId()))
            return scala.Option.apply(this);

        return scala.Option.apply(null);
    }

    /**
     * Clear current client state.
     */
    private void clearClientState() {

        assert !initializing;
        assert response == null;
        assert uriResolver == null;

        clearRequestStats();
        clearTransientState();

        if (this.controlsStructuralChanges != null)
            this.controlsStructuralChanges.clear();
    }

    public Set<String> getControlsStructuralChanges() {
        return controlsStructuralChanges != null ? controlsStructuralChanges : Collections.<String>emptySet();
    }

    public void addControlStructuralChange(String prefixedId) {
        if (this.controlsStructuralChanges == null)
            this.controlsStructuralChanges = new HashSet<String>();

        this.controlsStructuralChanges.add(prefixedId);
    }

    @Override
    public Scope innerScope() {
        // Do it here because at construction time, we don't yet have access to the static state!
        return staticState.topLevelPart().startScope();
    }

    public void afterInitialResponse() {

        getRequestStats().afterInitialResponse();

        this.uriResolver = null;        // URI resolver is of no use after initialization and it may keep dangerous references (PipelineContext)
        this.response = null;           // same as above
        this.initializing = false;

        // Do this before clearing the client state
        if (! getStaticState().isInlineResources())
            setInitialClientScript();

        clearClientState(); // client state can contain e.g. focus information, etc. set during initialization

        // Tell dependencies
        xpathDependencies.afterInitialResponse();
    }

    /**
     * Prepare the document for a sequence of external events.
     *
     * @param response          ExternalContext.Response for xf:submission[@replace = 'all'], or null
     */
    public void beforeExternalEvents(ExternalContext.Response response, boolean isAjaxRequest) {

        xpathDependencies.beforeUpdateResponse();
        this.response = response;

        if (isAjaxRequest) {
            processCompletedAsynchronousSubmissions(false, false);
            processDueDelayedEvents(false);
        } else {
            processDueDelayedEvents(true);
        }
    }

    /**
     * End a sequence of external events.
     *
     */
    public void afterExternalEvents(boolean isAjaxRequest) {

        if (isAjaxRequest) {
            processCompletedAsynchronousSubmissions(false, true);
            processDueDelayedEvents(false);
        }

        this.response = null;
    }

    /**
     * Called after sending a successful update response.
     */
    public void afterUpdateResponse() {

        getRequestStats().afterUpdateResponse();

        if (! getStaticState().isInlineResources())
            clearInitialClientScript();

        clearClientState();
        xformsControls.afterUpdateResponse();
        // Tell dependencies
        xpathDependencies.afterUpdateResponse();
    }

    public void rememberLastAjaxResponse(SAXStore response) {
        lastAjaxResponse = response;
    }

    public long getSequence() {
        return sequence;
    }

    /**
     * Return an OutputStream for xf:submission[@replace = 'all']. Used by submission.
     *
     * @return OutputStream
     */
    public ExternalContext.Response getResponse() {
        return response;
    }

    public AsynchronousSubmissionManager getAsynchronousSubmissionManager(boolean create) {
        if (asynchronousSubmissionManager == null && create)
            asynchronousSubmissionManager = new AsynchronousSubmissionManager(this);
        return asynchronousSubmissionManager;
    }

    private void processCompletedAsynchronousSubmissions(boolean skipDeferredEventHandling, boolean addPollEvent) {
        final AsynchronousSubmissionManager manager = getAsynchronousSubmissionManager(false);
        if (manager != null && manager.hasPendingAsynchronousSubmissions()) {
            if (!skipDeferredEventHandling)
                startOutermostActionHandler();
            manager.processCompletedAsynchronousSubmissions();
            if (!skipDeferredEventHandling)
                endOutermostActionHandler();

            // Remember to send a poll event if needed
            if (addPollEvent)
                manager.addClientDelayEventIfNeeded();
        }
    }

    private void createControlsAndModels() {
        addAllModels();
        xformsControls = new XFormsControls(this);
    }

    public void initializeNestedControls() {
        // Call-back from super class models initialization

        // This is important because if controls use binds, those must be up to date. In addition, MIP values will be up
        // to date. Finally, upon receiving xforms-ready after initialization, it is better if calculations and
        // validations are up to date.
        rebuildRecalculateRevalidateIfNeeded();

        // Initialize controls
        xformsControls.createControlTree(scala.Option.<scala.collection.immutable.Map<String, ControlState >>apply(null));
    }

    @Override
    public Seq<XFormsControl> getChildrenControls(XFormsControls controls) {
        return controls.getCurrentControlTree().children();
    }

    /**
     * Register that an upload has started.
     */
    public void startUpload(String uploadId) {
        if (pendingUploads == null)
            pendingUploads = new HashSet<String>();
        pendingUploads.add(uploadId);
    }

    /**
     * Register that an upload has ended.
     */
    public void endUpload(String uploadId) {
        // NOTE: Don't enforce existence of upload, as this is also called if upload control becomes non-relevant, and
        // also because asynchronously if the client notifies us to end an upload after a control has become non-relevant,
        // we don't want to fail.
        if (pendingUploads != null)
            pendingUploads.remove(uploadId);
    }

    public Set<String> getPendingUploads() {
        if (pendingUploads == null)
            return Collections.emptySet();
        else
            return pendingUploads;
    }

    /**
     * Return the number of pending uploads.
     */
    public int countPendingUploads() {
        return (pendingUploads == null) ? 0 : pendingUploads.size();
    }

    /**
     * Whether an upload is pending for the given upload control.
     */
    public boolean isUploadPendingFor(XFormsUploadControl uploadControl) {
        return (pendingUploads != null) && pendingUploads.contains(uploadControl.getUploadUniqueId());
    }
}
