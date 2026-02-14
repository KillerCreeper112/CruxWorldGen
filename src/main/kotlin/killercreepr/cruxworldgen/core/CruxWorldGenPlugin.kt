package killercreepr.cruxworldgen.core

import com.mojang.brigadier.arguments.StringArgumentType
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import killercreepr.crux.core.plugin.CruxPlugin
import killercreepr.crux.core.util.CruxWorldUtil
import killercreepr.cruxworldgen.test6.BukkitGen
import killercreepr.cruxworldgen.test6.DecorationPipeline
import killercreepr.cruxworldgen.test6.GenerationPipeline
import killercreepr.cruxworldgen.test6.prop.PropPointGrid
import killercreepr.cruxworldgen.test6.structure.StructurePipeline
import killercreepr.cruxworldgen.test6.structure.StructureRegistry
import killercreepr.cruxworldgen.test6.structure.test.TinyHutFeature
import killercreepr.cruxworldgen.test6.zone.TestZone
import killercreepr.cruxworldgen.test6.zone.ZoneRegistry
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

                  val structures = StructurePipeline(
                    StructureRegistry(
                      listOf(
                        TinyHutFeature()
                        // add more features here
                      )
                    )
                  )

                  val world = WorldCreator(name).generator(
                    BukkitGen(
                      GenerationPipeline(
                        ZoneRegistry(listOf(
                          TestZone()
                        ))
                      ),
                      DecorationPipeline(PropPointGrid()),
                      structures
                    )
                  ).createWorld()
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