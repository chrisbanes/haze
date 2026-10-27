// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

@Suppress("ConstPropertyName", "ktlint:standard:property-naming")
internal object GlassTraceSection {
  const val ContainerPlacement = "HazeGlass.containerPlacement"
  const val ContainerRecluster = "HazeGlass.containerRecluster"
  const val ContainerTranslate = "HazeGlass.containerTranslate"
  const val ContainerCluster = "HazeGlass.containerCluster"
  const val ContainerReconcile = "HazeGlass.containerReconcile"
  const val Prepare = "HazeGlass.prepare"
  const val PrepareBudget = "HazeGlass.prepareBudget"
  const val SelectDelegate = "HazeGlass.selectDelegate"
  const val DelegatePrepare = "HazeGlass.delegatePrepare"
  const val PrepareEffects = "HazeGlass.prepareEffects"
  const val PrepareLayers = "HazeGlass.prepareLayers"
  const val CreateRenderEffect = "HazeGlass.createRenderEffect"
  const val RuntimeDraw = "HazeGlass.runtimeDraw"
  const val Source = "HazeGlass.source"
  const val Blur = "HazeGlass.blur"
  const val Depth = "HazeGlass.depth"
  const val Optical = "HazeGlass.optical"
  const val Detail = "HazeGlass.detail"
  const val Rim = "HazeGlass.rim"
  const val InteractionOptical = "HazeGlass.interactionOptical"
  const val InteractionDetail = "HazeGlass.interactionDetail"
  const val InteractionLighting = "HazeGlass.interactionLighting"
  const val GroupAlpha = "HazeGlass.groupAlpha"
  const val Compose = "HazeGlass.compose"
  const val FallbackDraw = "HazeGlass.fallbackDraw"
  const val FallbackForeground = "HazeGlass.fallbackForeground"

  val all = listOf(
    ContainerPlacement,
    ContainerRecluster,
    ContainerTranslate,
    ContainerCluster,
    ContainerReconcile,
    Prepare,
    PrepareBudget,
    SelectDelegate,
    DelegatePrepare,
    PrepareEffects,
    PrepareLayers,
    CreateRenderEffect,
    RuntimeDraw,
    Source,
    Blur,
    Depth,
    Optical,
    Detail,
    Rim,
    InteractionOptical,
    InteractionDetail,
    InteractionLighting,
    GroupAlpha,
    Compose,
    FallbackDraw,
    FallbackForeground,
  )
}
