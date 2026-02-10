package killercreepr.cruxworldgen.api.world

open class GenerationPipeline {
    val generators = mutableMapOf<GenerationStage, MutableList<StageGenerator>>()

    fun register(generator: StageGenerator) {
        generators.getOrPut(generator.stage) { mutableListOf() }.add(generator)
    }

    fun runStage(stage: GenerationStage, context: ChunkGenerationContext) {
        generators[stage]?.forEach { it.generate(context) }
    }
}
