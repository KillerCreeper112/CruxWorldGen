package killercreepr.cruxworldgen.api.cave

import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext

interface CaveType {
  fun carveBlocks(ctx: GenerateContext, cave: CaveContext): Double
  fun addBlocks(ctx: GenerateContext, cave: CaveContext, add : Double): Double = 0.0

  /**
   * If surfaceFadeStart = 0
   * carving is allowed to start right at the surface.
   *
   * If surfaceFadeStart = 6
   * carving is blocked for the top 6 blocks, and only starts at depth 6.
   */
  val surfaceFadeStart: Int get() = 0//6

  /**
   * how many blocks does it take to ramp up to full strength?
   * Small ramp (like 6–10): caves ramp to full strength quickly
   * -> good for ravines / openings / dramatic mouths
   *
   * Large ramp (like 16–32): caves ramp in slowly
   * -> good for normal caves (surface stays mostly intact)
   */
  val surfaceFadeRamp: Int get() = 16
}