package com.mightystore.command.commands

import com.mightystore.pathfinder.helper.PathRenderer
import com.mightystore.pathfinder.PathWalker
import com.mightystore.pathfinder.Pathfinder
import com.mightystore.pathfinder.helper.NodeData
import net.minecraft.client.Minecraft
import kotlin.concurrent.thread
import net.minecraft.core.BlockPos
import org.cobalt.util.chat.ChatUtils

private var mc = Minecraft.getInstance()

object ToCommand {
  fun round(number: Double): Int {
    return kotlin.math.floor(number).toInt()
  }

  fun execute(x: Int, y: Int, z: Int) {
    val player = mc.player ?: run {
      ChatUtils.sendSystemMessage("Player not found!")
      return
    }

    val startPos = BlockPos(
      round(player.x),
      round(player.y) - 1,
      round(player.z)
    )
    val endPos = BlockPos(x, y - 2, z)

    ChatUtils.sendSystemMessage(
      "§aCalculating path from §e${startPos.x}, ${startPos.y + 2}, ${startPos.z}§a to §e${x}, ${y}, ${z}§a..."
    )

    thread {
      try {
        val startData = NodeData(startPos, 0.0)
        val endData = NodeData(endPos, 0.0)

        val pathfinder = Pathfinder()
        val path = pathfinder.calculatePath(startData, endData)

        mc.execute {
          if (path != null && path.isNotEmpty()) {
            PathRenderer.setPath(path)
            PathWalker.setPath(path)
            ChatUtils.sendSystemMessage("Path found! ${path.size} (final) nodes")
          } else {
            ChatUtils.sendSystemMessage("No path found to target location :(")
          }
        }
      } catch (e: Exception) {
        mc.execute {
          ChatUtils.sendSystemMessage("Error calculating path: ${e.message}")
          e.printStackTrace()
        }
      }
    }
  }
}
