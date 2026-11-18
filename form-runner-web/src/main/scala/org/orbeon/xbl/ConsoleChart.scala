/**
 * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.xbl

import io.circe.generic.auto.*
import io.circe.parser.decode
import org.orbeon.facades.ChartJs
import org.orbeon.oxf.fr.{ConsoleChartSeries, ConsoleChartSpec}
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.XFormsXbl
import org.orbeon.xforms.facade.XBLCompanion
import org.scalajs.dom
import org.scalajs.dom.{MutationObserver, MutationObserverInit, ResizeObserver, html}

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*


object ConsoleChart {

  XFormsXbl.declareCompanion("fr|console-chart", js.constructorOf[ConsoleChartCompanion])

  private val PointStyles: Vector[String] = Vector("circle", "rectRot", "triangle", "rect", "star", "crossRot", "circle")

  private final case class ThemeColors(
    isDark        : Boolean,
    textColor     : String,
    mutedColor    : String,
    gridColor     : String,
    surfaceColor  : String,
    borderColor   : String,
    strokeColors  : Vector[String],
    fillColors    : Vector[String]
  )

  private class ConsoleChartCompanion(containerElem: html.Element) extends XBLCompanion {

    private var wrapElemOpt       : Option[html.Element]            = None
    private var canvasElemOpt     : Option[html.Canvas]             = None
    private var emptyElemOpt      : Option[html.Element]            = None
    private var chartOpt          : Option[ChartJs]                 = None
    private var currentRawJson    : String                          = ""
    private var currentSpecOpt    : Option[ConsoleChartSpec]        = None
    private var renderedShapeKey  : String                          = ""
    private var resizeObserverOpt : Option[ResizeObserver]          = None
    private var themeObserverOpt  : Option[MutationObserver]        = None
    private var i18nObserverOpt   : Option[MutationObserver]        = None
    private var mediaQueryOpt     : Option[dom.MediaQueryList]      = None
    private var mediaListenerOpt  : Option[js.Function1[dom.Event, Unit]] = None

    override def init(): Unit = {
      wrapElemOpt   = containerElem.querySelectorOpt(".fr-console-chart-canvas-wrap")
      canvasElemOpt = containerElem.queryNestedElems[html.Canvas]("canvas.fr-console-chart-canvas").headOption
      emptyElemOpt  = containerElem.querySelectorOpt(".fr-console-chart-empty")

      // Observe container width so charts inside initially hidden `<fr:tab>` panes render crisply as soon as selected
      wrapElemOpt.foreach { wrapElem =>
        val ro = new ResizeObserver((_, _) => {
          if (wrapElem.clientWidth > 0) {
            if (chartOpt.isEmpty && currentSpecOpt.isDefined)
              syncChart(forceRebuild = true)
            else
              chartOpt.foreach(_.resize())
          }
        })
        ro.observe(wrapElem)
        resizeObserverOpt = Some(ro)
      }

      // Observe light/dark/system theme changes on `#fr-view`, `document.body`, and OS `prefers-color-scheme`
      val themeObs = new MutationObserver((_, _) => {
        if (chartOpt.isDefined)
          syncChart(forceRebuild = true)
      })
      val classFilter = new MutationObserverInit {
        attributes      = true
        attributeFilter = js.Array("class")
      }
      dom.document.getElementByIdOpt("fr-view").foreach(themeObs.observe(_, classFilter))
      Option(dom.document.body).foreach(themeObs.observe(_, classFilter))
      themeObserverOpt = Some(themeObs)

      val mql = dom.window.matchMedia("(prefers-color-scheme: dark)")
      val mqlFn: js.Function1[dom.Event, Unit] = (_: dom.Event) => {
        if (chartOpt.isDefined)
          syncChart(forceRebuild = true)
      }
      mql.addEventListener("change", mqlFn)
      mediaQueryOpt    = Some(mql)
      mediaListenerOpt = Some(mqlFn)

      // Observe localized series label updates (e.g. EN <-> FR switch in navbar)
      containerElem.querySelectorOpt(".fr-console-chart-series-i18n").foreach { i18nElem =>
        val i18nObs = new MutationObserver((_, _) => {
          if (chartOpt.isDefined)
            syncChart(forceRebuild = false)
        })
        i18nObs.observe(
          i18nElem,
          new MutationObserverInit {
            childList       = true
            subtree         = true
            attributes      = true
            characterData   = true
          }
        )
        i18nObserverOpt = Some(i18nObs)
      }

      if (currentRawJson.nonAllBlank)
        syncChart(forceRebuild = true)
    }

    override def destroy(): Unit = {
      destroyChartOnly()
      resizeObserverOpt.foreach(_.disconnect())
      resizeObserverOpt = None
      themeObserverOpt.foreach(_.disconnect())
      themeObserverOpt = None
      i18nObserverOpt.foreach(_.disconnect())
      i18nObserverOpt = None
      for {
        mql <- mediaQueryOpt
        fn  <- mediaListenerOpt
      } mql.removeEventListener("change", fn)
      mediaQueryOpt    = None
      mediaListenerOpt = None
      wrapElemOpt      = None
      canvasElemOpt    = None
      emptyElemOpt     = None
    }

    override def xformsGetValue(): String =
      currentRawJson

    override def xformsUpdateValue(newValue: String): js.UndefOr[Nothing] = {
      val normalized = Option(newValue).flatMap(_.trimAllToOpt).getOrElse("")
      if (normalized != currentRawJson) {
        currentRawJson = normalized
        currentSpecOpt =
          if (normalized.isEmpty) None
          else decode[ConsoleChartSpec](normalized).toOption
        syncChart(forceRebuild = false)
      }
      js.undefined
    }

    private def destroyChartOnly(): Unit = {
      chartOpt.foreach(_.destroy())
      chartOpt         = None
      renderedShapeKey = ""
    }

    private def readSeriesLabels: Map[String, String] =
      containerElem
        .querySelectorAllT(".fr-console-chart-series-i18n > span[data-key]")
        .flatMap { span =>
          for {
            k <- span.attValueOpt("data-key").flatMap(_.trimAllToOpt)
            v <- span.attValueOpt("data-label").flatMap(_.trimAllToOpt)
          } yield k -> v
        }
        .toMap

    private def isEffectiveDarkMode: Boolean = {
      val frViewClasses = dom.document.getElementByIdOpt("fr-view").map(_.classList)
      val bodyClasses   = Option(dom.document.body).map(_.classList)
      def hasClass(cls: String): Boolean =
        frViewClasses.exists(_.contains(cls)) || bodyClasses.exists(_.contains(cls))

      if (hasClass("fr-color-scheme-dark")) true
      else if (hasClass("fr-color-scheme-light")) false
      else dom.window.matchMedia("(prefers-color-scheme: dark)").matches
    }

    private def resolveThemeColors(): ThemeColors = {
      val isDark = isEffectiveDarkMode
      val cs     = dom.window.getComputedStyle(containerElem)
      def cssVar(name: String, fallback: String): String =
        Option(cs.getPropertyValue(name)).flatMap(_.trimAllToOpt).getOrElse(fallback)

      val textColor    = cssVar("--orbeon-base-color",            if (isDark) "#e5e7eb" else "#1f2937")
      val mutedColor   = cssVar("--orbeon-hint-color",            if (isDark) "#9ca3af" else "#6b7280")
      val borderColor  = cssVar("--orbeon-table-border-color",    if (isDark) "#374151" else "#e5e7eb")
      val surfaceColor = cssVar("--orbeon-form-background-color", if (isDark) "#1f2937" else "#ffffff")
      val accentColor  = cssVar("--orbeon-accent-color",          if (isDark) "#60a5fa" else "#2563eb")
      val gridColor    = if (isDark) "rgba(156, 163, 175, 0.16)" else "rgba(107, 114, 128, 0.16)"

      val strokes =
        if (isDark)
          Vector(
            accentColor,
            "#34d399", // emerald
            "#fbbf24", // amber
            "#94a3b8", // slate
            "#fb7185", // coral/rose
            "#a78bfa", // violet
            "rgba(148, 163, 184, 0.35)" // muted headroom track
          )
        else
          Vector(
            accentColor,
            "#059669", // emerald
            "#d97706", // amber
            "#64748b", // slate
            "#e11d48", // coral/rose
            "#7c3aed", // violet
            "rgba(100, 116, 139, 0.24)" // muted headroom track
          )

      val fills =
        if (isDark)
          Vector(
            "rgba(96, 165, 250, 0.22)",
            "rgba(52, 211, 153, 0.72)",
            "rgba(251, 191, 36, 0.72)",
            "rgba(148, 163, 184, 0.22)",
            "rgba(251, 113, 133, 0.72)",
            "rgba(167, 139, 250, 0.72)",
            "rgba(148, 163, 184, 0.20)"
          )
        else
          Vector(
            "rgba(37, 99, 235, 0.16)",
            "rgba(5, 150, 105, 0.76)",
            "rgba(217, 119, 6, 0.76)",
            "rgba(100, 116, 139, 0.18)",
            "rgba(225, 29, 72, 0.76)",
            "rgba(124, 58, 237, 0.76)",
            "rgba(148, 163, 184, 0.22)"
          )

      ThemeColors(
        isDark       = isDark,
        textColor    = textColor,
        mutedColor   = mutedColor,
        gridColor    = gridColor,
        surfaceColor = surfaceColor,
        borderColor  = borderColor,
        strokeColors = strokes,
        fillColors   = fills
      )
    }

    private def syncChart(forceRebuild: Boolean): Unit = {
      val chartJsAvailable = ! js.isUndefined(js.Dynamic.global.Chart)
      if (! chartJsAvailable)
        return

      (wrapElemOpt, canvasElemOpt, currentSpecOpt) match {
        case (Some(wrapElem), Some(canvasElem), Some(spec)) =>
          val hasData =
            if (spec.kind == "timeseries") spec.timestampsMs.nonEmpty && spec.series.nonEmpty
            else spec.categories.nonEmpty && spec.series.nonEmpty

          emptyElemOpt.foreach(_.toggleClass("xforms-hidden", hasData))
          wrapElem.toggleClass("xforms-hidden", ! hasData)

          if (! hasData) {
            destroyChartOnly()
            return
          }

          // Size container height cleanly for horizontal bar count vs time-series
          val targetHeightPx =
            if (spec.kind == "hbar") math.max(175, math.min(310, 48 + spec.categories.size * 26))
            else 215
          wrapElem.style.height = s"${targetHeightPx}px"

          // Defer Chart.js instantiation while inside a `display: none` inactive tab
          if (wrapElem.clientWidth <= 0)
            return

          val i18nLabels = readSeriesLabels
          val shapeKey   = s"${spec.kind}:${spec.stacked}:${spec.valueUnit}:${spec.secondaryUnitOpt.getOrElse("")}:${spec.series.map(s => s"${s.key}:${s.chartType}:${s.yAxisId}").mkString(",")}"

          if (! forceRebuild && chartOpt.isDefined && renderedShapeKey == shapeKey) {
            val chart  = chartOpt.get
            val labels = buildLabelsJs(spec)
            chart.data.labels         = labels
            chart.data._fullLabels    = spec.fullCategories.toJSArray
            chart.data._categoryMeta  = spec.categoryMeta.toJSArray
            val pointCount = if (spec.kind == "timeseries") spec.timestampsMs.size else spec.categories.size
            spec.series.zipWithIndex.foreach { case (s, idx) =>
              val ds       = chart.data.datasets.asInstanceOf[js.Array[js.Dynamic]](idx)
              val lbl: String = i18nLabels.getOrElse(s.key, s.key)
              ds.label = lbl
              ds.data  = s.values.toJSArray
              if (s.chartType != "bar")
                ds.pointRadius = if (pointCount <= 3) 3.0 else 0.0
            }
            chart.update("none")
          } else {
            destroyChartOnly()
            val config = buildChartConfig(spec, i18nLabels, resolveThemeColors())
            chartOpt         = Some(new ChartJs(canvasElem, config))
            renderedShapeKey = shapeKey
          }

        case _ =>
          destroyChartOnly()
      }
    }

    private def buildLabelsJs(spec: ConsoleChartSpec): js.Array[String] =
      if (spec.kind == "timeseries")
        spec.timestampsMs.map(ts => formatTimestamp(ts, includeSeconds = spec.window != "24h")).toJSArray
      else
        spec.categories.toJSArray

    private def buildChartConfig(
      spec      : ConsoleChartSpec,
      i18n      : Map[String, String],
      theme     : ThemeColors
    ): js.Dynamic = {
      val isHBar        = spec.kind == "hbar"
      val baseChartType = if (isHBar || spec.series.forall(_.chartType == "bar")) "bar" else "line"
      val pointCount    = if (spec.kind == "timeseries") spec.timestampsMs.size else spec.categories.size
      val reducedMotion = dom.window.matchMedia("(prefers-reduced-motion: reduce)").matches

      val datasets =
        spec.series.zipWithIndex.map { case (s, idx) =>
          val slot   = math.max(0, math.min(theme.strokeColors.size - 1, s.colorSlot))
          val stroke = theme.strokeColors(slot)
          val fill   =
            if (isHBar && s.chartType == "bar" && slot == 0)
              if (theme.isDark) "rgba(96, 165, 250, 0.78)" else "rgba(37, 99, 235, 0.80)"
            else
              theme.fillColors(slot)

          val dsType      = if (s.chartType == "bar") "bar" else "line"
          val dsLbl: String = i18n.getOrElse(s.key, s.key)
          val ds = js.Dynamic.literal(
            `type`          = dsType,
            label           = dsLbl,
            data            = s.values.toJSArray,
            borderColor     = stroke,
            backgroundColor = if (s.chartType == "line") stroke else fill,
            yAxisID         = if (isHBar) "y" else s.yAxisId
          )

          if (dsType == "bar") {
            ds.borderWidth   = if (slot == 6) 0 else 1
            ds.borderRadius  = 3
            ds.maxBarThickness = if (isHBar) 18 else 14
            if (spec.stacked && ! isHBar)
              ds.stack = s"stack-${s.yAxisId}"
          } else {
            ds.fill            = s.chartType == "area"
            ds.borderWidth     = 2
            ds.tension         = 0.25
            ds.pointStyle      = PointStyles(idx % PointStyles.size)
            ds.pointRadius     = if (pointCount <= 3) 3.0 else 0.0
            ds.pointHoverRadius = 4.5
            if (s.dashed)
              ds.borderDash = js.Array(5, 4)
          }
          ds
        }.toJSArray

      val dataObj = js.Dynamic.literal(
        labels        = buildLabelsJs(spec),
        datasets      = datasets,
        _fullLabels   = spec.fullCategories.toJSArray,
        _categoryMeta = spec.categoryMeta.toJSArray
      )

      val showLegend = spec.series.size > 1

      val scalesObj =
        if (isHBar) {
          js.Dynamic.literal(
            x = js.Dynamic.literal(
              stacked     = spec.stacked,
              beginAtZero = true,
              grid        = js.Dynamic.literal(color = theme.gridColor),
              border      = js.Dynamic.literal(color = theme.borderColor),
              ticks       = js.Dynamic.literal(
                color         = theme.mutedColor,
                maxTicksLimit = 5,
                callback      = ((v: js.Any) => formatUnitCompact(asDouble(v), spec.valueUnit)): js.Function1[js.Any, String]
              )
            ),
            y = js.Dynamic.literal(
              stacked = spec.stacked,
              grid    = js.Dynamic.literal(display = false),
              border  = js.Dynamic.literal(color = theme.borderColor),
              ticks   = js.Dynamic.literal(
                color    = theme.textColor,
                autoSkip = false,
                font     = js.Dynamic.literal(size = 11)
              )
            )
          )
        } else {
          val sc = js.Dynamic.literal(
            x = js.Dynamic.literal(
              stacked = spec.stacked,
              grid    = js.Dynamic.literal(display = false),
              border  = js.Dynamic.literal(color = theme.borderColor),
              ticks   = js.Dynamic.literal(
                color         = theme.mutedColor,
                maxRotation   = 0,
                autoSkip      = true,
                maxTicksLimit = 6,
                font          = js.Dynamic.literal(size = 11)
              )
            ),
            y = js.Dynamic.literal(
              stacked     = spec.stacked,
              beginAtZero = true,
              position    = "left",
              grid        = js.Dynamic.literal(color = theme.gridColor),
              border      = js.Dynamic.literal(color = theme.borderColor),
              ticks       = js.Dynamic.literal(
                color         = theme.mutedColor,
                maxTicksLimit = 5,
                precision     = if (spec.valueUnit == "count") 0 else js.undefined,
                callback      = ((v: js.Any) => formatUnitCompact(asDouble(v), spec.valueUnit)): js.Function1[js.Any, String]
              )
            )
          )
          spec.secondaryUnitOpt.foreach { secUnit =>
            sc.y1 = js.Dynamic.literal(
              beginAtZero = true,
              position    = "right",
              grid        = js.Dynamic.literal(drawOnChartArea = false),
              border      = js.Dynamic.literal(color = theme.borderColor),
              ticks       = js.Dynamic.literal(
                color         = theme.mutedColor,
                maxTicksLimit = 5,
                precision     = if (secUnit == "count") 0 else js.undefined,
                callback      = ((v: js.Any) => formatUnitCompact(asDouble(v), secUnit)): js.Function1[js.Any, String]
              )
            )
          }
          sc
        }

      val seriesUnitsByIdx: Vector[String] =
        spec.series.map { s =>
          if (s.yAxisId == "y1") spec.secondaryUnitOpt.getOrElse(spec.valueUnit)
          else spec.valueUnit
        }.toVector

      val tooltipCallbacks = js.Dynamic.literal(
        title = ((items: js.Array[js.Dynamic]) => {
          if (items.isEmpty) ""
          else {
            val item     = items(0)
            val idx      = item.dataIndex.asInstanceOf[Int]
            val fullArr  = item.chart.data._fullLabels.asInstanceOf[js.UndefOr[js.Array[String]]]
            fullArr.toOption.flatMap(_.lift(idx)).filter(_.nonEmpty).getOrElse(String.valueOf(item.label))
          }
        }): js.Function1[js.Array[js.Dynamic], String],
        label = ((ctx: js.Dynamic) => {
          val dsIdx = ctx.datasetIndex.asInstanceOf[Int]
          val unit  = seriesUnitsByIdx.lift(dsIdx).getOrElse(spec.valueUnit)
          val rawV  = if (isHBar) asDouble(ctx.parsed.x) else asDouble(ctx.parsed.y)
          val lbl   = String.valueOf(ctx.dataset.label)
          s" $lbl: ${formatUnitDetail(rawV, unit)}"
        }): js.Function1[js.Dynamic, String],
        afterBody = ((items: js.Array[js.Dynamic]) => {
          if (items.isEmpty) js.Array[String]()
          else {
            val item    = items(0)
            val idx     = item.dataIndex.asInstanceOf[Int]
            val metaArr = item.chart.data._categoryMeta.asInstanceOf[js.UndefOr[js.Array[String]]]
            metaArr.toOption.flatMap(_.lift(idx)).filter(_.nonEmpty) match {
              case Some(meta) => js.Array(meta)
              case None       => js.Array[String]()
            }
          }
        }): js.Function1[js.Array[js.Dynamic], js.Array[String]]
      )

      js.Dynamic.literal(
        `type`  = baseChartType,
        data    = dataObj,
        options = js.Dynamic.literal(
          indexAxis           = if (isHBar) "y" else "x",
          responsive          = true,
          maintainAspectRatio = false,
          animation           = if (reducedMotion) false else js.Dynamic.literal(duration = 200),
          interaction         = js.Dynamic.literal(
            mode      = if (isHBar) "nearest" else "index",
            axis      = if (isHBar) "y" else "x",
            intersect = false
          ),
          plugins             = js.Dynamic.literal(
            legend  = js.Dynamic.literal(
              display  = showLegend,
              position = "top",
              align    = "end",
              labels   = js.Dynamic.literal(
                color         = theme.textColor,
                boxWidth      = 10,
                boxHeight     = 10,
                usePointStyle = true,
                padding       = 10,
                font          = js.Dynamic.literal(size = 11)
              )
            ),
            tooltip = js.Dynamic.literal(
              backgroundColor = if (theme.isDark) "rgba(17, 24, 39, 0.94)" else "rgba(15, 23, 42, 0.92)",
              titleColor      = "#f9fafb",
              bodyColor       = "#f3f4f6",
              footerColor     = "#d1d5db",
              borderColor     = theme.borderColor,
              borderWidth     = 1,
              padding         = 9,
              callbacks       = tooltipCallbacks
            )
          ),
          scales              = scalesObj
        )
      )
    }

    private def asDouble(v: js.Any): Double = {
      val parsed = js.Dynamic.global.Number(v).asInstanceOf[Double]
      if (parsed.isNaN || parsed.isInfinite) 0.0 else parsed
    }

    private def formatTimestamp(epochMs: Long, includeSeconds: Boolean): String = {
      val d = new js.Date(epochMs.toDouble)
      def pad2(n: Double): String = {
        val i = n.toInt
        if (i < 10) s"0$i" else i.toString
      }
      if (includeSeconds)
        s"${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}"
      else
        s"${pad2(d.getHours())}:${pad2(d.getMinutes())}"
    }

    private def formatBytesValue(bytes: Double): String = {
      val b = math.max(0.0, bytes)
      if (b < 1024.0) f"${b.round} B"
      else if (b < 1024.0 * 1024.0) f"${b / 1024.0}%.1f KB"
      else if (b < 1024.0 * 1024.0 * 1024.0) f"${b / (1024.0 * 1024.0)}%.1f MB"
      else f"${b / (1024.0 * 1024.0 * 1024.0)}%.2f GB"
    }

    private def formatUnitCompact(value: Double, unit: String): String =
      unit match {
        case "bytes"       => formatBytesValue(value)
        case "rate"        => if (value == 0.0) "0/s" else if (value < 10.0) f"$value%.1f/s" else f"${value.round}/s"
        case "duration-ms" => if (value < 1000.0) f"${value.round} ms" else f"${value / 1000.0}%.1fs"
        case _             => f"${value.round}"
      }

    private def formatUnitDetail(value: Double, unit: String): String =
      unit match {
        case "bytes"       => formatBytesValue(value)
        case "rate"        => f"$value%.1f/s"
        case "duration-ms" => if (value < 1000.0) f"${value.round} ms" else f"${value / 1000.0}%.2fs"
        case _             => f"${value.round}"
      }
  }
}
