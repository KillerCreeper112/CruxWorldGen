package killercreepr.cruxworldgen.api.context

interface ChunkContext{
  val minHeight : Int
  val maxHeight : Int
  val width : Int
  val depth : Int
  val seaLevel : Int

  /*fun isEmpty(x: Int, y: Int, z: Int): Boolean
  fun isSolid(x: Int, y: Int, z: Int): Boolean
  fun isLiquid(x : Int, y : Int, z : Int): Boolean*/
  fun isInChunk(ctx: GenerateContext, worldX: Int, worldZ: Int): Boolean {
    val cx = ctx.chunkX // if you store it
    val cz = ctx.chunkZ
    return (worldX shr 4) == cx && (worldZ shr 4) == cz
  }
  fun isInChunk(ctx: GenerateContext, worldX: Int, worldY : Int, worldZ: Int): Boolean {
    if(worldY !in minHeight..<maxHeight) return false
    val cx = ctx.chunkX // if you store it
    val cz = ctx.chunkZ
    return (worldX shr 4) == cx && (worldZ shr 4) == cz
  }
  /*todo fun getBiome(worldX : Int, worldY : Int, worldZ : Int)
  fun getBiome(worldX : Int, worldZ : Int)*/
}