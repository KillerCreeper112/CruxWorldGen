package killercreepr.cruxworldgen.api.block

interface BlockData {
  companion object{
    val EMPTY : BlockData = Empty
    val NONE : BlockData = None
  }

  fun isLiquid(): Boolean
  fun isSolid(): Boolean
  fun isEmpty(): Boolean

  object Empty : BlockData {
    override fun isLiquid(): Boolean = false

    override fun isSolid(): Boolean = false

    override fun isEmpty(): Boolean = true
  }

  object None : BlockData {
    override fun isLiquid(): Boolean = false

    override fun isSolid(): Boolean = false

    override fun isEmpty(): Boolean = true
  }
}