/*
 * IdeaVim - Vim emulator for IDEs based on the IntelliJ platform
 * Copyright (C) 2003-2019 The IdeaVim authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.maddyhome.idea.vim.ex.handler

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.maddyhome.idea.vim.VimPlugin
import com.maddyhome.idea.vim.command.CommandState
import com.maddyhome.idea.vim.command.SelectionType
import com.maddyhome.idea.vim.common.TextRange
import com.maddyhome.idea.vim.ex.CommandHandler
import com.maddyhome.idea.vim.ex.CommandHandler.Flag.ARGUMENT_REQUIRED
import com.maddyhome.idea.vim.ex.CommandHandler.Flag.RANGE_OPTIONAL
import com.maddyhome.idea.vim.ex.CommandHandler.Flag.WRITABLE
import com.maddyhome.idea.vim.ex.CommandParser
import com.maddyhome.idea.vim.ex.ExCommand
import com.maddyhome.idea.vim.ex.ExException
import com.maddyhome.idea.vim.ex.InvalidRangeException
import com.maddyhome.idea.vim.ex.LineRange
import com.maddyhome.idea.vim.ex.commands
import com.maddyhome.idea.vim.ex.flags
import com.maddyhome.idea.vim.handler.CaretOrder
import com.maddyhome.idea.vim.helper.EditorHelper
import com.maddyhome.idea.vim.helper.MessageHelper
import com.maddyhome.idea.vim.helper.Msg
import java.util.*

class MoveTextHandler : CommandHandler(
        commands("m[ove]"),
        flags(RANGE_OPTIONAL, ARGUMENT_REQUIRED, WRITABLE)
) {
  @Throws(ExException::class)
  override fun execute(editor: Editor, context: DataContext, cmd: ExCommand): Boolean {
    val carets = EditorHelper.getOrderedCaretsList(editor, CaretOrder.DECREASING_OFFSET)
    val caretModel = editor.caretModel
    val caretCount = caretModel.caretCount

    val texts = ArrayList<String>(caretCount)
    val ranges = ArrayList<TextRange>(caretCount)
    var line = EditorHelper.getFileSize(editor)
    val command = CommandParser.getInstance().parse(cmd.argument)

    var lastRange: TextRange? = null
    for (caret in carets) {
      val range = cmd.getTextRange(editor, caret, context, false)
      val lineRange = cmd.getLineRange(editor, caret, context)

      line = Math.min(line, normalizeLine(editor, caret, context, command, lineRange))
      texts.add(EditorHelper.getText(editor, range.startOffset, range.endOffset))

      if (lastRange == null || lastRange.startOffset != range.startOffset && lastRange.endOffset != range.endOffset) {
        ranges.add(range)
        lastRange = range
      }
    }

    for (range in ranges) {
      editor.document.deleteString(range.startOffset, range.endOffset)
    }

    for (i in 0 until caretCount) {
      val caret = carets[i]
      val text = texts[i]

      val offset = VimPlugin.getMotion().moveCaretToLineStart(editor, line + 1)
      VimPlugin.getCopy().putText(editor, caret, context, text, SelectionType.LINE_WISE, CommandState.SubMode.NONE,
              offset, 1, true, false)
    }

    return true
  }

  @Throws(InvalidRangeException::class)
  private fun normalizeLine(editor: Editor, caret: Caret, context: DataContext,
                            command: ExCommand, lineRange: LineRange): Int {
    var line = command.ranges.getFirstLine(editor, caret, context)
    val adj = lineRange.endLine - lineRange.startLine + 1
    if (line >= lineRange.endLine)
      line -= adj
    else if (line >= lineRange.startLine) throw InvalidRangeException(MessageHelper.message(Msg.e_backrange))

    return line
  }
}
