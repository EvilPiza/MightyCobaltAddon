package com.mightystore.command.commands

import com.mightystore.pathfinder.PathWalker
import com.mightystore.pathfinder.Pathfinder
import com.mightystore.pathfinder.helper.PathRenderer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.math.floor
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import org.cobalt.util.chat.ChatUtils

object ToCommand {
  private val mc = Minecraft.getInstance()

  private val searchExecutor = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "mighty-pathfinder").apply { isDaemon = true }
  }

  @Volatile
  private var activeRequestId = 0

  fun shutdown() {
    activeRequestId++
    searchExecutor.shutdownNow()
  }

  fun execute(x: Int, y: Int, z: Int) {
    val player = mc.player ?: run {
      ChatUtils.sendSystemMessage("Player not found!")
      return
    }

    val requestId = ++activeRequestId
    PathWalker.stop()
    PathRenderer.setPath(null)

    val startPos = playerStandingBlock(player)
    val endPos = targetStandingBlock(x, y, z)

    ChatUtils.sendSystemMessage(
      "§aCalculating path from §e${startPos.x}, ${startPos.y + 2}, ${startPos.z}§a to §e${x}, ${y}, ${z}§a..."
    )

    searchExecutor.submit {
      runSearch(requestId, startPos, endPos, x, y, z)
    }
  }

  private fun runSearch(requestId: Int, startPos: BlockPos, endPos: BlockPos, x: Int, y: Int, z: Int) {
    val pathfinder = Pathfinder(startPos, endPos)

    try {
      while (requestId == activeRequestId) {
        val latch = CountDownLatch(1)
        var chunkResult: Pathfinder.Result = Pathfinder.Result.Searching

        try {
          mc.execute {
            try {
              if (requestId == activeRequestId) {
                chunkResult = pathfinder.search()
              }
            } finally {
              latch.countDown()
            }
          }
        } catch (e: Exception) {
          latch.countDown()
          throw e
        }

        latch.await()

        when (val result = chunkResult) {
          is Pathfinder.Result.Found -> {
            mc.execute {
              if (requestId != activeRequestId) return@execute

              PathRenderer.setPath(result.path)
              PathWalker.setPath(result.path)
              ChatUtils.sendSystemMessage("Path found! ${result.path.size} (final) nodes")
            }
            return
          }

          Pathfinder.Result.NotFound -> {
            mc.execute {
              if (requestId == activeRequestId) {
                PathWalker.stop()
                ChatUtils.sendSystemMessage("No path found to target location :(")
              }
            }
            return
          }

          Pathfinder.Result.Searching -> Unit
        }
      }
    } catch (e: Exception) {
      mc.execute {
        if (requestId == activeRequestId) {
          PathWalker.stop()
          ChatUtils.sendSystemMessage("Error calculating path: ${e.message}")
        }
        e.printStackTrace()
      }
    }
  }

  private fun playerStandingBlock(player: LocalPlayer): BlockPos =
    BlockPos(floor(player.x).toInt(), floor(player.y).toInt() - 1, floor(player.z).toInt())

  private fun targetStandingBlock(x: Int, y: Int, z: Int): BlockPos =
    BlockPos(x, y - 2, z)
}
