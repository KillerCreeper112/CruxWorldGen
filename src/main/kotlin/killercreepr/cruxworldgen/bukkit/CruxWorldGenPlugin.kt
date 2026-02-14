package killercreepr.cruxworldgen.bukkit

import com.mojang.brigadier.arguments.StringArgumentType
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import killercreepr.crux.core.plugin.CruxPlugin
import killercreepr.crux.core.util.CruxMath
import killercreepr.crux.core.util.CruxWorldUtil
import killercreepr.cruxworldgen.api.prop.PropPointGrid
import killercreepr.cruxworldgen.bukkit.generation.BukkitGenerationChunkGenerator
import killercreepr.cruxworldgen.bukkit.generation.WorldDetails
import killercreepr.cruxworldgen.core.decor.SimpleDecorationPipeline
import killercreepr.cruxworldgen.core.decor.SimplePropPointGrid
import killercreepr.cruxworldgen.core.generation.SimpleGenerationPipeline
import killercreepr.cruxworldgen.core.noise.SimpleNoiseBank
import killercreepr.cruxworldgen.core.structure.SimpleStructurePipeline
import killercreepr.cruxworldgen.core.structure.SimpleStructureRegistry
import killercreepr.cruxworldgen.core.zone.SimpleZoneRegistry
import killercreepr.cruxworldgen.test6.zone.TestZone
import org.bukkit.WorldCreator
import org.bukkit.entity.Player

class CruxWorldGenPlugin : CruxPlugin() {
  override fun onLoad() {
    super.onLoad()
    lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS){ event ->
      event.registrar()
        .register(
          Commands.literal("cgentest")
            .then(
              Commands.argument("name", StringArgumentType.string())
                .executes { ctx ->
                  val sender = ctx.getSource().sender

                  val name = ctx.getArgument("name", String::class.java)
                  val got = server.getWorld(name)
                  if(got != null){
                    for (player in got.players) {
                      player.teleport(server.getWorld("world")!!.spawnLocation)
                    }
                  }
                  CruxWorldUtil.deleteWorld(name)

                  val zones = SimpleZoneRegistry(
                    listOf(TestZone())
                  )
                  val structureRegistry = SimpleStructureRegistry(listOf())
                  val generation = SimpleGenerationPipeline(zones)
                  val decorations = SimpleDecorationPipeline(SimplePropPointGrid())
                  val structures = SimpleStructurePipeline(structureRegistry)

                  val seed = CruxMath.random().nextLong()
                  val noise = SimpleNoiseBank(seed)

                  val worldDetails = WorldDetails(
                    64,
                    16, 16
                  )

                  val generator = BukkitGenerationChunkGenerator(
                    generation,
                    decorations,
                    structures,
                    noise,
                    worldDetails
                  )
                  val world = WorldCreator(name).generator(
                    generator
                  ).seed(seed).createWorld()
                  (sender as? Player)?.teleport(world!!.spawnLocation)
                  sender.sendMessage("Deeeed it")
                  1
                }
            )
            .build()
        )
    }
  }
}