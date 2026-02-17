package killercreepr.cruxworldgen.api.context.terrain

interface Terrain2D {
  fun surfaceY(worldX : Int, worldZ : Int) : Int
  fun skySurfaceY(worldX : Int, worldZ : Int) : Int
  fun oceanFloorY(worldX : Int, worldZ : Int) : Int
  fun waterDepth(worldX : Int, worldZ : Int) : Int

  fun isInBounds(worldX : Int, worldZ : Int) : Boolean

  fun isOceanColumn(wx: Int, wz: Int): Boolean
  fun seaSurfaceY(wx: Int, wz: Int): Int
  fun topY(wx: Int, wz: Int): Int
}