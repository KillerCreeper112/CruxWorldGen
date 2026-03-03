package killercreepr.cruxworldgen.api.context.terrain

interface Terrain3D {
  fun isCaveAir(worldX: Int, worldY: Int, worldZ: Int): Boolean
  fun localIsCaveAir(localX: Int, worldY: Int, localZ: Int) : Boolean
}