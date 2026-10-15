package com.obddiag.app

/**
 * Petit évaluateur d'expressions arithmétiques pour les PIDs personnalisés,
 * dans le style des équations de Torque : A, B, C... = octets de données
 * (A = premier octet après l'écho de la requête), + - * / %, parenthèses,
 * décalages << >>, ET/OU binaires & |, et {A:3} = bit 3 de l'octet A.
 */
object Formula {

    fun eval(expr: String, bytes: List<Int>): Double? =
        try { Parser(expr, bytes).parse() } catch (_: Exception) { null }

    private class Parser(val s: String, val bytes: List<Int>) {
        var p = 0

        fun parse(): Double {
            val v = orExpr()
            skip()
            if (p != s.length) error("caractère inattendu")
            return v
        }

        private fun skip() { while (p < s.length && s[p].isWhitespace()) p++ }
        private fun peek(): Char { skip(); return if (p < s.length) s[p] else '\u0000' }
        private fun peek2(): String { skip(); return if (p + 1 < s.length) s.substring(p, p + 2) else "" }

        private fun orExpr(): Double {
            var v = andExpr()
            while (peek() == '|') { p++; v = (v.toLong() or andExpr().toLong()).toDouble() }
            return v
        }

        private fun andExpr(): Double {
            var v = shiftExpr()
            while (peek() == '&') { p++; v = (v.toLong() and shiftExpr().toLong()).toDouble() }
            return v
        }

        private fun shiftExpr(): Double {
            var v = addExpr()
            while (true) {
                when (peek2()) {
                    "<<" -> { p += 2; v = (v.toLong() shl addExpr().toInt()).toDouble() }
                    ">>" -> { p += 2; v = (v.toLong() shr addExpr().toInt()).toDouble() }
                    else -> return v
                }
            }
        }

        private fun addExpr(): Double {
            var v = mulExpr()
            while (true) {
                when (peek()) {
                    '+' -> { p++; v += mulExpr() }
                    '-' -> { p++; v -= mulExpr() }
                    else -> return v
                }
            }
        }

        private fun mulExpr(): Double {
            var v = unary()
            while (true) {
                when (peek()) {
                    '*' -> { p++; v *= unary() }
                    '/' -> { p++; v /= unary() }
                    '%' -> { p++; v %= unary() }
                    else -> return v
                }
            }
        }

        private fun unary(): Double = when (peek()) {
            '-' -> { p++; -unary() }
            '+' -> { p++; unary() }
            else -> primary()
        }

        private fun byteAt(letter: Char): Int {
            val i = letter - 'A'
            if (i !in bytes.indices) error("octet $letter absent")
            return bytes[i]
        }

        private fun primary(): Double {
            val c = peek()
            when {
                c == '(' -> {
                    p++
                    val v = orExpr()
                    if (peek() != ')') error("parenthèse manquante")
                    p++
                    return v
                }
                c == '{' -> {
                    p++
                    skip()
                    val letter = s[p++]
                    if (s[p++] != ':') error("syntaxe {A:n}")
                    val start = p
                    while (p < s.length && s[p].isDigit()) p++
                    val bit = s.substring(start, p).toInt()
                    if (s[p++] != '}') error("accolade manquante")
                    return ((byteAt(letter) shr bit) and 1).toDouble()
                }
                c.isDigit() || c == '.' -> {
                    val start = p
                    if (s.startsWith("0x", p) || s.startsWith("0X", p)) {
                        p += 2
                        val h = p
                        while (p < s.length && (s[p].isDigit() || s[p].lowercaseChar() in 'a'..'f')) p++
                        return s.substring(h, p).toLong(16).toDouble()
                    }
                    while (p < s.length && (s[p].isDigit() || s[p] == '.')) p++
                    return s.substring(start, p).toDouble()
                }
                c in 'A'..'Z' -> {
                    p++
                    return byteAt(c).toDouble()
                }
                else -> error("jeton inattendu")
            }
        }
    }
}
