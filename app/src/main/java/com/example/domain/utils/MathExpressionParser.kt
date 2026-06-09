package com.example.domain.utils

import java.math.BigDecimal
import java.math.RoundingMode

object MathExpressionParser {
    /**
     * Parses simple mathematical structures (+, -, *, /) and computes the resulting BigDecimal.
     * If the input is empty or invalid, returns null.
     */
    fun parseAndCompute(expression: String): BigDecimal? {
        val sanitized = expression.replace(" ", "").replace(",", "")
        if (sanitized.isEmpty()) return null
        
        return try {
            evaluate(sanitized)
        } catch (e: Exception) {
            null
        }
    }

    private fun evaluate(expr: String): BigDecimal {
        if (expr.isEmpty()) return BigDecimal.ZERO
        
        // Find addition or subtraction from right to left
        var index = -1
        for (i in expr.length - 1 downTo 0) {
            val c = expr[i]
            if (c == '+' || c == '-') {
                index = i
                break
            }
        }

        if (index != -1) {
            val leftStr = expr.substring(0, index)
            val rightStr = expr.substring(index + 1)
            val left = if (leftStr.isEmpty()) BigDecimal.ZERO else evaluate(leftStr)
            val right = if (rightStr.isEmpty()) BigDecimal.ZERO else evaluate(rightStr)
            return if (expr[index] == '+') left.add(right) else left.subtract(right)
        }

        // Find multiplication or division from right to left
        for (i in expr.length - 1 downTo 0) {
            val c = expr[i]
            if (c == '*' || c == '/') {
                index = i
                break
            }
        }

        if (index != -1) {
            val leftStr = expr.substring(0, index)
            val rightStr = expr.substring(index + 1)
            val left = if (leftStr.isEmpty()) BigDecimal.ZERO else evaluate(leftStr)
            val right = if (rightStr.isEmpty()) BigDecimal.ZERO else evaluate(rightStr)
            return if (expr[index] == '*') {
                left.multiply(right)
            } else {
                if (right.compareTo(BigDecimal.ZERO) == 0) BigDecimal.ZERO else left.divide(right, 4, RoundingMode.HALF_UP)
            }
        }

        // Base numeric string parsing
        return BigDecimal(expr)
    }
}
