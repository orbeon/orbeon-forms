package org.orbeon.oxf.fr.workflow.definitions20191


// Represent the workflow configuration as a hierarchy of case classes.
// Note that uPickle or Circe, which we use for serialization, do not support shared objects.
// Also note that prickle supports that but has a more complex JSON serialization:
// https://github.com/benhutchison/prickle#support-for-shared-objects

// NOTE: You can't use for example a `case class Lang(name: String)` type for the `Map` key without a
// custom encoder/decoder. So We use `String for now.
case class LocalizedString(values: Map[String, String])

case class Stage (name: String)
case class Stages(stages: Vector[Stage], defaultStageName: String)

// Buttons
sealed trait ButtonName
case class   CustomButtonName    (name: LocalizedString) extends ButtonName
case class   PredefinedButtonName(name: String)          extends ButtonName

sealed trait WorkflowAction
case object  NopWorkflowAction                            extends WorkflowAction
case class   ChangeStageWorkflowAction(stageName: String) extends WorkflowAction
case class   RunProcessWorkflowAction (process: String)   extends WorkflowAction

case class Button(
  name    : ButtonName,
  actions : Vector[WorkflowAction]
)

// Later we might have custom `WorkflowRole`s
sealed trait WorkflowRole
case object  OwnerWorkflowRole       extends WorkflowRole
case object  GroupMemberWorkflowRole extends WorkflowRole

sealed trait Combinator
case object  IsCombinator    extends Combinator
case object  IsNotCombinator extends Combinator

sealed trait GroupingCombinator
case object  AllGroupingCombinator extends GroupingCombinator
case object  AnyGroupingCombinator extends GroupingCombinator

sealed trait AvailabilityRule
case class   GroupingAvailabilityRule          (combinator: GroupingCombinator, rules: Vector[AvailabilityRule]) extends AvailabilityRule
case class   WorkflowStageAvailabilityRule     (combinator: Combinator,         stageName: String)               extends AvailabilityRule
case class   WorkflowRoleAvailabilityRule      (combinator: Combinator,         role: WorkflowRole)              extends AvailabilityRule
case class   AuthenticationRoleAvailabilityRule(combinator: Combinator,         roleName: String)                extends AvailabilityRule

sealed trait Availability
case object  ToAnyoneAvailability                                        extends Availability
case class   ToUsersAvailability(groupingRule: GroupingAvailabilityRule) extends Availability

sealed trait Operation
case class   CreateOperation(initialStageName: String) extends Operation // or initial name could be stored somewhere else
case object  ListSearchOperation                       extends Operation
case object  EditOperation                             extends Operation
case object  ViewOperation                             extends Operation
case object  PdfOperation                              extends Operation

case class Perspective(
  name           : String,
  availability   : Availability, // restricted to `ToAnyone` or nested `AuthenticationRoleRule` for `creating`
  operations     : Set[Operation],
  summaryButtons : Vector[Button],
  editButtons    : Vector[Button],
  viewButtons    : Vector[Button]
)

case class State(
  stages       : Stages,
  buttons      : Vector[Button], // order doesn't matter
  perspectives : Vector[Perspective]
  //roles: Vector[Nothing]
)