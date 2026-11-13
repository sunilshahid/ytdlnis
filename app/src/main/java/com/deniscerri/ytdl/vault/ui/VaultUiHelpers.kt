package com.deniscerri.ytdl.vault.ui

import android.widget.EditText

internal object VaultUiHelpers {
    /**
     * Copies characters from an [EditText] directly into a fresh [CharArray] and clears the
     * widget's editable buffer so sensitive passphrases never linger in an immutable `String`.
     */
    fun readCharsAndClear(editText: EditText): CharArray {
        val editable = editText.text ?: return CharArray(0)
        val len = editable.length
        val chars = CharArray(len)
        editable.getChars(0, len, chars, 0)
        editable.clear()
        return chars
    }
}
