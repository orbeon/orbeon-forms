package org.orbeon.oxf.fr

import org.orbeon.oxf.properties.PropertySet

import java.time.ZoneId
import java.time.temporal.TemporalAccessor


object FormRunnerDateTimeSupport {

  // Get a timezone formatter based on configured properties
  def timezoneFormatter(zoneIdStringOpt: Option[String], propertyProfileOpt: Option[String])(implicit properties: PropertySet): (ZoneId, TemporalAccessor => String) =
    zoneIdStringOpt.map(timezoneFormatterForZoneId)
      .orElse(timezoneFormatterForProperty("user.timezone", propertyProfileOpt))
      .orElse(timezoneFormatterForProperty("oxf.fr.default-timezone", propertyProfileOpt))
      .getOrElse(UtcTimezoneFormatter)

  // To avoid creating a formatter every time, we cache it against the property
  private def timezoneFormatterForProperty(name: String, propertyProfileOpt: Option[String])(implicit properties: PropertySet): Option[(ZoneId, TemporalAccessor => String)] =
    for {
      property    <- properties.getPropertyOpt(name, propertyProfileOpt)
      stringValue <- property.nonBlankStringValue
    } yield
      property.associatedValue(_ => timezoneFormatterForZoneId(stringValue))

  // Q: Unclear if there is a better way than using a formatter. We should be able to just be able to query the
  // right name from the timezone.
  private def timezoneFormatterForZoneId(zoneIdString: String): (ZoneId, TemporalAccessor => String) = {
    val zoneId = ZoneId.of(zoneIdString)
    (zoneId, java.time.format.DateTimeFormatter.ofPattern("z").withZone(zoneId).format)
  }

  private val UtcTimezoneFormatter: (ZoneId, TemporalAccessor => String) = timezoneFormatterForZoneId("UTC")
}
