package org.orbeon.oxf.xforms.state

import org.orbeon.oxf.xforms.model.{InstanceCaching, XFormsInstance}
import org.orbeon.xforms.XFormsId


// Minimal immutable representation of a serialized control
case class ControlState(
  effectiveId : String,
  visited     : Boolean,
  keyValues   : Map[String, String]
)

// Minimal immutable representation of a serialized instance
// If there is caching information, don't include the actual content
case class InstanceState(
  effectiveId      : String,
  modelEffectiveId : String,
  cachingOrContent : InstanceCaching Either String,
  readonly         : Boolean,
  modified         : Boolean,
  valid            : Boolean
) {

  def this(instance: XFormsInstance) =
    this(
      instance.getEffectiveId,
      instance.parent.getEffectiveId,
      instance.instanceCaching.toLeft(instance.contentAsString),
      instance.readonly,
      instance.modified,
      instance.valid)
}

case class InstancesControls(instances: List[InstanceState], controls: Map[String, ControlState])
