package killercreepr.cruxworldgen.api.structure

interface StructureInstance {
  val worldX: Int
  val worldY: Int
  val worldZ: Int
  val rot: Int    // 0/90/180/270
  val seed: Long
}