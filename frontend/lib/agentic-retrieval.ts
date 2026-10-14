/**
 * MedLearn Controlled Agentic Retrieval Engine
 * 
 * Implements the Agentic Multi-Hop Retrieval Loop via `search_medical_kb`:
 * 1. Question Decomposition: Breaks multi-part questions into focused clinical subquestions
 * 2. Query Rewriting & Expansion: Normalizes ambiguous phrasing into canonical medical terminology
 * 3. Iterative Multi-Facet Search: Queries `search_medical_kb` per subquestion
 * 4. Evidence Coverage Assessment: Evaluates whether all query aspects are supported
 * 5. Bounded Budget Control: Maximum 3 iterations, 4000ms timeout, candidate limits
 */

import { RerankerCandidate } from "./reranker";

export interface MedicalKbSearchQuery {
  query: string;
  subtopic?: string;
  aspect?: "DEFINITION" | "SYMPTOMS" | "CAUSES" | "TREATMENT" | "DIAGNOSIS" | "GENERAL";
  maxResults?: number;
}

export interface MedicalKbSearchResult {
  query: string;
  aspect: string;
  totalHits: number;
  passages: RerankerCandidate[];
  coveredConcepts: string[];
  latencyMs: number;
}

export interface AgenticRetrievalState {
  originalQuery: string;
  subquestions: Array<{ subquery: string; aspect: string; covered: boolean }>;
  allRetrievedPassages: RerankerCandidate[];
  iterationsRun: number;
  maxIterations: number;
  budgetExhausted: boolean;
  unresolvedAspects: string[];
}

/**
 * Sanitizes search query to prevent injection, unapproved access, or runaway regexes.
 */
export function sanitizeKbQuery(query: string): string {
  if (!query) return "";
  return query
    .replace(/[^\w\s-?,]/g, " ")
    .replace(/\s{2,}/g, " ")
    .slice(0, 200)
    .trim();
}

/**
 * Decomposes a multi-part question into focused clinical subquestions.
 * Example:
 * "What is pulmonary embolism and how is it diagnosed and treated?"
 * -> Subquestion 1: "What is pulmonary embolism?" (Aspect: DEFINITION)
 * -> Subquestion 2: "Diagnosis of pulmonary embolism" (Aspect: DIAGNOSIS)
 * -> Subquestion 3: "Treatment of pulmonary embolism" (Aspect: TREATMENT)
 */
export function decomposeMultiPartQuestion(
  rawQuery: string,
  primaryEntity: string
): Array<{ subquery: string; aspect: string }> {
  const q = rawQuery.trim();
  const lower = q.toLowerCase();
  const entity = primaryEntity || q;

  const subquestions: Array<{ subquery: string; aspect: string }> = [];

  const hasDefinition = /what\s+is|what\s+are|define|overview|meaning\s+of/i.test(lower);
  const hasSymptoms = /symptom|sign|presentation|clinical\s*feature/i.test(lower);
  const hasCauses = /cause|etiology|risk\s*factor|why\s+does/i.test(lower);
  const hasDiagnosis = /diagnos|test|screen|investigation|how\s+to\s+diagnose/i.test(lower);
  const hasTreatment = /treat|treatment|cure|management|medication|therapy|how\s+to\s+treat/i.test(lower);

  let facetCount = 0;
  if (hasDefinition) facetCount++;
  if (hasSymptoms) facetCount++;
  if (hasCauses) facetCount++;
  if (hasDiagnosis) facetCount++;
  if (hasTreatment) facetCount++;

  // Single-facet query: no decomposition needed
  if (facetCount <= 1) {
    let aspect = "DEFINITION";
    if (hasSymptoms) aspect = "SYMPTOMS";
    else if (hasCauses) aspect = "CAUSES";
    else if (hasDiagnosis) aspect = "DIAGNOSIS";
    else if (hasTreatment) aspect = "TREATMENT";

    subquestions.push({ subquery: q, aspect });
    return subquestions;
  }

  // Multi-facet query: build targeted subqueries around primary entity
  if (hasDefinition) {
    subquestions.push({ subquery: `What is ${entity}?`, aspect: "DEFINITION" });
  }
  if (hasSymptoms) {
    subquestions.push({ subquery: `Symptoms of ${entity}`, aspect: "SYMPTOMS" });
  }
  if (hasCauses) {
    subquestions.push({ subquery: `Causes and risk factors for ${entity}`, aspect: "CAUSES" });
  }
  if (hasDiagnosis) {
    subquestions.push({ subquery: `Diagnosis and testing for ${entity}`, aspect: "DIAGNOSIS" });
  }
  if (hasTreatment) {
    subquestions.push({ subquery: `Treatment and management of ${entity}`, aspect: "TREATMENT" });
  }

  return subquestions;
}

/**
 * Assesses evidence coverage: Determines which subquestions are adequately supported
 * by the retrieved candidate cluster.
 */
export function assessEvidenceCoverage(
  subquestions: Array<{ subquery: string; aspect: string; covered?: boolean }>,
  passages: RerankerCandidate[],
  primaryEntity: string
): { fullyCovered: boolean; unresolvedAspects: string[] } {
  const unresolved: string[] = [];
  const entityLower = (primaryEntity || "").toLowerCase();

  subquestions.forEach((sub) => {
    let supported = false;
    const aspect = sub.aspect;

    for (const p of passages) {
      const qLower = (p.question || "").toLowerCase();
      const aLower = (p.answer || "").toLowerCase();
      const tLower = (p.topic || "").toLowerCase();

      // Entity presence check
      const hasEntity = tLower.includes(entityLower) || qLower.includes(entityLower) || aLower.includes(entityLower);

      if (hasEntity) {
        if (aspect === "TREATMENT" && /treatment|treat|cure|management|therapy/i.test(qLower + " " + aLower)) {
          supported = true;
          break;
        } else if (aspect === "SYMPTOMS" && /symptom|sign|manifestation/i.test(qLower + " " + aLower)) {
          supported = true;
          break;
        } else if (aspect === "CAUSES" && /cause|etiology|risk/i.test(qLower + " " + aLower)) {
          supported = true;
          break;
        } else if (aspect === "DIAGNOSIS" && /diagnos|test|screen/i.test(qLower + " " + aLower)) {
          supported = true;
          break;
        } else if (aspect === "DEFINITION") {
          supported = true;
          break;
        }
      }
    }

    sub.covered = supported;
    if (!supported) {
      unresolved.push(sub.aspect);
    }
  });

  return {
    fullyCovered: unresolved.length === 0,
    unresolvedAspects: unresolved,
  };
}
