package com.mighty.pathfinder.helper

import net.minecraft.util.math.Box
import org.cobalt.api.event.annotation.SubscribeEvent
import org.cobalt.api.event.impl.render.WorldRenderContext
import org.cobalt.api.event.impl.render.WorldRenderEvent
import org.cobalt.api.util.render.Render3D
import java.awt.Color

object PathRenderer {

  private var renderBoxes: List<Box> = emptyList()
  private var color: Color = Color(241, 188, 0)

  @SubscribeEvent
  fun onWorldRender(event: WorldRenderEvent) {
    render(event.context)
  }

  fun setPath(path: List<Node>?) {
    renderBoxes = path?.map { node ->
      val x = node.data.pos.x.toDouble()
      val y = node.data.pos.y.toDouble()
      val z = node.data.pos.z.toDouble()
      Box(x, y, z, x + 1, y + 1, z + 1)
    } ?: emptyList()
  }

  fun setColor(newColor: Color) {
    color = newColor
  }

  fun getPath(): List<Box> = renderBoxes

  fun render(context: WorldRenderContext) {
    for (box in renderBoxes) {
      Render3D.drawBox(context, box, color = color, true)
    }
  }
}
