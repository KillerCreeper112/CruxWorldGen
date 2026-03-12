package killercreepr.cruxworldgen.bukkit.block

import killercreepr.cruxworldgen.api.block.BlockSectionReader
import killercreepr.cruxworldgen.api.block.MultiBlockDataResolver
import killercreepr.cruxworldgen.api.block.MultiBlockSectionReader
import killercreepr.cruxworldgen.api.block.SimpleMultiBlockSectionReader

//normal and multi resolvers are separated mainly just so others must explicitly know that they need to register something
object BukkitBlockAdapter {
  private val blockResolver = MultiBukkitBlockDataResolver("minecraft")
  fun resolver() : BukkitBlockDataResolver = blockResolver
  fun multiResolver() : MultiBlockDataResolver = blockResolver

  private val blockReader = SimpleMultiBlockSectionReader(LinkedHashMap(), BukkitBlockSectionReader.INSTANCE)
  fun reader(): BlockSectionReader = blockReader
  fun multiReader(): MultiBlockSectionReader = blockReader
}