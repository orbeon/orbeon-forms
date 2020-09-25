package org.orbeon.oxf.fr.workflow.definitions20201


// Represent the workflow configuration as a hierarchy of case classes.
// Note that uPickle or Circe, which we use for serialization, do not support shared objects.
// Also note that prickle supports that but has a more complex JSON serialization:
// https://github.com/benhutchison/prickle#support-for-shared-objects

// NOTE: You can't use for example a `case class Lang(name: String)` type for the `Map` key without a
// custom encoder/decoder. So We use `String for now.
case class LocalizedString(values: Map[String, String])

case class Stage(
  name          : String,
  documentation : String
)

// Buttons
case class   ButtonName (name: String)

sealed trait ButtonLabel
case object  PredefinedButtonLabel                        extends ButtonLabel
case class   CustomButtonLabel    (name: LocalizedString) extends ButtonLabel

sealed trait WorkflowAction
case object  NopWorkflowAction                            extends WorkflowAction
case class   ChangeStageWorkflowAction(stageName: String) extends WorkflowAction
case class   RunProcessWorkflowAction (process: String)   extends WorkflowAction

case class Button(
  name          : ButtonName,
  documentation : String,
  label         : ButtonLabel,
  actions       : Vector[WorkflowAction]
)

// Later we might have custom `WorkflowRole`s
sealed trait WorkflowRole
case object  OwnerWorkflowRole       extends WorkflowRole
case object  GroupMemberWorkflowRole extends WorkflowRole

sealed trait Comparison
case object  IsComparison    extends Comparison
case object  IsNotComparison extends Comparison

sealed trait Combinator
case object  AllCombinator   extends Combinator
case object  AnyCombinator   extends Combinator

sealed trait AvailabilityRule
case class   GroupingAvailabilityRule          (combinator: Combinator, rules: Vector[AvailabilityRule]) extends AvailabilityRule
case class   WorkflowStageAvailabilityRule     (combinator: Comparison, stageName: String)               extends AvailabilityRule
case class   WorkflowRoleAvailabilityRule      (combinator: Comparison, role: WorkflowRole)              extends AvailabilityRule
case class   AuthenticationRoleAvailabilityRule(combinator: Comparison, roleName: String)                extends AvailabilityRule

sealed trait Availability
case object  ToAnyoneAvailability                        extends Availability
case class   ToUsersAvailability(rule: AvailabilityRule) extends Availability

sealed trait Page
case object  SummaryPage                  extends Page
case object  EditPage                     extends Page
case object  ViewPage                     extends Page
case object  RenderedPage                 extends Page
case class   NewPage(initialStage: Stage) extends Page

case class Perspective(
  name           : String,
  documentation  : String,
  accessTo       : Set[Page],
  availability   : Availability,
  summaryButtons : Vector[ButtonName],
  editButtons    : Vector[ButtonName],
  viewButtons    : Vector[ButtonName]
)

case class WorkflowConfig(
  stages       : Vector[Stage],
  buttons      : Vector[Button], // order doesn't matter; `Set`?
  perspectives : Vector[Perspective]
  //roles: Vector[Nothing]
)