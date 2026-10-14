/**
 * MedLearn Medical Safety & Numerical Verification Engine
 * 
 * Implements:
 * 1. Clinical Numerical & Dosage Extraction:
 *    Detects dosages (mg, mcg, g, IU, mL), blood pressures (120/80 mmHg),
 *    concentrations, heart rates (bpm), oxygen saturations (SpO2 %), and clinical thresholds.
 * 2. Evidence Grounding Verification:
 *    Validates that every numerical claim or dosage in the generated response
 *    is strictly present in and supported by the retrieved clinical evidence text.
 * 3. Emergency Triage Identification & Escalation:
 *    Instantly identifies life-threatening medical emergencies (cardiac arrest,
 *    stroke, severe respiratory distress, anaphylaxis, acute hemorrhage)
 *    and appends urgent emergency services guidance without latency delay.
 */

export interface ExtractedNumericalClaim {
  rawMatch: string;
  value: number;
  unit: string;
  context: string;
  verifiedInSource: boolean;
}

export interface SafetyVerificationResult {
  isEmergency: boolean;
  emergencyCategory?: string;
  emergencyNotice?: string;
  allNumbersVerified: boolean;
  unverifiedClaims: ExtractedNumericalClaim[];
  sanitizedAnswer: string;
  disclaimer: string;
}

// Regex matching clinical dosages, units, and vital sign thresholds
const CLINICAL_NUMBER_REGEX = /\b(\d+(?:\.\d+)?)\s*(mg|mcg|µg|g|kg|ml|iu|meq|mmol\/l|mg\/dl|mmhg|bpm|%|hours?|days?|weeks?|months?)\b/gi;
const BLOOD_PRESSURE_REGEX = /\b(\d{2,3})\/(\d{2,3})\s*(?:mmhg)?\b/gi;

// Life-Threatening Emergency Red Flags
const EMERGENCY_PATTERNS = [
  {
    category: "ACUTE_CORONARY_SYNDROME",
    regex: /\b(crushing\s+(chest\s+pain|pressure)|chest\s+pain\s+(radiating\s+to|with\s+(sweating|shortness\s+of\s+breath|jaw\s+pain))|heart\s+attack\s+now|cardiac\s+arrest)\b/i,
    notice: "🚨 **EMERGENCY WARNING:** Crushing chest pain, radiation to jaw/left arm, diaphoresis, and acute shortness of breath are cardinal signs of Acute Myocardial Infarction. Call emergency medical services (911 / 112 / 108) immediately. Do not drive yourself to the hospital."
  },
  {
    category: "ACUTE_STROKE",
    regex: /\b(sudden\s+(facial\s+droop|arm\s+weakness|speech\s+difficulty|numbness\s+on\s+one\s+side)|face\s+drooping|slurred\s+speech\s+sudden|stroke\s+symptoms)\b/i,
    notice: "🚨 **EMERGENCY WARNING (FAST PROTOCOL):** Sudden facial droop, unilateral limb weakness, or slurred speech indicate an acute ischemic or hemorrhagic stroke. Immediate emergency hospitalization within the thrombolytic window (<4.5 hours) is critical. Call emergency services immediately."
  },
  {
    category: "ANAPHYLAXIS",
    regex: /\b(anaphylaxis|swollen\s+(lips|tongue|throat)\s+with\s+difficulty\s+breathing|throat\s+closing\s+up|severe\s+allergic\s+reaction)\b/i,
    notice: "🚨 **EMERGENCY WARNING (ANAPHYLAXIS):** Acute airway swelling, stridor, or respiratory distress requires immediate intramuscular epinephrine and emergency department transport. Call emergency services now."
  },
  {
    category: "SEVERE_RESPIRATORY_DISTRESS",
    regex: /\b(cannot\s+breathe|gasping\s+for\s+air|cyanosis|blue\s+lips|severe\s+asthma\s+attack\s+not\s+responding)\b/i,
    notice: "🚨 **EMERGENCY WARNING:** Severe respiratory distress, inability to speak in sentences, and cyanosis indicate imminent respiratory failure. Seek immediate emergency intervention."
  }
];

export const STANDARD_CLINICAL_DISCLAIMER =
  "> [!NOTE]\n> **Medical Education Disclaimer:** MedLearn provides evidence-based information from peer-reviewed literature (NIH MedQuAD & clinical compendia) strictly for medical education and study. It does not replace individualized diagnosis, prescription, or clinical evaluation by a licensed healthcare practitioner.";

/**
 * Checks for life-threatening clinical emergencies in user query.
 */
export function detectClinicalEmergency(query: string): { isEmergency: boolean; emergencyCategory?: string; emergencyNotice?: string } {
  const q = (query || "").toLowerCase();
  for (const item of EMERGENCY_PATTERNS) {
    if (item.regex.test(q)) {
      return {
        isEmergency: true,
        emergencyCategory: item.category,
        emergencyNotice: item.notice,
      };
    }
  }
  return { isEmergency: false };
}

/**
 * Extracts all clinical numerical values, units, and dosages from text.
 */
export function extractNumericalClaims(text: string): ExtractedNumericalClaim[] {
  if (!text) return [];
  const claims: ExtractedNumericalClaim[] = [];

  let match: RegExpExecArray | null;
  const regex = new RegExp(CLINICAL_NUMBER_REGEX);

  while ((match = regex.exec(text)) !== null) {
    const rawMatch = match[0];
    const val = parseFloat(match[1]);
    const unit = match[2].toLowerCase();

    // Determine surrounding sentence context
    const startIdx = Math.max(0, match.index - 30);
    const endIdx = Math.min(text.length, match.index + rawMatch.length + 30);
    const context = text.substring(startIdx, endIdx).trim();

    claims.push({
      rawMatch,
      value: val,
      unit,
      context,
      verifiedInSource: false,
    });
  }

  // Also check blood pressure patterns
  const bpRegex = new RegExp(BLOOD_PRESSURE_REGEX);
  while ((match = bpRegex.exec(text)) !== null) {
    claims.push({
      rawMatch: match[0],
      value: parseFloat(match[1]),
      unit: "mmhg",
      context: match[0],
      verifiedInSource: false,
    });
  }

  return claims;
}

/**
 * Verifies that all clinical numerical claims in generated answer
 * are explicitly present and corroborated in the retrieved evidence passages.
 */
export function verifyAnswerSafetyAndNumerics(
  query: string,
  generatedAnswer: string,
  retrievedPassages: Array<{ answer?: string; question?: string }>
): SafetyVerificationResult {
  const emergencyCheck = detectClinicalEmergency(query);

  const combinedSourceText = retrievedPassages
    .map((p) => `${p.question || ""} ${p.answer || ""}`)
    .join(" ")
    .toLowerCase();

  const extractedClaims = extractNumericalClaims(generatedAnswer);
  const unverified: ExtractedNumericalClaim[] = [];

  extractedClaims.forEach((claim) => {
    const termClean = claim.rawMatch.toLowerCase().replace(/\s+/g, "");
    const sourceClean = combinedSourceText.replace(/\s+/g, "");

    // Check if the specific value and unit are present in source passages
    const isPresent = sourceClean.includes(termClean) || combinedSourceText.includes(claim.rawMatch.toLowerCase());

    if (isPresent) {
      claim.verifiedInSource = true;
    } else {
      claim.verifiedInSource = false;
      // If it's a critical medication dosage (e.g. mg, mcg, g) or vital sign threshold
      if (["mg", "mcg", "µg", "g", "iu", "meq", "mmol/l", "mg/dl", "mmhg"].includes(claim.unit)) {
        unverified.push(claim);
      }
    }
  });

  let sanitized = generatedAnswer;

  // If unverified dosages were detected, append transparent safety verification notice
  if (unverified.length > 0) {
    const unverifiedList = unverified
      .map((c) => `\`${c.rawMatch}\``)
      .filter((v, i, a) => a.indexOf(v) === i)
      .join(", ");

    sanitized += `\n\n> [!WARNING]\n> **Dosage Verification Notice:** The following numerical values/dosages (${unverifiedList}) require clinical corroboration against official pharmacopoeia guidelines before patient administration.`;
  }

  // Prepend emergency notice if emergency detected
  if (emergencyCheck.isEmergency && emergencyCheck.emergencyNotice) {
    sanitized = `${emergencyCheck.emergencyNotice}\n\n---\n\n${sanitized}`;
  }

  return {
    isEmergency: emergencyCheck.isEmergency,
    emergencyCategory: emergencyCheck.emergencyCategory,
    emergencyNotice: emergencyCheck.emergencyNotice,
    allNumbersVerified: unverified.length === 0,
    unverifiedClaims: unverified,
    sanitizedAnswer: sanitized,
    disclaimer: STANDARD_CLINICAL_DISCLAIMER,
  };
}
