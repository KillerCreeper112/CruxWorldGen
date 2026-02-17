package killercreepr.cruxworldgen.core.context

import killercreepr.cruxworldgen.api.context.terrain.Terrain2D
import killercreepr.cruxworldgen.api.context.terrain.TerrainSnapshot

class SimpleTerrainSnapshot(
  override val terrain2D: Terrain2D
) : TerrainSnapshot {
}
