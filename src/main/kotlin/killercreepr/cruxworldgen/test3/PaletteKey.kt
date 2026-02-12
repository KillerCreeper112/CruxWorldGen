package killercreepr.cruxworldgen.test3

import org.bukkit.Material

typealias PaletteKey = String

interface BiomePalette {
  /** Return a block for a semantic role like "surface.top", "surface.filler", "surface.cliff" */
  fun material(key: PaletteKey, ctx: PaletteContext): Material
}

data class PaletteContext(
  val x: Int,
  val y: Int,
  val z: Int,
  val slope: Double,        // 0..1 (you can stub as 0.0 for now)
  val depthFromTop: Int,    // 0 at top block, 1,2,... below
  val seed: Long
)
