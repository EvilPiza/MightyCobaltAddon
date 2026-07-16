package com.mightystore.pathfinder.helper

import java.awt.Color
import net.minecraft.world.phys.AABB
import org.cobalt.event.annotation.SubscribeEvent
import org.cobalt.event.impl.WorldEvent
import org.cobalt.util.render.GizmoRenderer

object PathRenderer {

  private var renderBoxes: List<AABB> = emptyList()
  private var color: Color = Color(241, 188, 0)

  @SubscribeEvent
  fun onWorldRender(event: WorldEvent.Render) {
    render()
  }

  fun setPath(path: List<Node>?) {
    renderBoxes = path?.map { node ->
      val x = node.data.pos.x.toDouble()
      val y = node.data.pos.y.toDouble()
      val z = node.data.pos.z.toDouble()
      AABB(x, y, z, x + 1, y + 1, z + 1)
    } ?: emptyList()
  }

  fun setColor(newColor: Color) {
    color = newColor
  }

  fun getPath(): List<AABB> = renderBoxes

  fun render() {
    for (box in renderBoxes) {
      GizmoRenderer.drawBox(box, color)
    }
  }
}
