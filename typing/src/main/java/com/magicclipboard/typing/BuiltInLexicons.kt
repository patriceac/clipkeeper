package com.magicclipboard.typing

import com.magicclipboard.typing.model.LanguageMode

internal object BuiltInLexicons {
    val frequencyLexicon: Map<LanguageMode, Map<String, Int>> = mapOf(
        LanguageMode.ENGLISH to listOf(
            "the", "be", "to", "of", "and", "a", "in", "that", "have", "i",
            "it", "for", "not", "on", "with", "he", "as", "you", "do", "at",
            "this", "but", "his", "by", "from", "they", "we", "say", "her", "she",
            "or", "an", "will", "my", "one", "all", "would", "there", "their", "what",
            "about", "which", "when", "make", "can", "like", "time", "just", "know", "take",
            "people", "into", "year", "your", "good", "some", "could", "them", "see", "other",
            "than", "then", "now", "look", "only", "come", "its", "over", "think", "also",
            "back", "after", "use", "two", "how", "our", "work", "first", "well", "way",
            "even", "new", "want", "because", "any", "these", "give", "day", "most", "us",
            "hello", "clipboard", "copy", "paste", "keyboard", "history", "image", "phone", "message", "bonjour",
        ).withIndex().associate { (index, word) -> word to (200 - index) },
        LanguageMode.FRENCH to listOf(
            "le", "de", "un", "etre", "et", "a", "il", "avoir", "ne", "je",
            "son", "que", "se", "qui", "ce", "dans", "en", "du", "elle", "au",
            "de", "ce", "le", "pour", "pas", "vous", "par", "sur", "faire", "plus",
            "dire", "me", "on", "mon", "lui", "nous", "comme", "mais", "pouvoir", "avec",
            "tout", "y", "aller", "voir", "bien", "ou", "sans", "tu", "leur", "homme",
            "si", "deux", "mari", "apres", "temps", "main", "jour", "mettre", "bonjour", "merci",
            "bonjour", "clavier", "presse", "papiers", "historique", "image", "copier", "coller", "telephone", "message",
            "bonjour", "comment", "salut", "oui", "non", "etre", "avons", "fais", "peux", "voudrais",
        ).withIndex().associate { (index, word) -> word to (200 - index) },
    )

    val nextWordLexicon: Map<LanguageMode, Map<String, List<String>>> = mapOf(
        LanguageMode.ENGLISH to mapOf(
            "thank" to listOf("you", "them", "goodness"),
            "see" to listOf("you", "that", "the"),
            "good" to listOf("morning", "luck", "job"),
            "copy" to listOf("that", "this", "image"),
            "paste" to listOf("it", "here", "there"),
        ),
        LanguageMode.FRENCH to mapOf(
            "bonjour" to listOf("merci", "je", "vous"),
            "merci" to listOf("beaucoup", "pour", "bien"),
            "je" to listOf("suis", "voudrais", "peux"),
            "copier" to listOf("cela", "texte", "image"),
            "coller" to listOf("ici", "maintenant", "cela"),
        ),
    )
}

