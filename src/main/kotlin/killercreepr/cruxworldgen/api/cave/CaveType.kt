package killercreepr.cruxworldgen.api.cave

import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext

interface CaveType {
  fun carveBlocks(ctx: GenerateContext, cave: CaveContext): Double
  fun addBlocks(ctx: GenerateContext, cave: CaveContext, add : Double): Double = 0.0

  val canOpenToSky: Boolean get() = false

  /** How quickly it fades in from surface if it CAN open to sky. */
  val surfaceFadeDepth: Int get() = 4
}