package com.wordpilot.assistant.engine.offline.dictionary

import android.content.Context
import com.wordpilot.assistant.engine.offline.model.POSTag
import com.wordpilot.assistant.engine.offline.morphology.MorphologyAnalyzer
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

data class DictEntry(
    val word: String,
    val frequency: Int,
    val tags: Set<POSTag>,
    val lemma: String? = null
)

class EnglishDictionary(
    private val context: Context? = null,
    private val morphology: MorphologyAnalyzer = MorphologyAnalyzer()
) {

    private val entries = ConcurrentHashMap<String, DictEntry>()
    private val userCustomWords = ConcurrentHashMap.newKeySet<String>()

    // Trie for fast Damerau-Levenshtein spelling candidate search
    class TrieNode {
        val children = ConcurrentHashMap<Char, TrieNode>()
        var isWord = false
        var frequency = 0
        var word: String? = null
    }

    val root = TrieNode()

    init {
        loadCoreLexicon()
        if (context != null) {
            loadFromAssets(context)
        } else {
            loadFromLocalFiles()
        }
    }

    private fun loadFromLocalFiles() {
        val candidates = listOf(
            java.io.File("src/main/assets/words.txt"),
            java.io.File("app/src/main/assets/words.txt"),
            java.io.File("../app/src/main/assets/words.txt")
        )
        val file = candidates.firstOrNull { it.exists() } ?: return
        try {
            val reader = BufferedReader(InputStreamReader(file.inputStream()))
            var line: String?
            var rank = 30000
            while (reader.readLine().also { line = it } != null) {
                val raw = line?.trim()?.lowercase() ?: continue
                if (raw.isNotEmpty() && raw.all { it.isLetter() || it == '\'' || it == '-' }) {
                    if (!entries.containsKey(raw)) {
                        val tags = inferTagsForWord(raw)
                        addEntry(raw, rank, tags)
                        if (rank > 1) rank--
                    }
                }
            }
            reader.close()
        } catch (_: Exception) {}
    }

    private fun loadCoreLexicon() {
        // Pronouns - Subject
        val subjPronouns = listOf("i", "you", "he", "she", "it", "we", "they")
        subjPronouns.forEach { addEntry(it, 100000, setOf(POSTag.PRP)) }

        // Pronouns - Object
        val objPronouns = listOf("me", "him", "her", "us", "them")
        objPronouns.forEach { addEntry(it, 95000, setOf(POSTag.PRP_DO)) }

        // Pronouns - Possessive
        val possPronouns = listOf("my", "your", "his", "her", "its", "our", "their", "whose")
        possPronouns.forEach { addEntry(it, 95000, setOf(POSTag.PRP_POS)) }

        // Reflexive Pronouns
        val reflexives = listOf("myself", "yourself", "himself", "herself", "itself", "ourselves", "yourselves", "themselves")
        reflexives.forEach { addEntry(it, 80000, setOf(POSTag.PRP_REF)) }

        // Wh-words
        addEntry("who", 90000, setOf(POSTag.WP))
        addEntry("whom", 70000, setOf(POSTag.WP, POSTag.PRP_DO))
        addEntry("what", 92000, setOf(POSTag.WP, POSTag.DT))
        addEntry("which", 91000, setOf(POSTag.WP, POSTag.DT))
        addEntry("where", 85000, setOf(POSTag.WRB))
        addEntry("when", 88000, setOf(POSTag.WRB, POSTag.IN))
        addEntry("why", 84000, setOf(POSTag.WRB))
        addEntry("how", 86000, setOf(POSTag.WRB))

        // Determiners & Articles
        addEntry("the", 150000, setOf(POSTag.DT))
        addEntry("a", 140000, setOf(POSTag.DT))
        addEntry("an", 130000, setOf(POSTag.DT))
        addEntry("this", 95000, setOf(POSTag.DT))
        addEntry("that", 95000, setOf(POSTag.DT, POSTag.IN))
        addEntry("these", 90000, setOf(POSTag.DT))
        addEntry("those", 90000, setOf(POSTag.DT))
        addEntry("each", 80000, setOf(POSTag.DT))
        addEntry("every", 82000, setOf(POSTag.DT))
        addEntry("some", 85000, setOf(POSTag.DT))
        addEntry("any", 83000, setOf(POSTag.DT))
        addEntry("all", 88000, setOf(POSTag.DT))
        addEntry("both", 75000, setOf(POSTag.DT))
        addEntry("neither", 65000, setOf(POSTag.DT, POSTag.CC))
        addEntry("either", 70000, setOf(POSTag.DT, POSTag.CC))
        addEntry("many", 85000, setOf(POSTag.DT, POSTag.JJ))
        addEntry("much", 82000, setOf(POSTag.DT, POSTag.RB))
        addEntry("few", 78000, setOf(POSTag.DT, POSTag.JJ))
        addEntry("fewer", 68000, setOf(POSTag.DT, POSTag.JJR))
        addEntry("little", 80000, setOf(POSTag.DT, POSTag.JJ))
        addEntry("less", 75000, setOf(POSTag.DT, POSTag.RBR))
        addEntry("least", 70000, setOf(POSTag.JJS, POSTag.RBS))
        addEntry("no", 90000, setOf(POSTag.DT, POSTag.RB))

        // Prepositions
        val prepositions = listOf(
            "in", "on", "at", "by", "with", "from", "to", "for", "of", "about",
            "over", "under", "through", "into", "onto", "between", "among", "against",
            "during", "before", "after", "above", "below", "behind", "beyond", "without",
            "within", "across", "along", "behind", "near", "toward", "towards", "upon", "off"
        )
        prepositions.forEach { addEntry(it, 90000, setOf(POSTag.IN)) }

        // Conjunctions
        val coordinating = listOf("and", "but", "or", "nor", "so", "yet", "for")
        coordinating.forEach { addEntry(it, 95000, setOf(POSTag.CC)) }

        val subordinating = listOf("because", "although", "though", "while", "if", "unless", "since", "as", "whereas", "whether")
        subordinating.forEach { addEntry(it, 85000, setOf(POSTag.IN)) }

        // Modal verbs
        val modals = listOf("can", "could", "will", "would", "shall", "should", "may", "might", "must")
        modals.forEach { addEntry(it, 92000, setOf(POSTag.MD)) }

        // Auxiliary verbs
        addEntry("be", 110000, setOf(POSTag.VB))
        addEntry("is", 120000, setOf(POSTag.VBZ))
        addEntry("am", 95000, setOf(POSTag.VBP))
        addEntry("are", 115000, setOf(POSTag.VBP))
        addEntry("was", 110000, setOf(POSTag.VBD))
        addEntry("were", 105000, setOf(POSTag.VBD))
        addEntry("been", 100000, setOf(POSTag.VBN))
        addEntry("being", 90000, setOf(POSTag.VBG))

        addEntry("have", 110000, setOf(POSTag.VB, POSTag.VBP))
        addEntry("has", 105000, setOf(POSTag.VBZ))
        addEntry("had", 102000, setOf(POSTag.VBD, POSTag.VBN))
        addEntry("having", 85000, setOf(POSTag.VBG))

        addEntry("do", 100000, setOf(POSTag.VB, POSTag.VBP))
        addEntry("does", 95000, setOf(POSTag.VBZ))
        addEntry("did", 98000, setOf(POSTag.VBD))
        addEntry("done", 90000, setOf(POSTag.VBN))
        addEntry("doing", 85000, setOf(POSTag.VBG))

        // Common contractions
        val contractions = listOf(
            "don't", "doesn't", "didn't", "can't", "cannot", "won't", "wouldn't", "shouldn't", "couldn't",
            "haven't", "hasn't", "hadn't", "isn't", "aren't", "wasn't", "weren't",
            "it's", "that's", "there's", "here's", "what's", "who's", "let's",
            "i'm", "you're", "we're", "they're", "he's", "she's",
            "i've", "you've", "we've", "they've",
            "i'll", "you'll", "he'll", "she'll", "we'll", "they'll",
            "i'd", "you'd", "he'd", "she'd", "we'd", "they'd"
        )
        contractions.forEach { addEntry(it, 90000, setOf(POSTag.UNKNOWN)) }

        // Common Core Vocabulary (Nouns, Verbs, Adjectives, Adverbs)
        val coreWords = listOf(
            "time", "person", "year", "way", "day", "thing", "man", "world", "life", "hand",
            "part", "child", "eye", "woman", "place", "work", "week", "case", "point", "government",
            "company", "number", "group", "problem", "fact", "idea", "water", "room", "mother",
            "area", "money", "story", "fact", "month", "lot", "right", "study", "book", "word",
            "business", "issue", "side", "kind", "head", "house", "service", "friend", "father",
            "power", "hour", "game", "line", "end", "member", "law", "car", "city", "community",
            "name", "president", "team", "minute", "idea", "kid", "body", "information", "back",
            "parent", "face", "others", "level", "office", "door", "health", "person", "art", "war",
            "history", "party", "result", "change", "morning", "reason", "research", "girl", "guy",
            "moment", "air", "teacher", "force", "education", "dog", "dogs", "cat", "cats",
            "say", "go", "get", "make", "know", "think", "take", "see", "come", "want", "look",
            "use", "find", "give", "tell", "work", "call", "try", "ask", "need", "feel", "become",
            "leave", "put", "mean", "keep", "let", "begin", "seem", "help", "talk", "turn", "start",
            "show", "hear", "play", "run", "move", "like", "live", "believe", "hold", "bring",
            "happen", "must", "write", "provide", "sit", "stand", "lose", "pay", "meet", "include",
            "continue", "set", "learn", "change", "lead", "understand", "watch", "follow", "stop",
            "create", "speak", "read", "allow", "add", "spend", "grow", "open", "walk", "win",
            "offer", "remember", "love", "consider", "appear", "buy", "wait", "serve", "die", "send",
            "expect", "build", "stay", "fall", "cut", "reach", "kill", "remain", "suggest", "raise",
            "pass", "sell", "require", "report", "decide", "pull", "bark", "barks", "barking", "jump",
            "jumps", "jumped", "jumping",
            "good", "new", "first", "last", "long", "great", "little", "own", "other", "old", "right",
            "big", "high", "different", "small", "large", "next", "early", "young", "important",
            "few", "public", "bad", "same", "able", "quick", "lazy", "brown", "fast", "slow", "happy",
            "sad", "easy", "difficult", "clear", "simple", "professional", "polite", "casual",
            "up", "so", "out", "just", "now", "how", "then", "more", "also", "here", "well", "only",
            "very", "even", "back", "there", "down", "still", "in", "as", "too", "when", "never",
            "really", "most", "on", "why", "about", "over", "again", "where", "right", "off", "always",
            "today", "tomorrow", "yesterday", "tonight", "often", "sometimes", "usually", "quickly", "slowly",
            "assistant", "hoverpen", "wordpilot", "hello", "writing", "think", "market", "phone", "motorola", "apple", "apples", "orange"
        )
        coreWords.forEach { word ->
            val tags = inferTagsForWord(word)
            addEntry(word, 50000, tags)
        }
    }

    private fun loadFromAssets(context: Context) {
        try {
            val assetManager = context.assets
            val inputStream = assetManager.open("words.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))
            var line: String?
            var rank = 30000
            while (reader.readLine().also { line = it } != null) {
                val raw = line?.trim()?.lowercase() ?: continue
                if (raw.isNotEmpty() && raw.all { it.isLetter() || it == '\'' || it == '-' }) {
                    if (!entries.containsKey(raw)) {
                        val tags = inferTagsForWord(raw)
                        addEntry(raw, rank, tags)
                        if (rank > 1) rank--
                    }
                }
            }
            reader.close()
        } catch (_: Exception) {}
    }

    private fun inferTagsForWord(word: String): Set<POSTag> {
        val tags = mutableSetOf<POSTag>()
        val lower = word.lowercase()

        // Adverbs ending in -ly
        if (lower.endsWith("ly") && lower.length > 3) {
            tags.add(POSTag.RB)
        }

        // Verbs and forms
        val verbAnalyses = morphology.analyzeVerbForm(lower)
        if (verbAnalyses.isNotEmpty()) {
            for (va in verbAnalyses) {
                when (va.form) {
                    com.wordpilot.assistant.engine.offline.morphology.VerbForm.BASE -> tags.add(POSTag.VB)
                    com.wordpilot.assistant.engine.offline.morphology.VerbForm.THIRD_PERSON_SINGULAR -> tags.add(POSTag.VBZ)
                    com.wordpilot.assistant.engine.offline.morphology.VerbForm.PAST -> tags.add(POSTag.VBD)
                    com.wordpilot.assistant.engine.offline.morphology.VerbForm.PAST_PARTICIPLE -> tags.add(POSTag.VBN)
                    com.wordpilot.assistant.engine.offline.morphology.VerbForm.PRESENT_PARTICIPLE -> {
                        tags.add(POSTag.VBG)
                        tags.add(POSTag.NN) // gerund
                    }
                }
            }
        }

        // Nouns & Plurals
        if (morphology.isPluralNoun(lower)) {
            tags.add(POSTag.NNS)
        } else {
            tags.add(POSTag.NN)
        }

        // Adjectives
        if (lower.endsWith("ful") || lower.endsWith("ous") || lower.endsWith("ive") || lower.endsWith("able") || lower.endsWith("ible") || lower.endsWith("al") || lower.endsWith("ic")) {
            tags.add(POSTag.JJ)
        }

        if (tags.isEmpty()) {
            tags.add(POSTag.NN)
        }

        return tags
    }

    fun addEntry(word: String, frequency: Int, tags: Set<POSTag>, lemma: String? = null) {
        val clean = word.lowercase()
        val entry = DictEntry(clean, frequency, tags, lemma)
        entries[clean] = entry

        // Insert into trie
        var current = root
        for (ch in clean) {
            current = current.children.computeIfAbsent(ch) { TrieNode() }
        }
        current.isWord = true
        current.word = clean
        current.frequency = maxOf(current.frequency, frequency)
    }

    fun isKnownWord(word: String): Boolean {
        val clean = word.lowercase().trim()
        if (clean.isEmpty()) return true
        if (clean in userCustomWords) return true
        if (entries.containsKey(clean)) return true

        // Check if possessive e.g. "dog's"
        if (clean.endsWith("'s") && clean.length > 2) {
            val base = clean.dropLast(2)
            if (isKnownWord(base)) return true
        }

        // Check regular inflected forms
        val singular = morphology.getSingularNoun(clean)
        if (singular != clean && entries.containsKey(singular)) return true

        val baseVerb = morphology.getBaseVerb(clean)
        if (baseVerb != clean && entries.containsKey(baseVerb)) return true

        return false
    }

    fun getEntry(word: String): DictEntry? {
        val clean = word.lowercase().trim()
        return entries[clean]
    }

    fun getTags(word: String): Set<POSTag> {
        val clean = word.lowercase().trim()
        val entry = entries[clean]
        if (entry != null && entry.tags.isNotEmpty()) {
            return entry.tags
        }
        return inferTagsForWord(clean)
    }

    fun addCustomWord(word: String) {
        val clean = word.lowercase().trim()
        if (clean.isNotEmpty()) {
            userCustomWords.add(clean)
            addEntry(clean, 50000, setOf(POSTag.NN))
        }
    }

    fun removeCustomWord(word: String) {
        val clean = word.lowercase().trim()
        userCustomWords.remove(clean)
        entries.remove(clean)
        var current = root
        for (ch in clean) {
            current = current.children[ch] ?: return
        }
        current.isWord = false
    }

    fun hasCustomWord(word: String): Boolean = word.lowercase().trim() in userCustomWords
}
