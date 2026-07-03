package com.mighty.module

import org.cobalt.api.event.annotation.SubscribeEvent
import org.cobalt.api.event.impl.client.TickEvent
import org.cobalt.api.module.Module
import org.cobalt.api.module.setting.impl.KeyBindSetting
import org.cobalt.api.module.setting.impl.CheckboxSetting
import org.cobalt.api.util.ChatUtils
import org.cobalt.api.util.MouseUtils
import org.cobalt.api.util.helper.KeyBind
import org.cobalt.api.event.impl.render.NvgEvent
import org.lwjgl.glfw.GLFW
import net.minecraft.client.MinecraftClient

object Main : Module(
  name = "Main tab",
) {
  var keyBind by KeyBindSetting(
    name = "Toggle Keybind",
    description = "Keybind to toggle the module",
    defaultValue = KeyBind(GLFW.GLFW_KEY_J)
  )
  var ungrabMouseToggle by CheckboxSetting(
    name = "Ungrab Mouse",
    description = "Toggle mouse grab while active",
    defaultValue = true
  )

  private var isToggled = false
  private var wasKeyPressed = false
  private var isPausedByScreen = false
  private val mc = MinecraftClient.getInstance()

  fun start() {
    isToggled = true

    if (ungrabMouseToggle == true) {
      MouseUtils.ungrabMouse()
    }

    // TODO: your start logic here
  }

  fun stop() {
    isToggled = false

    if (ungrabMouseToggle == true) {
      MouseUtils.grabMouse()
    }

    // TODO: your stop/reset logic here
  }

  @SubscribeEvent
  fun keybindListener(event: TickEvent) {
    val isPressed = keyBind.isPressed()
    if (isPressed && !wasKeyPressed) {
      isToggled = !isToggled

      if (isToggled) start()
      else stop()

      ChatUtils.sendMessage(
        "Module is now "
          + (if (isToggled) "§aEnabled" else "§cDisabled")
          + "§r"
      )
    }
    wasKeyPressed = isPressed
  }

  fun isToggled(): Boolean {
    return isToggled
  }

  @SubscribeEvent
  fun onTick(event: TickEvent) {
    if (!isToggled) {
      return
    }

    if (mc.currentScreen != null) {
      if (!isPausedByScreen) {
        isPausedByScreen = true
        ChatUtils.sendMessage("Module is now §eIdle§r while a menu is open")
      }
      return
    }

    if (isPausedByScreen) {
      isPausedByScreen = false
      ChatUtils.sendMessage("Module has §aResumed§r")
    }

    // TODO: your per-tick logic here
  }

  @SubscribeEvent
  fun onScreenRender(event: NvgEvent) {
    if (!isToggled) return

    // TODO: your HUD/overlay render logic here
  }
}
