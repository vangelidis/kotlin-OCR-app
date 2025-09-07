package com.example.navlesson

import org.apache.commons.text.similarity.CosineSimilarity
import org.apache.commons.text.similarity.JaccardSimilarity
import org.apache.commons.text.similarity.LevenshteinDistance
import kotlin.math.min

fun cosineSimilarity(text1: String, text2: String): Double {
    val cosineSimilarity = CosineSimilarity()
    val vector1 = text1.split(" ").groupingBy { it }.eachCount().mapKeys { it.key as CharSequence }
    val vector2 = text2.split(" ").groupingBy { it }.eachCount().mapKeys { it.key as CharSequence }
    return cosineSimilarity.cosineSimilarity(vector1, vector2)
}

fun jaccardSimilarity(text1: String, text2: String): Double {
    val jaccardSimilarity = JaccardSimilarity()
    return jaccardSimilarity.apply(text1, text2)
}

fun levenshteinDistance(text1: String, text2: String): Int {
    val levenshteinDistance = LevenshteinDistance()
    return levenshteinDistance.apply(text1, text2)
}

fun similarity(text1: String, text2: String): Map<String, Any> {
    val cosineSim = cosineSimilarity(text1, text2)
    val jaccardSim = jaccardSimilarity(text1, text2)
    val levenshteinDist = levenshteinDistance(text1, text2)
    val editDist = editDistance(text1, text2)
    val similarityScore = (text1.length - editDist) / text1.length.toDouble()

    return mapOf(
        "Cosine Similarity" to cosineSim,
        "Jaccard Similarity" to jaccardSim,
        "Levenshtein Distance" to levenshteinDist,
        "Edit Distance Similarity" to similarityScore
    )

}

fun editDistance(text1: String, text2: String): Int {
    val costs = IntArray(text2.length + 1) { it }
    for (i in 1..text1.length) {
        var lastValue = i - 1
        for (j in 1..text2.length) {
            val newValue = min(
                min(costs[j] + 1, costs[j - 1] + 1),
                lastValue + if (text1[i - 1] == text2[j - 1]) 0 else 1
            )
            lastValue = costs[j]
            costs[j] = newValue
        }
    }
    return costs[text2.length]
}