/**
 * MedLearn Production BM25 Keyword Search Engine
 * 
 * Implements the standard Okapi BM25 scoring algorithm:
 * BM25(D, Q) = sum_{q_i in Q} IDF(q_i) * (f(q_i, D) * (k1 + 1)) / (f(q_i, D) + k1 * (1 - b + b * (|D| / avgdl)))
 * 
 * Parameters:
 * - k1 = 1.2: Term frequency saturation parameter
 * - b = 0.75: Document length normalization parameter
 * - avgdl = 85: Calibrated average document length for NIH MedQuAD passages
 */

export interface BM25Document {
  id: number | string;
  subject?: string;
  topic?: string;
  question: string;
  answer: string;
  source?: string;
  keywords?: string;
  tokens?: string[];
  docLength?: number;
}

export interface BM25ScoredResult extends BM25Document {
  bm25Score: number;
}

const K1 = 1.2;
const B = 0.75;
const DEFAULT_AVGDL = 85.0;
const CORPUS_SIZE = 16418;

// Common English and query stopwords (preserved medical terms like 'not', 'no' for negation)
const STOP_WORDS = new Set([
  "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are",
  "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but",
  "by", "can", "could", "did", "do", "does", "doing", "down", "during", "each", "few", "for",
  "from", "further", "had", "has", "have", "having", "he", "her", "here", "hers", "herself",
  "him", "himself", "his", "how", "i", "if", "in", "into", "is", "it", "its", "itself",
  "me", "more", "most", "my", "myself", "of", "off", "on", "once", "only", "or", "other",
  "ought", "our", "ours", "ourselves", "out", "over", "own", "same", "she", "should", "so",
  "some", "such", "than", "that", "the", "their", "theirs", "them", "themselves", "then",
  "there", "these", "they", "this", "those", "through", "to", "too", "under", "until", "up",
  "very", "was", "we", "were", "what", "when", "where", "which", "while", "who", "whom",
  "why", "with", "would", "you", "your", "yours", "yourself", "yourselves"
]);

/**
 * Tokenizes and normalizes text for BM25 indexing and querying.
 */
export function tokenizeText(text: string): string[] {
  if (!text) return [];
  return text
    .toLowerCase()
    .replace(/[^\w\s-]/g, " ")
    .split(/[\s-]+/)
    .map((w) => w.trim())
    .filter((w) => w.length >= 2 && !STOP_WORDS.has(w));
}

/**
 * Computes smoothed Inverse Document Frequency (IDF)
 * IDF(qi) = ln(1 + (N - n(qi) + 0.5) / (n(qi) + 0.5))
 */
export function computeIdf(term: string, docCountWithTerm: number = 20, totalDocs: number = CORPUS_SIZE): number {
  const n = Math.max(1, docCountWithTerm);
  const idf = Math.log(1 + (totalDocs - n + 0.5) / (n + 0.5));
  return Math.max(0.1, idf);
}

/**
 * Scores a collection of candidate documents against a query using Okapi BM25.
 */
export function scoreDocumentsBM25(
  query: string,
  documents: BM25Document[],
  k1: number = K1,
  b: number = B,
  avgdl: number = DEFAULT_AVGDL
): BM25ScoredResult[] {
  if (!query || !documents || documents.length === 0) {
    return [];
  }

  const queryTokens = tokenizeText(query);
  if (queryTokens.length === 0) {
    return documents.map((d) => ({ ...d, bm25Score: 0 }));
  }

  // Pre-tokenize and calculate document lengths
  const preparedDocs = documents.map((doc) => {
    const fullText = `${doc.topic || ""} ${doc.question || ""} ${doc.keywords || ""} ${doc.answer || ""}`;
    const tokens = tokenizeText(fullText);
    const docLength = tokens.length || 1;

    // Build term frequency map
    const tfMap = new Map<string, number>();
    tokens.forEach((t) => {
      tfMap.set(t, (tfMap.get(t) || 0) + 1);
    });

    return {
      doc,
      tokens,
      docLength,
      tfMap,
    };
  });

  // Calculate actual avgdl from candidate set if sufficient documents
  const actualAvgdl = preparedDocs.reduce((acc, d) => acc + d.docLength, 0) / preparedDocs.length || avgdl;

  // Calculate document frequency n(qi) across candidate cluster
  const dfMap = new Map<string, number>();
  queryTokens.forEach((qTerm) => {
    let count = 0;
    preparedDocs.forEach((d) => {
      if (d.tfMap.has(qTerm)) count++;
    });
    dfMap.set(qTerm, count);
  });

  // Score each document
  const scored: BM25ScoredResult[] = preparedDocs.map(({ doc, docLength, tfMap }) => {
    let score = 0.0;

    queryTokens.forEach((qTerm) => {
      const tf = tfMap.get(qTerm) || 0;
      if (tf > 0) {
        const df = dfMap.get(qTerm) || 1;
        const idf = computeIdf(qTerm, df, preparedDocs.length);

        // BM25 term weighting formula
        const numerator = tf * (k1 + 1);
        const denominator = tf + k1 * (1 - b + b * (docLength / actualAvgdl));
        score += idf * (numerator / denominator);

        // Term match in topic or question header bonus (2.0x weight)
        const qLower = (doc.question || "").toLowerCase();
        const tLower = (doc.topic || "").toLowerCase();
        if (tLower.includes(qTerm) || qLower.includes(qTerm)) {
          score += idf * 1.5;
        }
      }
    });

    return {
      ...doc,
      bm25Score: Math.round(score * 1000) / 1000,
    };
  });

  // Sort descending by BM25 score
  scored.sort((a, b) => b.bm25Score - a.bm25Score);
  return scored;
}
