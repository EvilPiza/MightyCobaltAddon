package com.example.module

import net.minecraft.client.MinecraftClient
import org.cobalt.api.event.EventBus
import org.cobalt.api.event.annotation.SubscribeEvent
import org.cobalt.api.event.impl.client.TickEvent
import org.cobalt.api.module.Module
import org.cobalt.api.module.setting.impl.CheckboxSetting

object AutoSprint : Module(
  name = "AutoSprint",
) {

  val enabled by CheckboxSetting(
    name = "Enabled",
    description = "Enables AutoSprint",
    defaultValue = false
  )

  init {
    EventBus.register(this)
  }

  @SubscribeEvent
  fun onTick(event: TickEvent) {
    if (!enabled) return
    val mc = MinecraftClient.getInstance()
    mc.options.sprintKey.isPressed = true
  }

}
