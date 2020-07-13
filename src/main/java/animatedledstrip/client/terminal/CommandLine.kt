package animatedledstrip.client.terminal

import animatedledstrip.animationutils.Animation
import animatedledstrip.animationutils.AnimationData
import animatedledstrip.animationutils.EndAnimation
import animatedledstrip.client.AnimationSender
import animatedledstrip.leds.AnimatedLEDStrip
import animatedledstrip.leds.StripInfo
import animatedledstrip.utils.*
import com.googlecode.lanterna.graphics.TextGraphics
import com.googlecode.lanterna.input.KeyType
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.googlecode.lanterna.terminal.Terminal
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.com.intellij.util.containers.Stack
import org.pmw.tinylog.Configurator
import org.pmw.tinylog.Level

class CommandLine(port: Int, private val quiet: Boolean = false) {

    private val sender = AnimationSender("localhost", port)

    private var endCmdLine = false
    private var showAnimationInfos = false

    private lateinit var terminal: Terminal
    private lateinit var textGraphics: TextGraphics

    private var inputStr = ""

    private val inputHistory = Stack<String>()
    private val inputFuture = Stack<String>()

    private var onHistory = false

    private var printLine = 0

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

    private fun println(message: String) {
        if (!quiet) {
            kotlin.io.println(message)
            message.split("\n").forEach { textGraphics.putLongText(it) }
            terminal.clearLine((printLine) % (terminal.terminalSize.rows - 2))
            terminal.clearLine((printLine + 1) % (terminal.terminalSize.rows - 2))
            terminal.resetCursor()
            terminal.flush()
        }
    }

    private fun TextGraphics.putLongText(text: String) {
        var remainingText = text.filter { it != 0.toChar() }
        while (remainingText.isNotEmpty()) {
            terminal.clearLine(printLine)
            putString(0, printLine, remainingText.take(this.size.columns))
            remainingText = remainingText.drop(this.size.columns)
            if (printLine < this.size.rows - 2) printLine++ else printLine = 0
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
            terminal.flush()
        }
        textGraphics = terminal.newTextGraphics()

        println("Welcome to the AnimatedLEDStrip Server console")
        sender
            .setOnReceiveCallback {
                printFormattedData(it)
            }
            .setOnDisconnectCallback {
                println("Disconnected")
                endCmdLine = true
            }
            .start()

        println("Connected")

        input@ while (!endCmdLine) {
            terminal.clearInput()

            read@ while (!endCmdLine) {
                val key = terminal.pollInput() ?: continue@read
                when (key.keyType) {
                    KeyType.Enter -> {
                        while (inputFuture.isNotEmpty()) inputHistory.push(inputFuture.pop())
                        inputHistory.push(inputStr)
                        terminal.clearInput()
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
                            else -> {}
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
                }
            }

            when (inputStr.toUpperCase()) {
                "" -> continue@input
                "EXIT" -> {
                    sender.end()
                    return@runBlocking
                }
                "Q", "QUIT" -> {
                    sendCmd(inputStr)
                    sender.end()
                    return@runBlocking
                }
                "CLEAR" -> {
                    terminal.clearScreen()
                    terminal.flush()
                    printLine = 0
                }
                else -> sendCmd(inputStr)
            }

            // Show AnimationInfos if requested
            if (inputStr.startsWith("animation", ignoreCase = true)
                || inputStr.startsWith("a", ignoreCase = true)
            ) showAnimationInfos = true

            inputStr = ""
        }
        terminal.exitPrivateMode()
    }

    private fun printFormattedData(data: String) {
        for (jsonStr in data.split(";")) {
            when (jsonStr.getDataTypePrefix()) {
                AnimationData.prefix -> println(jsonStr.jsonToAnimationData().toHumanReadableString())
                StripInfo.prefix -> println(jsonStr.jsonToStripInfo().toHumanReadableString())
                EndAnimation.prefix -> println(jsonStr.jsonToEndAnimation().toHumanReadableString())
                AnimatedLEDStrip.sectionPrefix -> println(jsonStr.jsonToSection().toHumanReadableString())
                Animation.AnimationInfo.prefix -> {
                    // Guard prevents animations from being printed when first connected
                    // but allows them if requested
                    if (showAnimationInfos) println(jsonStr.jsonToAnimationInfo().toHumanReadableString())
                }
                else -> if (jsonStr != "") println(jsonStr)
            }
        }
    }
}
