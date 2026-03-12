package killercreepr.cruxworldgen.api.block

import killercreepr.cruxworldgen.api.context.LimitedRegion
import java.util.*

interface BlockSectionReader {
  fun readBlock(region: LimitedRegion, x: Int, y: Int, z: Int): BlockSection
  fun canReadBlock(region: LimitedRegion, x: Int, y: Int, z: Int) : Boolean = true
}

interface MultiBlockSectionReader : BlockSectionReader{
  fun registerReader(id : String, reader : BlockSectionReader)
}

class SimpleMultiBlockSectionReader(
  val registry: SequencedMap<String, BlockSectionReader>,
  val fallbackReader: BlockSectionReader,
) : MultiBlockSectionReader {
  override fun registerReader(id: String, reader: BlockSectionReader) {
    registry[id] = reader
  }

  override fun readBlock(
    region: LimitedRegion,
    x: Int,
    y: Int,
    z: Int
  ): BlockSection {
    for (reader in registry.values) {
      if(reader.canReadBlock(region, x,y,z)) return reader.readBlock(region,x,y,z)
    }
    return fallbackReader.readBlock(region,x,y,z)
  }

}