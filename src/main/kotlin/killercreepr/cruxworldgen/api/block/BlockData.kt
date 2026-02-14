package killercreepr.cruxworldgen.api.block

interface BlockData {
  companion object{
    val EMPTY : BlockData = Empty()
    val NONE : BlockData = None()
  }

  class Empty : BlockData
  class None : BlockData
}