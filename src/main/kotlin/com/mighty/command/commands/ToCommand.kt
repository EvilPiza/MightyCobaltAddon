package com.mighty.command.commands

import com.mighty.pathfinder.helper.PathRenderer
import com.mighty.pathfinder.PathWalker
import com.mighty.pathfinder.Pathfinder
import com.mighty.pathfinder.helper.NodeData
import net.minecraft.util.math.BlockPos
import org.cobalt.api.util.ChatUtils
import kotlin.concurrent.thread
import net.minecraft.client.MinecraftClient

private var mc = MinecraftClient.getInstance()

object ToCommand {
  fun round(number: Double): Int {
    return kotlin.math.floor(number).toInt()
  }

  fun execute(x: Int, y: Int, z: Int) {
    val player = mc.player ?: run {
      ChatUtils.sendMessage("§cPlayer not found!")
      return
    }

    val startPos = BlockPos(
      round(player.x),
      round(player.y) - 1,
      round(player.z)
    )
    val endPos = BlockPos(x, y - 2, z)

    ChatUtils.sendMessage(
      "§aCalculating path from §e${startPos.x}, ${startPos.y + 2}, ${startPos.z}§a to §e${x}, ${y}, ${z}§a..."
    )

    thread {
      try {
        val startData = NodeData(startPos, 0.0, false)
        val endData = NodeData(endPos, 0.0, false)

        val pathfinder = Pathfinder()
        val path = pathfinder.calculatePath(startData, endData)

        mc.execute {
          if (path != null && path.isNotEmpty()) {
            PathRenderer.setPath(path)
            PathWalker.setPath(path)
            ChatUtils.sendMessage("§aPath found! §e${path.size}§a (final) nodes")
          } else {
            ChatUtils.sendMessage("§cNo path found to target location :(")
          }
        }
      } catch (e: Exception) {
        mc.execute {
          ChatUtils.sendMessage("§cError calculating path: ${e.message}")
          e.printStackTrace()
        }
      }
    }
  }
}
