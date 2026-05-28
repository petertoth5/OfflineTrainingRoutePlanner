package javax.lang.model

import java.util.Arrays
import java.util.HashSet

/**
 * Android compatibility stub for GraphHopper 8.0
 */
enum class SourceVersion {
    RELEASE_0, RELEASE_1, RELEASE_2, RELEASE_3, RELEASE_4, RELEASE_5, RELEASE_6, RELEASE_7, RELEASE_8;

    companion object {
        private val keywords: Set<String> = HashSet(
            Arrays.asList(
                "abstract", "continue", "for", "new", "switch", "assert", "default", "if", "package", "synchronized",
                "boolean", "do", "goto", "private", "this", "break", "double", "implements", "protected", "throw",
                "byte", "else", "import", "public", "throws", "case", "enum", "instanceof", "return", "transient",
                "catch", "extends", "int", "short", "try", "char", "final", "interface", "static", "void",
                "class", "finally", "long", "strictfp", "volatile", "const", "float", "native", "super", "while",
                "true", "false", "null"
            )
        )

        @JvmStatic
        fun isKeyword(s: CharSequence): Boolean {
            return keywords.contains(s.toString())
        }

        @JvmStatic
        fun isIdentifier(s: CharSequence): Boolean {
            if (s.isEmpty() || !Character.isJavaIdentifierStart(s[0])) return false
            for (i in 1 until s.length) {
                if (!Character.isJavaIdentifierPart(s[i])) return false
            }
            return true
        }
    }
}
