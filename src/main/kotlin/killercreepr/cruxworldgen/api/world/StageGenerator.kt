package killercreepr.cruxworldgen.api.world

interface StageGenerator {
    val stage: GenerationStage
    fun generate(context: ChunkGenerationContext)
}
