package com.mighty.command

import com.mighty.command.commands.ToCommand
import org.cobalt.api.command.Command
import org.cobalt.api.command.annotation.DefaultHandler
import org.cobalt.api.command.annotation.SubCommand
import org.cobalt.api.util.ChatUtils

object CommandHandler : Command(
  name = "mighty",
  aliases = arrayOf("mighty")
) {
  @DefaultHandler
  fun main() {
    ChatUtils.sendMessage("what does bro want?")
  }

  @SubCommand
  fun to(x: Int, y: Int, z: Int) {
    ToCommand.execute(x, y, z)
  }
}
