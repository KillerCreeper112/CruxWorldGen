package killercreepr.cruxworldgen.core.context

import killercreepr.cruxworldgen.api.context.terrain.Terrain2D
import killercreepr.cruxworldgen.api.context.terrain.Terrain3D
import killercreepr.cruxworldgen.api.context.terrain.TerrainSnapshot

class SimpleTerrainSnapshot(
  override val terrain2D: Terrain2D,
  override val terrain3D: Terrain3D
) : TerrainSnapshot {
}
