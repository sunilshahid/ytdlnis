package com.deniscerri.ytdl.lyrics.romanize

import com.deniscerri.ytdl.lyrics.LyricsDocument
import com.deniscerri.ytdl.lyrics.LyricsLine
import com.deniscerri.ytdl.lyrics.LyricsWord

/**
 * High-performance, 100% offline transliteration engine.
 *
 * Supports:
 * - Indic scripts: Devanagari (Hindi, Marathi, Nepali, Sanskrit), Gurmukhi (Punjabi)
 * - CJK: Japanese Kana (Hiragana & Katakana Hepburn), Korean Hangul (Revised Romanization)
 * - Cyrillic: Russian, Ukrainian, Belarusian
 *
 * Runs strictly offline with zero network and zero external dependencies.
 */
object Romanizer {

    fun canRomanize(text: String): Boolean {
        for (i in 0 until text.length) {
            val c = text[i]
            if (c in '\u0900'..'\u097F' || // Devanagari
                c in '\u0A00'..'\u0A7F' || // Gurmukhi
                c in '\u3040'..'\u30FF' || // Hiragana & Katakana
                c in '\uAC00'..'\uD7AF' || // Hangul Syllables
                c in '\u0400'..'\u04FF'    // Cyrillic
            ) {
                return true
            }
        }
        return false
    }

    fun canRomanizeDocument(doc: LyricsDocument): Boolean {
        return doc.lines.any { canRomanize(it.text) }
    }

    fun romanize(text: String): String {
        if (!canRomanize(text)) return text

        val sb = StringBuilder(text.length * 2)
        var i = 0
        val len = text.length

        while (i < len) {
            val c = text[i]

            when {
                // Devanagari Block
                c in '\u0900'..'\u097F' -> {
                    i = romanizeDevanagariChar(text, i, sb)
                }
                // Gurmukhi Block
                c in '\u0A00'..'\u0A7F' -> {
                    i = romanizeGurmukhiChar(text, i, sb)
                }
                // Japanese Kana Block
                c in '\u3040'..'\u30FF' -> {
                    i = romanizeKanaChar(text, i, sb)
                }
                // Korean Hangul Syllable Block
                c in '\uAC00'..'\uD7AF' -> {
                    romanizeHangulChar(c, sb)
                    i++
                }
                // Cyrillic Block
                c in '\u0400'..'\u04FF' -> {
                    romanizeCyrillicChar(c, sb)
                    i++
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }

        return sb.toString()
    }

    fun romanizeDocument(doc: LyricsDocument): LyricsDocument {
        val updatedLines = doc.lines.map { line ->
            val rom = if (line.romanization != null) {
                line.romanization
            } else if (canRomanize(line.text)) {
                romanize(line.text)
            } else {
                null
            }

            val updatedWords = if (line.hasWordTimings && canRomanize(line.text)) {
                line.words.map { w ->
                    w.copy(text = romanize(w.text))
                }
            } else {
                line.words
            }

            line.copy(romanization = rom, words = updatedWords)
        }
        return doc.copy(lines = updatedLines)
    }

    // =========================================================================
    // 1. DEVANAGARI (Hindi, Sanskrit, Marathi, Nepali)
    // =========================================================================

    private val DEVANAGARI_INDEPENDENT_VOWELS = mapOf(
        '\u0904' to "e",   '\u0905' to "a",   '\u0906' to "aa",  '\u0907' to "i",
        '\u0908' to "ee",  '\u0909' to "u",   '\u090A' to "oo",  '\u090B' to "ri",
        '\u090E' to "e",   '\u090F' to "e",   '\u0910' to "ai",  '\u0911' to "o",
        '\u0912' to "o",   '\u0913' to "o",   '\u0914' to "au"
    )

    private val DEVANAGARI_MATRAS = mapOf(
        '\u093E' to "aa",  '\u093F' to "i",   '\u0940' to "ee",  '\u0941' to "u",
        '\u0942' to "oo",  '\u0943' to "ri",  '\u0946' to "e",   '\u0947' to "e",
        '\u0948' to "ai",  '\u094A' to "o",   '\u094B' to "o",   '\u094C' to "au",
        '\u0949' to "o",   '\u0945' to "e"
    )

    private val DEVANAGARI_CONSONANTS = mapOf(
        '\u0915' to "k",   '\u0916' to "kh",  '\u0917' to "g",   '\u0918' to "gh",  '\u0919' to "ng",
        '\u091A' to "ch",  '\u091B' to "chh", '\u091C' to "j",   '\u091D' to "jh",  '\u091E' to "ny",
        '\u091F' to "t",   '\u0920' to "th",  '\u0921' to "d",   '\u0922' to "dh",  '\u0923' to "n",
        '\u0924' to "t",   '\u0925' to "th",  '\u0926' to "d",   '\u0927' to "dh",  '\u0928' to "n",
        '\u092A' to "p",   '\u092B' to "ph",  '\u092C' to "b",   '\u092D' to "bh",  '\u092E' to "m",
        '\u092F' to "y",   '\u0930' to "r",   '\u0932' to "l",   '\u0933' to "l",   '\u0935' to "v",
        '\u0936' to "sh",  '\u0937' to "sh",  '\u0938' to "s",   '\u0939' to "h",
        '\u0958' to "q",   '\u0959' to "kh",  '\u095A' to "gh",  '\u095B' to "z",
        '\u095C' to "r",   '\u095D' to "rh",  '\u095E' to "f",   '\u095F' to "y"
    )

    private fun romanizeDevanagariChar(text: String, index: Int, sb: StringBuilder): Int {
        val c = text[index]

        // Independent vowel
        val vowel = DEVANAGARI_INDEPENDENT_VOWELS[c]
        if (vowel != null) {
            sb.append(vowel)
            return index + 1
        }

        // Anusvara, Candrabindu, Visarga
        when (c) {
            '\u0901', '\u0902' -> { // Candrabindu / Anusvara
                sb.append("n")
                return index + 1
            }
            '\u0903' -> { // Visarga
                sb.append("h")
                return index + 1
            }
            '\u093C' -> return index + 1 // Nukta handled or ignored
            '\u093D' -> return index + 1 // Avagraha
        }

        // Consonant
        val cons = DEVANAGARI_CONSONANTS[c]
        if (cons != null) {
            sb.append(cons)
            val nextIndex = index + 1
            if (nextIndex < text.length) {
                val nextChar = text[nextIndex]
                // Virama / Halant suppresses inherent 'a'
                if (nextChar == '\u094D') {
                    return nextIndex + 1
                }
                // Matra replaces inherent 'a'
                val matra = DEVANAGARI_MATRAS[nextChar]
                if (matra != null) {
                    sb.append(matra)
                    return nextIndex + 1
                }
                // Nukta following consonant (e.g. ज + ़ = z, ड + ़ = r, फ + ़ = f)
                if (nextChar == '\u093C') {
                    // Check if further char has matra or virama
                    val postNukta = nextIndex + 1
                    if (postNukta < text.length) {
                        if (text[postNukta] == '\u094D') return postNukta + 1
                        val postMatra = DEVANAGARI_MATRAS[text[postNukta]]
                        if (postMatra != null) {
                            sb.append(postMatra)
                            return postNukta + 1
                        }
                    }
                    if (postNukta < text.length && !text[postNukta].isWhitespace()) {
                        sb.append("a")
                    }
                    return postNukta
                }
                // Hindi schwa deletion: drop final 'a' at word boundary or punctuation
                if (nextChar.isWhitespace() || !nextChar.isLetter()) {
                    // Single consonant words like 'न' keep 'a'
                    val prevChar = if (index > 0) text[index - 1] else ' '
                    if (prevChar.isWhitespace()) {
                        sb.append("a")
                    }
                    return nextIndex
                }
            } else {
                // End of text: drop schwa unless single letter
                val prevChar = if (index > 0) text[index - 1] else ' '
                if (prevChar.isWhitespace()) {
                    sb.append("a")
                }
                return nextIndex
            }
            // Consonant in middle of word with no matra or halant has inherent 'a'
            sb.append("a")
            return nextIndex
        }

        // Standalone Matra (rare malformed)
        val matra = DEVANAGARI_MATRAS[c]
        if (matra != null) {
            sb.append(matra)
            return index + 1
        }

        return index + 1
    }

    // =========================================================================
    // 2. GURMUKHI (Punjabi)
    // =========================================================================

    private val GURMUKHI_CONSONANTS = mapOf(
        '\u0A15' to "k",  '\u0A16' to "kh", '\u0A17' to "g",  '\u0A18' to "gh", '\u0A19' to "ng",
        '\u0A1A' to "ch", '\u0A1B' to "chh",'\u0A1C' to "j",  '\u0A1D' to "jh", '\u0A1E' to "ny",
        '\u0A1F' to "t",  '\u0A20' to "th", '\u0A21' to "d",  '\u0A22' to "dh", '\u0A23' to "n",
        '\u0A24' to "t",  '\u0A25' to "th", '\u0A26' to "d",  '\u0A27' to "dh", '\u0A28' to "n",
        '\u0A2A' to "p",  '\u0A2B' to "f",  '\u0A2C' to "b",  '\u0A2D' to "bh", '\u0A2E' to "m",
        '\u0A2F' to "y",  '\u0A30' to "r",  '\u0A32' to "l",  '\u0A33' to "l",  '\u0A35' to "v",
        '\u0A36' to "sh", '\u0A38' to "s",  '\u0A39' to "h",  '\u0A59' to "kh", '\u0A5A' to "gh",
        '\u0A5B' to "z",  '\u0A5C' to "r",  '\u0A5E' to "f"
    )

    private val GURMUKHI_MATRAS = mapOf(
        '\u0A3E' to "aa", '\u0A3F' to "i",  '\u0A40' to "ee", '\u0A41' to "u",
        '\u0A42' to "oo", '\u0A47' to "e",  '\u0A48' to "ai", '\u0A4B' to "o",
        '\u0A4C' to "au"
    )

    private val GURMUKHI_VOWELS = mapOf(
        '\u0A05' to "a",  '\u0A06' to "aa", '\u0A07' to "i",  '\u0A08' to "ee",
        '\u0A09' to "u",  '\u0A0A' to "oo", '\u0A0F' to "e",  '\u0A10' to "ai",
        '\u0A13' to "o",  '\u0A14' to "au"
    )

    private fun romanizeGurmukhiChar(text: String, index: Int, sb: StringBuilder): Int {
        val c = text[index]

        val vowel = GURMUKHI_VOWELS[c]
        if (vowel != null) {
            sb.append(vowel)
            return index + 1
        }

        // Tippi / Bindi / Addak
        when (c) {
            '\u0A70', '\u0A02' -> { // Tippi / Bindi
                sb.append("n")
                return index + 1
            }
            '\u0A71' -> { // Addak (gemination)
                val next = if (index + 1 < text.length) text[index + 1] else null
                val cons = next?.let { GURMUKHI_CONSONANTS[it] }
                if (cons != null) sb.append(cons.first())
                return index + 1
            }
        }

        val cons = GURMUKHI_CONSONANTS[c]
        if (cons != null) {
            sb.append(cons)
            val nextIndex = index + 1
            if (nextIndex < text.length) {
                val nextChar = text[nextIndex]
                if (nextChar == '\u0A4D') return nextIndex + 1 // Virama
                val matra = GURMUKHI_MATRAS[nextChar]
                if (matra != null) {
                    sb.append(matra)
                    return nextIndex + 1
                }
                if (nextChar.isWhitespace() || !nextChar.isLetter()) {
                    return nextIndex
                }
            } else {
                return nextIndex
            }
            sb.append("a")
            return nextIndex
        }

        val matra = GURMUKHI_MATRAS[c]
        if (matra != null) {
            sb.append(matra)
            return index + 1
        }

        return index + 1
    }

    // =========================================================================
    // 3. JAPANESE KANA (Hiragana & Katakana Hepburn)
    // =========================================================================

    private val KANA_DIGRAPHS = mapOf(
        "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo",
        "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho",
        "ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho",
        "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo",
        "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo",
        "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo",
        "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo",
        "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
        "じゃ" to "ja",  "じゅ" to "ju",  "じょ" to "jo",
        "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo",
        "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo",
        // Katakana equivalents
        "キャ" to "kya", "キュ" to "kyu", "キョ" to "kyo",
        "シャ" to "sha", "シュ" to "shu", "ショ" to "sho",
        "チャ" to "cha", "チュ" to "chu", "チョ" to "cho",
        "ニャ" to "nya", "ニュ" to "nyu", "ニョ" to "nyo",
        "ヒャ" to "hya", "ヒュ" to "hyu", "ヒョ" to "hyo",
        "ミャ" to "mya", "ミュ" to "myu", "ミョ" to "myo",
        "リャ" to "rya", "リュ" to "ryu", "リョ" to "ryo",
        "ギャ" to "gya", "ギュ" to "gyu", "ギョ" to "gyo",
        "ジャ" to "ja",  "ジュ" to "ju",  "ジョ" to "jo",
        "ビャ" to "bya", "ビュ" to "byu", "ビョ" to "byo",
        "ピャ" to "pya", "ピュ" to "pyu", "ピョ" to "pyo"
    )

    private val KANA_SINGLE = mapOf(
        'あ' to "a",   'い' to "i",   'う' to "u",   'え' to "e",   'お' to "o",
        'か' to "ka",  'き' to "ki",  'く' to "ku",  'け' to "ke",  'こ' to "ko",
        'さ' to "sa",  'し' to "shi", 'す' to "su",  'せ' to "se",  'そ' to "so",
        'た' to "ta",  'ち' to "chi", 'つ' to "tsu", 'て' to "te",  'と' to "to",
        'な' to "na",  'に' to "ni",  'ぬ' to "nu",  'ね' to "ne",  'の' to "no",
        'は' to "ha",  'ひ' to "hi",  'ふ' to "fu",  'へ' to "he",  'ほ' to "ho",
        'ま' to "ma",  'み' to "mi",  'む' to "mu",  'め' to "me",  'も' to "mo",
        'や' to "ya",  'ゆ' to "yu",  'よ' to "yo",
        'ら' to "ra",  'り' to "ri",  'る' to "ru",  'れ' to "re",  'ろ' to "ro",
        'わ' to "wa",  'を' to "wo",  'ん' to "n",
        'が' to "ga",  'ぎ' to "gi",  'ぐ' to "gu",  'げ' to "ge",  'ご' to "go",
        'ざ' to "za",  'じ' to "ji",  'ず' to "zu",  'ぜ' to "ze",  'ぞ' to "zo",
        'だ' to "da",  'ぢ' to "ji",  'づ' to "zu",  'で' to "de",  'ど' to "do",
        'ば' to "ba",  'び' to "bi",  'ぶ' to "bu",  'べ' to "be",  'ぼ' to "bo",
        'ぱ' to "pa",  'ぴ' to "pi",  'ぷ' to "pu",  'ぺ' to "pe",  'ぽ' to "po",
        'ぁ' to "a",   'ぃ' to "i",   'ぅ' to "u",   'ぇ' to "e",   'ぉ' to "o",
        'ゃ' to "ya",  'ゅ' to "yu",  'ょ' to "yo",  'ゎ' to "wa",
        // Katakana
        'ア' to "a",   'イ' to "i",   'ウ' to "u",   'エ' to "e",   'オ' to "o",
        'カ' to "ka",  'キ' to "ki",  'ク' to "ku",  'ケ' to "ke",  'コ' to "ko",
        'サ' to "sa",  'シ' to "shi", 'ス' to "su",  'セ' to "se",  'ソ' to "so",
        'タ' to "ta",  'チ' to "chi", 'ツ' to "tsu", 'テ' to "te",  'ト' to "to",
        'ナ' to "na",  'ニ' to "ni",  'ヌ' to "nu",  'ネ' to "ne",  'ノ' to "no",
        'ハ' to "ha",  'ヒ' to "hi",  'フ' to "fu",  'ヘ' to "he",  'ホ' to "ho",
        'マ' to "ma",  'ミ' to "mi",  'ム' to "mu",  'メ' to "me",  'モ' to "mo",
        'ヤ' to "ya",  'ユ' to "yu",  'ヨ' to "yo",
        'ラ' to "ra",  'リ' to "ri",  'ル' to "ru",  'レ' to "re",  'ロ' to "ro",
        'ワ' to "wa",  'ヲ' to "wo",  'ン' to "n",
        'ガ' to "ga",  'ギ' to "gi",  'グ' to "gu",  'ゲ' to "ge",  'ゴ' to "go",
        'ザ' to "za",  'ジ' to "ji",  'ズ' to "zu",  'ゼ' to "ze",  'ゾ' to "zo",
        'ダ' to "da",  'ヂ' to "ji",  'ヅ' to "zu",  'デ' to "de",  'ド' to "do",
        'バ' to "ba",  'ビ' to "bi",  'ブ' to "bu",  'ベ' to "be",  'ボ' to "bo",
        'パ' to "pa",  'ピ' to "pi",  'プ' to "pu",  'ペ' to "pe",  'ポ' to "po",
        'ァ' to "a",   'ィ' to "i",   'ゥ' to "u",   'ェ' to "e",   'ォ' to "o",
        'ャ' to "ya",  'ュ' to "yu",  'ョ' to "yo",  'ヮ' to "wa",  'ー' to "-"
    )

    private fun romanizeKanaChar(text: String, index: Int, sb: StringBuilder): Int {
        // Check 2-char digraph first
        if (index + 1 < text.length) {
            val pair = text.substring(index, index + 2)
            val dig = KANA_DIGRAPHS[pair]
            if (dig != null) {
                sb.append(dig)
                return index + 2
            }
        }

        val c = text[index]

        // Sokuon (っ / ッ) duplicates the following consonant
        if (c == 'っ' || c == 'ッ') {
            if (index + 1 < text.length) {
                val next = text[index + 1]
                val nextRom = KANA_SINGLE[next] ?: KANA_DIGRAPHS[text.substring(index + 1, minOf(index + 3, text.length))]
                if (nextRom != null && nextRom.isNotEmpty() && nextRom[0].isLetter()) {
                    sb.append(nextRom[0])
                    return index + 1
                }
            }
            return index + 1
        }

        val single = KANA_SINGLE[c]
        if (single != null) {
            sb.append(single)
            return index + 1
        }

        sb.append(c)
        return index + 1
    }

    // =========================================================================
    // 4. KOREAN HANGUL (Revised Romanization of Korean)
    // =========================================================================

    private val HANGUL_INITIALS = arrayOf(
        "g", "kk", "n", "d", "tt", "r", "m", "b", "pp",
        "s", "ss", "", "j", "jj", "ch", "k", "t", "p", "h"
    )

    private val HANGUL_MEDIALS = arrayOf(
        "a", "ae", "ya", "yae", "eo", "e", "yeo", "ye",
        "o", "wa", "wae", "oe", "yo", "u", "wo", "we",
        "wi", "yu", "eu", "ui", "i"
    )

    private val HANGUL_FINALS = arrayOf(
        "", "k", "k", "ks", "n", "nj", "nh", "t", "l",
        "lg", "lm", "lb", "ls", "lt", "lp", "lh", "m",
        "p", "ps", "t", "ss", "ng", "t", "t", "k",
        "t", "p", "h"
    )

    private fun romanizeHangulChar(c: Char, sb: StringBuilder) {
        val codepoint = c.code
        if (codepoint in 0xAC00..0xD7A3) {
            val syllableIndex = codepoint - 0xAC00
            val initial = syllableIndex / 588
            val medial = (syllableIndex % 588) / 28
            val final = syllableIndex % 28

            sb.append(HANGUL_INITIALS[initial])
            sb.append(HANGUL_MEDIALS[medial])
            sb.append(HANGUL_FINALS[final])
        } else {
            sb.append(c)
        }
    }

    // =========================================================================
    // 5. CYRILLIC (Russian, Ukrainian, Belarusian BGN/PCGN)
    // =========================================================================

    private val CYRILLIC_MAP = mapOf(
        'а' to "a",  'б' to "b",  'в' to "v",  'г' to "g",  'д' to "d",  'е' to "e",
        'ё' to "yo", 'ж' to "zh", 'з' to "z",  'и' to "i",  'й' to "y",  'к' to "k",
        'л' to "l",  'м' to "m",  'н' to "n",  'о' to "o",  'п' to "p",  'р' to "r",
        'с' to "s",  'т' to "t",  'у' to "u",  'ф' to "f",  'х' to "kh", 'ц' to "ts",
        'ч' to "ch", 'ш' to "sh", 'щ' to "shch",'ъ' to "",  'ы' to "y",  'ь' to "",
        'э' to "e",  'ю' to "yu", 'я' to "ya",
        'А' to "A",  'Б' to "B",  'В' to "V",  'Г' to "G",  'Д' to "D",  'Е' to "E",
        'Ё' to "Yo", 'Ж' to "Zh", 'З' to "Z",  'И' to "I",  'Й' to "Y",  'К' to "K",
        'Л' to "L",  'М' to "M",  'Н' to "N",  'О' to "O",  'П' to "P",  'Р' to "R",
        'С' to "S",  'Т' to "T",  'У' to "U",  'Ф' to "F",  'Х' to "Kh", 'Ц' to "Ts",
        'Ч' to "Ch", 'Ш' to "Sh", 'Щ' to "Shch",'Ъ' to "",  'Ы' to "Y",  'Ь' to "",
        'Э' to "E",  'Ю' to "Yu", 'Я' to "Ya"
    )

    private fun romanizeCyrillicChar(c: Char, sb: StringBuilder) {
        val mapped = CYRILLIC_MAP[c]
        if (mapped != null) {
            sb.append(mapped)
        } else {
            sb.append(c)
        }
    }
}
