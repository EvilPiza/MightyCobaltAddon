package com.example.module

import java.awt.Color
import net.minecraft.client.MinecraftClient
import org.cobalt.api.event.EventBus
import org.cobalt.api.event.annotation.SubscribeEvent
import org.cobalt.api.event.impl.client.TickEvent
import org.cobalt.api.module.Module
import org.cobalt.api.module.setting.impl.*
import org.lwjgl.glfw.GLFW

object AutoSprint : Module(
  name = "AutoSprint",
) {

  val enabled by CheckboxSetting(
    name = "Enabled",
    description = "Enables AutoSprint",
    defaultValue = false
  )

  override fun onEnable() {
    EventBus.register(this)
    println("Enabled")
  }

  override fun onDisable() {
    EventBus.unregister(this)
    println("Disabled")
  }

  @SubscribeEvent
  fun onTick(event: TickEvent) {
    val mc = MinecraftClient.getInstance()
    mc.options.sprintKey.isPressed = true
  }

}
