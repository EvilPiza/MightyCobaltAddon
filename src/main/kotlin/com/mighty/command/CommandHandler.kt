package com.mighty.command

import com.mighty.command.commands.ToCommand
import org.cobalt.command.Command
import org.cobalt.command.annotation.DefaultHandler
import org.cobalt.command.annotation.SubCommand
import org.cobalt.util.ChatUtils

object CommandHandler : Command(
  name = "mighty",
  aliases = arrayOf("mighty").toList()
) {
  @DefaultHandler
  fun main() {
    ChatUtils.sendSystemMessage("what does bro want?")
  }

  @SubCommand
  fun to(x: Int, y: Int, z: Int) {
    ToCommand.execute(x, y, z)
  }
}
