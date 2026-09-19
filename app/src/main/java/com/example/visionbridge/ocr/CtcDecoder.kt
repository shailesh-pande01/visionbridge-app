package com.example.visionbridge.ocr

/**
 * CTC greedy decoder for PP-OCRv5 recognition outputs.
 *
 * Index 0 is reserved for CTC blank token.
 * Indices 1..N correspond to the character dictionary symbols.
 * Index N+1 is the space token " ".
 *
 * Total classes: dictionary.size + 2.
 */
class CtcDecoder(characterDict: List<String>) {

    val labels: List<String> = listOf("blank") + characterDict + listOf(" ")
    val classCount: Int = labels.size

    data class DecodeResult(
        val text: String,
        val score: Float
    )

    /**
     * Decodes the recognition model output for a single item in the batch.
     *
     * @param probs Array of shape [timeSteps, numClasses] (flattened as 1D array of size timeSteps * numClasses).
     * @param timeSteps Number of time slices (e.g. 40).
     * @param numClasses Number of classes per slice (must equal [classCount]).
     */
    fun decode(probs: FloatArray, timeSteps: Int, numClasses: Int): DecodeResult {
        if (numClasses != classCount) {
            throw IllegalStateException(
                "Model class count ($numClasses) does not match expected dictionary size + 2 ($classCount)."
            )
        }

        val keptChars = StringBuilder()
        val keptScores = ArrayList<Float>()
        var lastIndex = -1

        for (t in 0 until timeSteps) {
            val offset = t * numClasses
            var maxIdx = 0
            var maxVal = probs[offset]

            for (c in 1 until numClasses) {
                val v = probs[offset + c]
                if (v > maxVal) {
                    maxVal = v
                    maxIdx = c
                }
            }

            // Skip blank (index 0) and collapse consecutive duplicates
            if (maxIdx != 0 && maxIdx != lastIndex) {
                keptChars.append(labels[maxIdx])
                keptScores.add(maxVal)
            }
            lastIndex = maxIdx
        }

        val text = keptChars.toString()
        val avgScore = if (keptScores.isEmpty()) 0.0f else keptScores.sum() / keptScores.size

        return DecodeResult(text = text, score = avgScore)
    }
}
