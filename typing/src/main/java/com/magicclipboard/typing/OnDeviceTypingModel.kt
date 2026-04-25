package com.magicclipboard.typing

import com.magicclipboard.typing.model.GestureTrace
import com.magicclipboard.typing.model.LanguageMode
import com.magicclipboard.typing.model.SuggestionCandidate
import kotlin.math.abs
import kotlin.math.max

class OnDeviceTypingModel : SuggestionEngine, GestureDecoder {
    private val learnedWords = mutableMapOf<LanguageMode, MutableMap<String, Int>>()

    override fun suggest(
        contextBeforeCursor: String,
        composingText: String,
        languageMode: LanguageMode,
        limit: Int,
    ): List<SuggestionCandidate> {
        val normalizedToken = normalize(composingText)
        val previousWord = normalize(contextBeforeCursor.trim().split(Regex("\\s+")).lastOrNull().orEmpty())

        if (normalizedToken.isBlank()) {
            return BuiltInLexicons.nextWordLexicon[languageMode]
                ?.get(previousWord)
                .orEmpty()
                .take(limit)
                .mapIndexed { index, word ->
                    SuggestionCandidate(
                        text = applyCase(word, composingText),
                        score = 1f - (index * 0.1f),
                    )
                }
        }

        val candidates = lexicon(languageMode).entries
            .asSequence()
            .filter { (word, _) -> word.startsWith(normalizedToken) }
            .sortedByDescending { (_, weight) -> weight }
            .mapIndexed { index, (word, weight) ->
                SuggestionCandidate(
                    text = applyCase(word, composingText),
                    score = weight.toFloat() - index,
                )
            }
            .take(limit)
            .toMutableList()

        autocorrect(composingText, languageMode)
            ?.takeIf { corrected -> corrected != normalize(composingText) && candidates.none { it.text.equals(corrected, ignoreCase = true) } }
            ?.let { corrected ->
                candidates.add(
                    0,
                    SuggestionCandidate(
                        text = applyCase(corrected, composingText),
                        score = Float.MAX_VALUE,
                        isAutocorrect = true,
                    ),
                )
            }

        return candidates.take(limit)
    }

    override fun autocorrect(
        token: String,
        languageMode: LanguageMode,
    ): String? {
        val normalizedToken = normalize(token)
        if (normalizedToken.length < 3 || lexicon(languageMode).containsKey(normalizedToken)) {
            return null
        }

        return lexicon(languageMode)
            .keys
            .asSequence()
            .filter { abs(it.length - normalizedToken.length) <= 2 }
            .map { candidate ->
                candidate to levenshtein(candidate, normalizedToken)
            }
            .filter { (_, distance) -> distance <= 2 }
            .sortedWith(compareBy<Pair<String, Int>> { it.second }.thenByDescending { lexicon(languageMode)[it.first] ?: 0 })
            .map { it.first }
            .firstOrNull()
    }

    override fun learn(
        token: String,
        languageMode: LanguageMode,
    ) {
        val normalized = normalize(token)
        if (normalized.length < 2) return

        val bucket = learnedWords.getOrPut(languageMode) { mutableMapOf() }
        bucket[normalized] = (bucket[normalized] ?: 0) + 300
    }

    override fun seedLearnedWords(
        languageMode: LanguageMode,
        words: Set<String>,
    ) {
        val bucket = learnedWords.getOrPut(languageMode) { mutableMapOf() }
        words.forEach { word ->
            val normalized = normalize(word)
            if (normalized.isNotBlank()) {
                bucket[normalized] = max(bucket[normalized] ?: 0, 280)
            }
        }
    }

    override fun decode(
        trace: GestureTrace,
        languageMode: LanguageMode,
        limit: Int,
    ): List<SuggestionCandidate> {
        val signature = trace.letters
            .map { it.lowercaseChar() }
            .fold(mutableListOf<Char>()) { acc, letter ->
                if (acc.lastOrNull() != letter) acc += letter
                acc
            }
            .joinToString(separator = "")

        if (signature.length < 3) return emptyList()

        return lexicon(languageMode)
            .keys
            .asSequence()
            .filter { candidate ->
                candidate.firstOrNull() == signature.firstOrNull() &&
                    candidate.lastOrNull() == signature.lastOrNull()
            }
            .map { candidate -> candidate to gestureScore(candidate, signature, languageMode) }
            .filter { (_, score) -> score > 0f }
            .sortedByDescending { (_, score) -> score }
            .take(limit)
            .map { (candidate, score) ->
                SuggestionCandidate(text = candidate, score = score)
            }
            .toList()
    }

    private fun lexicon(languageMode: LanguageMode): Map<String, Int> {
        return buildMap {
            putAll(BuiltInLexicons.frequencyLexicon[languageMode].orEmpty())
            putAll(learnedWords[languageMode].orEmpty())
        }
    }

    private fun gestureScore(
        candidate: String,
        signature: String,
        languageMode: LanguageMode,
    ): Float {
        if (candidate.firstOrNull() != signature.firstOrNull() || candidate.lastOrNull() != signature.lastOrNull()) {
            return 0f
        }

        val sharedSequenceLength = longestCommonSubsequenceLength(candidate, signature)
        if (sharedSequenceLength < minOf(3, candidate.length)) return 0f

        val frequency = (lexicon(languageMode)[candidate] ?: 0) / 100f
        val coverageBoost = (sharedSequenceLength.toFloat() / candidate.length) * 4f
        val noisePenalty = (signature.length - sharedSequenceLength).coerceAtLeast(0) * 0.45f
        val omissionPenalty = (candidate.length - sharedSequenceLength).coerceAtLeast(0) * 0.35f
        return (8f + frequency + coverageBoost) - noisePenalty - omissionPenalty
    }

    private fun longestCommonSubsequenceLength(
        left: String,
        right: String,
    ): Int {
        if (left.isEmpty() || right.isEmpty()) return 0

        val previous = IntArray(right.length + 1)
        val current = IntArray(right.length + 1)

        left.forEach { leftChar ->
            right.forEachIndexed { index, rightChar ->
                current[index + 1] = if (leftChar == rightChar) {
                    previous[index] + 1
                } else {
                    max(previous[index + 1], current[index])
                }
            }
            current.copyInto(previous)
            current.fill(0)
        }

        return previous[right.length]
    }

    private fun normalize(token: String): String {
        return token
            .trim()
            .lowercase()
            .replace("[^\\p{L}]".toRegex(), "")
    }

    private fun applyCase(
        value: String,
        original: String,
    ): String {
        return when {
            original.all(Char::isUpperCase) && original.isNotBlank() -> value.uppercase()
            original.firstOrNull()?.isUpperCase() == true -> value.replaceFirstChar { it.titlecase() }
            else -> value
        }
    }

    private fun levenshtein(
        left: String,
        right: String,
    ): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length

        val previous = IntArray(right.length + 1) { it }
        val current = IntArray(right.length + 1)

        left.forEachIndexed { i, l ->
            current[0] = i + 1
            right.forEachIndexed { j, r ->
                val substitutionCost = if (l == r) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + substitutionCost,
                )
            }
            current.copyInto(previous)
        }

        return previous[right.length]
    }
}
