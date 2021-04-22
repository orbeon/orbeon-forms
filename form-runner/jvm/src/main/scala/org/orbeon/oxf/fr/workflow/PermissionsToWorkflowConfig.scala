package org.orbeon.oxf.fr.workflow

import org.orbeon.oxf.fr.permission.{AnyOperation, DefinedPermissions, Group, Owner, Permissions, RolesAnyOf, SpecificOperations, UndefinedPermissions, Operation => PermissionOperation, Operations => PermissionOperations}
import org.orbeon.oxf.util.CoreUtils._

object PermissionsToWorkflowConfig {

  import Private._
  import org.orbeon.oxf.fr.workflow.definitions20201._

  def convert(permissions: Permissions): WorkflowConfig = {

    val perspectives =
      permissions match {
        case UndefinedPermissions =>
          throw new UnsupportedOperationException("We only expect `DefinedPermissions` to be converted to a `WorkflowConfig`")
        case DefinedPermissions(permissionsList) =>

          // Determine the possible "actors" (owner, group member, specific authentication role) with the operations they can perform
          val actorsOperations: List[(Availability, List[PermissionOperation])] =
            permissionsList.flatMap { permission =>
              val actors =
                if (permission.conditions.isEmpty)
                  List(ToAnyoneAvailability)
                else
                  permission.conditions
                    .flatMap {
                      case Owner => List(WorkflowRoleAvailabilityRule(IsComparison, OwnerWorkflowRole))
                      case Group => List(WorkflowRoleAvailabilityRule(IsComparison, GroupMemberWorkflowRole))
                      case RolesAnyOf(roles) => roles.map(AuthenticationRoleAvailabilityRule(IsComparison, _))
                    }.map(ToUsersAvailability(_))
              val operations = permission.operations match {
                case AnyOperation => PermissionOperations.All
                case SpecificOperations(operations) => operations
              }
              actors.map(_ -> operations)
            }

          // Group by actor
          val actorToOperations: Map[Availability, List[PermissionOperation]] =
            actorsOperations
              .groupBy(_._1)
              .map { case (actor, operations) => actor -> operations.flatMap(_._2) }

          // For each actor, find the perspective that applies
          val perspectivesActors: List[(PermissionPerspective, Availability)] =
            actorToOperations.map { case (actor, operations) =>
              val perspectives = perspectivesFromOperations(operations)
              perspectives.map(perspective => perspective -> actor)
            }.flatten.to[List]

          // Group by perspective
          val perspectiveToActors: Map[PermissionPerspective, List[Availability]] =
            perspectivesActors
              .groupBy(_._1)
              .map { case (perspective, actors) => perspective -> actors.map(_._2)}

          // Build actual perspectives
          perspectiveToActors.map { case (perspective, actors) =>
            val availability =
              if (actors.size == 1)
                actors.head
              else if (actors.contains(ToAnyoneAvailability))
                ToAnyoneAvailability
              else {
                val availabilityRules = actors.collect { case ToUsersAvailability(availabilityRule) => availabilityRule }
                ToUsersAvailability(GroupingAvailabilityRule(AnyCombinator, availabilityRules.to[Vector]))
              }
            perspective.builder(availability)
          }
      }

    val buttons = perspectives.flatMap { perspective =>
      val perspectiveButtons =
        perspective.viewButtons      ++
          perspective.editButtons    ++
          perspective.summaryButtons
      perspectiveButtons.map(predefinedButton)
    }

    val stages = Vector(
      Stage("started", ""),
      Stage("saved", "")
    )

    WorkflowConfig(stages, buttons.to[Vector], perspectives.to[Vector])
  }

  def predefinedButton(name: ButtonName): Button =
    Button(
      name          = name,
      documentation = "",
      label         = PredefinedButtonLabel,
      actions       = Vector(RunProcessWorkflowAction(name.name))
    )

  def customButton(name: ButtonName, label: String): Button =
    Button(
      name          = name,
      documentation = "",
      label         = CustomButtonLabel(LocalizedString(Map("en" -> label))),
      actions       = Vector(RunProcessWorkflowAction(name.name))
    )

  case class PermissionPerspective(builder: Availability => Perspective)

  val CreatorPermissionPerspective = PermissionPerspective(
    builder = availability =>
      Perspective(
        name           = "creator",
        documentation  = "",
        accessTo       = Set(NewPage(StartedStage)),
        availability   = availability,
        summaryButtons = Vector.empty,
        editButtons    = editButtons.map(ButtonName),
        viewButtons    = Vector.empty
      )
  )

  val ReaderPermissionPerspective = PermissionPerspective(
    builder = availability =>
      Perspective(
        name           = "reader",
        documentation  = "",
        accessTo       = Set(SummaryPage, ViewPage),
        availability   = availability,
        summaryButtons = summaryViewButtons.map(ButtonName),
        editButtons    = Vector.empty,
        viewButtons    = viewButtons.map(ButtonName)
      )
  )

  val EditorPermissionPerspective = PermissionPerspective(
    builder = availability =>
      Perspective(
        name           = "editor",
        documentation  = "",
        accessTo       = Set(SummaryPage, EditPage, ViewPage),
        availability   = availability,
        summaryButtons = summaryViewButtons.map(ButtonName),
        editButtons    = editButtons.map(ButtonName),
        viewButtons    = viewButtons.map(ButtonName)
      )
  )

  val ReaderCleanerPermissionPerspective = PermissionPerspective(
    builder = availability =>
      Perspective(
        name           = "reader-cleaner",
        documentation  = "",
        accessTo       = Set(SummaryPage, EditPage, ViewPage),
        availability   = availability,
        summaryButtons = summaryViewButtons.map(ButtonName),
        editButtons    = editButtons.map(ButtonName),
        viewButtons    = viewButtons.map(ButtonName)
      )
  )

  val EditorCleanerPermissionPerspective = PermissionPerspective(
    builder = availability =>
      Perspective(
        name           = "editor-cleaner",
        documentation  = "",
        accessTo       = Set(SummaryPage, EditPage, ViewPage),
        availability   = availability,
        summaryButtons = summaryViewButtons.map(ButtonName),
        editButtons    = editButtons.map(ButtonName),
        viewButtons    = viewButtons.map(ButtonName)
      )
  )

  val CleanerPermissionPerspective = PermissionPerspective(
    builder = availability =>
      Perspective(
        name           = "cleaner",
        documentation  = "",
        accessTo       = Set(SummaryPage),
        availability   = availability,
        summaryButtons = summaryViewButtons.map(ButtonName),
        editButtons    = editButtons.map(ButtonName),
        viewButtons    = viewButtons.map(ButtonName)
      )
  )

  private object Private {

    def perspectivesFromOperations(
      operations: List[PermissionOperation]
    ): List[PermissionPerspective] = {
      val creatorOpt = operations.contains(PermissionOperation.Create).option(CreatorPermissionPerspective)
      val otherPerspectiveOpt =
        if (operations.contains(PermissionOperation.Update)) {
          if (operations.contains(PermissionOperation.Delete))
            Some(EditorCleanerPermissionPerspective)
          else
            Some(EditorPermissionPerspective)
        } else if (operations.contains(PermissionOperation.Read)) {
          if (operations.contains(PermissionOperation.Delete))
            Some(ReaderCleanerPermissionPerspective)
          else
            Some(ReaderPermissionPerspective)
        } else if (operations.contains(PermissionOperation.Delete)) {
          Some(CleanerPermissionPerspective)
        } else {
          None
        }
      (creatorOpt ++ otherPerspectiveOpt).to[List]
    }

    val summaryViewButtons       = Vector("home"   , "review", "pdf",                            "new")
    val summaryEditButtons       = Vector("home"   , "review", "pdf",               "duplicate", "new")
    val summaryEditDeleteButtons = Vector("home"   , "review", "pdf", "delete",     "duplicate", "new")
    val editButtons              = Vector("summary", "clear",  "pdf", "save-final", "review")
    val viewButtons              = Vector("edit",              "pdf")

    val StartedStage = Stage("started", "")
    val SavedStage   = Stage("saved", "")
  }

}
