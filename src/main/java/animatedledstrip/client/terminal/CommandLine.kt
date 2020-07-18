/*
 *  Copyright (c) 2020 AnimatedLEDStrip
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */

package animatedledstrip.client.terminal

import animatedledstrip.animationutils.Animation
import animatedledstrip.animationutils.AnimationData
import animatedledstrip.animationutils.EndAnimation
import animatedledstrip.client.AnimationSender
import animatedledstrip.leds.AnimatedLEDStrip
import animatedledstrip.leds.StripInfo
import animatedledstrip.utils.*
import com.googlecode.lanterna.SGR
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.graphics.TextGraphics
import com.googlecode.lanterna.input.KeyType
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.googlecode.lanterna.terminal.Terminal
import io.github.maxnz.parser.CommandParser
import io.github.maxnz.parser.action
import io.github.maxnz.parser.command
import io.github.maxnz.parser.commandGroup
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.com.intellij.util.containers.Stack
import org.pmw.tinylog.Configurator
import org.pmw.tinylog.Level

class CommandLine(port: Int) {

    private val sender = AnimationSender("localhost", port)

    private var endCmdLine = false
    private var showAnimationInfos = false

    private lateinit var terminal: Terminal
    private lateinit var textGraphics: TextGraphics

    private var inputStr = ""

    private val inputHistory = Stack<String>()
    private val inputFuture = Stack<String>()

    private var onHistory = false

    private val output = mutableListOf<Pair<String, MessageType>>()

    private var outputFirstIndex = 0

    private val helpMessage = """
                Type commands and press enter to send them to the server.
                Type "help" to view available commands.
                 
                Use up and down arrows to view command history.
                Use page up and page down to view output history.
                 
            """.trimIndent()

    init {
        Configurator.defaultConfig().level(Level.INFO).activate()
    }

    private fun Terminal.clearLine(line: Int) {
        setCursorPosition(0, line)
        for (i in 0 until terminalSize.columns)
            putCharacter(' ')
        flush()
    }

    private fun Terminal.resetCursor() {
        setCursorPosition(inputStr.length, terminalSize.rows)
    }

    private fun Terminal.clearInput() {
        clearLine(terminalSize.rows)
        resetCursor()
        flush()
    }

    private fun Terminal.putString(str: String) {
        str.forEach { putCharacter(it) }
    }

    private fun println(message: String, type: MessageType) {
        message.split("\n").forEach { textGraphics.putLongText(it, type) }
        terminal.resetCursor()
        terminal.flush()
    }

    private fun TextGraphics.putLongText(text: String, type: MessageType) {
        var remainingText = text.filter { it != 0.toChar() }
        while (remainingText.isNotEmpty()) {
            val textToPrint = remainingText.take(this.size.columns)
            output.add(Pair(textToPrint, type))
            remainingText = remainingText.removePrefix(textToPrint)
        }
        outputFirstIndex = (output.lastIndex - (size.rows - 2)).coerceAtLeast(0)
        putLines()
    }

    private fun TextGraphics.putLines() {
        (outputFirstIndex..(outputFirstIndex + size.rows)).forEachIndexed { row, i ->
            val line = output.getOrElse(i) { Pair("", false) }
            when (line.second) {
                MessageType.NORMAL -> {
                    foregroundColor = TextColor.ANSI.DEFAULT
                    disableModifiers(SGR.BOLD)
                }
                MessageType.NORMAL_BOLD -> {
                    foregroundColor = TextColor.ANSI.DEFAULT
                    enableModifiers(SGR.BOLD)
                }
                MessageType.COMMAND -> {
                    foregroundColor = TextColor.ANSI.GREEN
                    enableModifiers(SGR.BOLD)
                }
                MessageType.CONNECTION -> {
                    foregroundColor = TextColor.ANSI.BLUE
                    enableModifiers(SGR.BOLD)
                }
                MessageType.TERMINAL_MESSAGE -> {
                    foregroundColor = TextColor.ANSI.CYAN
                    disableModifiers(SGR.BOLD)
                }
                MessageType.TERMINAL_MESSAGE_BOLD -> {
                    foregroundColor = TextColor.ANSI.CYAN
                    enableModifiers(SGR.BOLD)
                }
            }
            terminal.clearLine(row)
            terminal.flush()
            putString(0, row, line.first)
            foregroundColor = TextColor.ANSI.DEFAULT
            disableModifiers(SGR.BOLD)
        }
    }

    private fun sendCmd(cmd: String) {
        sender.sendBytes("CMD :$cmd".toByteArray())
    }

    fun start() = runBlocking {
        terminal = DefaultTerminalFactory().createTerminal()
        terminal.enterPrivateMode()
        terminal.addResizeListener { terminal, terminalSize ->
            terminal.clearLine(terminal.cursorPosition.row)
            terminal.setCursorPosition(0, terminalSize.rows)
            terminal.putString(inputStr)
            textGraphics.putLines()
            terminal.resetCursor()
            terminal.flush()
        }
        textGraphics = terminal.newTextGraphics()

        println("Welcome to the AnimatedLEDStrip Server console", MessageType.TERMINAL_MESSAGE_BOLD)
        println(helpMessage, MessageType.TERMINAL_MESSAGE)
        sender
            .setOnReceiveCallback {
                printFormattedData(it)
            }
            .setOnConnectCallback { ip, port ->
                println("Connected to $ip:$port", MessageType.CONNECTION)
            }
            .setOnDisconnectCallback { ip, port ->
                println("Disconnected from $ip:$port", MessageType.CONNECTION)
                showAnimationInfos = false
            }
            .setOnUnableToConnectCallback { ip, port ->
                println("Could not connect to $ip:$port", MessageType.CONNECTION)
            }

        val parser = CommandParser<CommandLine, Unit>(this@CommandLine)

        parser.apply {

            helpParagraph = helpMessage

            helpMessageAction = { _, msg ->
                println("Terminal Help", type = MessageType.TERMINAL_MESSAGE_BOLD)
                println(msg, type = MessageType.TERMINAL_MESSAGE)
                if (sender.connected) {
                    println(" \nServer Help", type = MessageType.NORMAL_BOLD)
                    sendCmd("help")
                }
            }

            badCommandAction = { _, cmd ->
                if (sender.connected) {
                    showAnimationInfos = true
                    sendCmd(cmd.removePrefix("Bad Command: "))
                } else {
                    println(cmd, MessageType.NORMAL)
                }
            }
            commandGroup("terminal") {
                command("exit") {
                    description = "Exit the terminal"

                    action { _, _ ->
                        sender.end()
                        endCmdLine = true
                    }
                }

                command("connect") {
                    description = "Connect to a server"
                    argHelpStr = "[IP [PORT]]"

                    action { _, args ->
                        sender.end()
                        if (args.getOrNull(0) != null) sender.setIPAddress(args[0])
                        if (args.getOrNull(1) != null) {
                            if (args[1].toIntOrNull() == null) {
                                println("Port ${args[1]} is not a valid integer", MessageType.NORMAL)
                                return@action
                            }
                            sender.setPort(args[1].toInt())
                        }
                        sender.start()
                    }
                }

                command("disconnect") {
                    description = "Disconnect from a server"

                    action { _, _ ->
                        sender.end()
                    }
                }
            }
        }

        input@ while (!endCmdLine) {
            terminal.clearInput()

            read@ while (!endCmdLine) {
                val key = terminal.pollInput() ?: continue@read
                when (key.keyType) {
                    KeyType.Enter -> {
                        while (inputFuture.isNotEmpty()) inputHistory.push(inputFuture.pop())
                        inputHistory.push(inputStr)
                        terminal.clearInput()
                        println(inputStr, type = MessageType.COMMAND)
                        break@read
                    }
                    KeyType.Backspace -> {
                        inputStr = inputStr.dropLast(1)
                        terminal.resetCursor()
                        terminal.putCharacter(' ')
                        terminal.resetCursor()
                        terminal.flush()
                        continue@read
                    }
                    KeyType.Character -> {
                        inputStr += key.character
                        terminal.putCharacter(key.character)
                        terminal.resetCursor()
                        terminal.flush()
                        continue@read
                    }
                    KeyType.ArrowDown -> {
                        when {
                            inputFuture.isNotEmpty() -> {
                                if (inputStr.isNotEmpty()) inputHistory.push(inputStr)
                                inputStr = ""
                                terminal.clearInput()
                                inputStr = inputFuture.pop()
                                terminal.putString(inputStr)
                                terminal.resetCursor()
                                terminal.flush()
                            }
                            onHistory -> {
                                onHistory = false
                                inputHistory.push(inputStr)
                                inputStr = ""
                                terminal.clearInput()
                                terminal.flush()
                            }
                            else -> {
                            }
                        }
                    }
                    KeyType.ArrowUp -> {
                        if (inputHistory.isNotEmpty()) {
                            if (onHistory) inputFuture.push(inputStr)
                            inputStr = ""
                            terminal.clearInput()
                            inputStr = inputHistory.pop()
                            terminal.putString(inputStr)
                            onHistory = true
                            terminal.flush()
                        }
                    }
                    KeyType.PageUp -> {
                        outputFirstIndex = (outputFirstIndex - (textGraphics.size.rows - 2)).coerceAtLeast(0)
                        textGraphics.putLines()
                        terminal.resetCursor()
                        terminal.flush()
                    }
                    KeyType.PageDown -> {
                        outputFirstIndex =
                            (outputFirstIndex + (textGraphics.size.rows - 2)).coerceAtMost(output.lastIndex - (textGraphics.size.rows - 2))
                        textGraphics.putLines()
                        terminal.resetCursor()
                        terminal.flush()
                    }
                }
            }

            parser.parseCommand(inputStr, Unit)

            inputStr = ""
        }
        terminal.exitPrivateMode()
    }

    private fun printFormattedData(data: String) {
        for (jsonStr in data.split(DELIMITER)) {
            when (jsonStr.getDataTypePrefix()) {
                AnimationData.prefix -> println(
                    jsonStr.jsonToAnimationData().toHumanReadableString(),
                    MessageType.NORMAL
                )
                StripInfo.prefix -> println(jsonStr.jsonToStripInfo().toHumanReadableString(), MessageType.NORMAL)
                EndAnimation.prefix -> println(jsonStr.jsonToEndAnimation().toHumanReadableString(), MessageType.NORMAL)
                AnimatedLEDStrip.sectionPrefix -> println(
                    jsonStr.jsonToSection().toHumanReadableString(),
                    MessageType.NORMAL
                )
                Animation.AnimationInfo.prefix -> {
                    // Guard prevents animations from being printed when first connected
                    // but allows them if requested
                    if (showAnimationInfos) println(
                        jsonStr.jsonToAnimationInfo().toHumanReadableString(),
                        MessageType.NORMAL
                    )
                }
                else -> if (jsonStr != "") println(jsonStr, MessageType.NORMAL)
            }
        }
    }
}
