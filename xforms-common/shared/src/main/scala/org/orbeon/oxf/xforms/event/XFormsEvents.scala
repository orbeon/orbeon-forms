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
package org.orbeon.oxf.xforms.event

import org.orbeon.oxf.util.LoggerFactory

// TODO: Rename to for example XFormsEventNames
object XFormsEvents {

  val LOGGING_CATEGORY = "event"
  val logger = LoggerFactory.createLogger(XFormsEvents.getClass)

  // Custom initialization events
  val XXFORMS_READY               = "xxforms-ready"

  // Other custom events
  val XXFORMS_STATE_RESTORED      = "xxforms-state-restored"
  val XXFORMS_SUBMIT              = "xxforms-submit"
  val XXFORMS_LOAD                = "xxforms-load"
  val XXFORMS_SETINDEX            = "xxforms-setindex"
  val XXFORMS_REPEAT_ACTIVATE     = "xxforms-repeat-activate"
  val XXFORMS_ACTION_ERROR        = "xxforms-action-error"
  val XXFORMS_REFRESH_DONE        = "xxforms-refresh-done"
  val XXFORMS_DIALOG_CLOSE        = "xxforms-dialog-close"
  val XXFORMS_DIALOG_OPEN         = "xxforms-dialog-open"
  val XXFORMS_INSTANCE_INVALIDATE = "xxforms-instance-invalidate"
  val XXFORMS_DND                 = "xxforms-dnd"
  val XXFORMS_VALID               = "xxforms-valid"
  val XXFORMS_INVALID             = "xxforms-invalid"
  val XXFORMS_VALUE_CHANGED       = "xxforms-value-changed"
  val XXFORMS_NODESET_CHANGED     = "xxforms-nodeset-changed"
  val XXFORMS_INDEX_CHANGED       = "xxforms-index-changed"
  val XXFORMS_ITERATION_MOVED     = "xxforms-iteration-moved"
  val XXFORMS_XPATH_ERROR         = "xxforms-xpath-error"
  val XXFORMS_BINDING_ERROR       = "xxforms-binding-error"
  val XXFORMS_BLUR                = "xxforms-blur"

  // Standard XForms events
  val XFORMS_MODEL_CONSTRUCT      = "xforms-model-construct"
  val XXFORMS_INSTANCES_READY     = "xxforms-instances-ready"
  val XFORMS_MODEL_CONSTRUCT_DONE = "xforms-model-construct-done"
  val XFORMS_READY                = "xforms-ready"
  val XFORMS_MODEL_DESTRUCT       = "xforms-model-destruct"
  val XFORMS_REBUILD              = "xforms-rebuild"
  val XFORMS_RECALCULATE          = "xforms-recalculate"
  val XFORMS_REVALIDATE           = "xforms-revalidate"
  val XFORMS_REFRESH              = "xforms-refresh"
  val XFORMS_RESET                = "xforms-reset"
  val XFORMS_SUBMIT               = "xforms-submit"
  val XFORMS_SUBMIT_SERIALIZE     = "xforms-submit-serialize"
  val XFORMS_SUBMIT_DONE          = "xforms-submit-done"
  val XFORMS_VALUE_CHANGED        = "xforms-value-changed"
  val XFORMS_VALID                = "xforms-valid"
  val XFORMS_INVALID              = "xforms-invalid"
  val XFORMS_REQUIRED             = "xforms-required"
  val XFORMS_OPTIONAL             = "xforms-optional"
  val XFORMS_READWRITE            = "xforms-readwrite"
  val XFORMS_READONLY             = "xforms-readonly"
  val XXFORMS_CONSTRAINTS_CHANGED = "xxforms-constraints-changed"
  val XXFORMS_INITIALLY_DISABLED  = "xxforms-initially-disabled"
  val XXFORMS_VISIBLE             = "xxforms-visible"
  val XXFORMS_HIDDEN              = "xxforms-hidden"
  val XXFORMS_VISITED             = "xxforms-visited"
  val XXFORMS_UNVISITED           = "xxforms-unvisited"
  val XFORMS_ENABLED              = "xforms-enabled"
  val XFORMS_DISABLED             = "xforms-disabled"
  val XFORMS_DESELECT             = "xforms-deselect"
  val XFORMS_SELECT               = "xforms-select"
  val XFORMS_INSERT               = "xforms-insert"
  val XFORMS_DELETE               = "xforms-delete"
  val XXFORMS_REPLACE             = "xxforms-replace"
  val XFORMS_FOCUS                = "xforms-focus"
  val XFORMS_SCROLL_FIRST         = "xforms-scroll-first"
  val XFORMS_SCROLL_LAST          = "xforms-scroll-last"
  val XFORMS_HELP                 = "xforms-help"
  val XFORMS_HINT                 = "xforms-hint"

  // DOM events
  val DOM_ACTIVATE                = "DOMActivate"
  val DOM_FOCUS_OUT               = "DOMFocusOut"
  val DOM_FOCUS_IN                = "DOMFocusIn"

  // Exceptions and errors
  val XFORMS_LINK_EXCEPTION       = "xforms-link-exception"
  val XFORMS_LINK_ERROR           = "xforms-link-error"
  val XFORMS_SUBMIT_ERROR         = "xforms-submit-error"
}
