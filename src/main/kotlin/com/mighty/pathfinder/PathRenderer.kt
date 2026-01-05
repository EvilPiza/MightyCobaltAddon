package com.mighty.pathfinder

import com.mighty.command.MightyCommand
import java.awt.Color
import net.minecraft.util.math.Box
import org.cobalt.api.event.annotation.SubscribeEvent
import org.cobalt.api.event.impl.render.WorldRenderContext
import org.cobalt.api.event.impl.render.WorldRenderEvent
import org.cobalt.api.util.render.Render3D

object PathRenderer {

  @SubscribeEvent
  fun onWorldRender(event: WorldRenderEvent) {
    render(event.context)
  }

  fun render(context: WorldRenderContext) {
    val path = MightyCommand.getCachedPath() ?: return

    for (currentNode in path) {
      val render = Render3D

      val x = currentNode.data.pos.x.toDouble()
      val y = currentNode.data.pos.y.toDouble()
      val z = currentNode.data.pos.z.toDouble()

      render.drawBox(context, Box(x, y, z, x+1, y+1, z+1), color = Color(241, 188, 0), true)
    }
  }
}
