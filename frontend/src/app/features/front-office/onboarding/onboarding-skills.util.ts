/**
 * Map quick MCQ answers (A–D) to six radar dimensions for the onboarding snapshot.
 * Heuristic — tune weights or replace with server-side scoring later.
 */
const DIMENSIONS = ['Frontend', 'Backend', 'DevOps', 'Algorithms', 'Databases', 'Soft Skills'] as const;

const LETTER_BIAS: Record<string, number[]> = {
  A: [12, 8, 5, 10, 8, 6],
  B: [8, 12, 8, 12, 10, 5],
  C: [6, 10, 14, 8, 8, 5],
  D: [8, 8, 6, 14, 12, 10],
};

export function computeOnboardingSkillScores(answers: (string | null)[]): Record<string, number> {
  const base = [42, 42, 38, 45, 40, 55];
  answers.forEach((letter) => {
    const L = (letter || 'A').toUpperCase().charAt(0);
    const bias = LETTER_BIAS[L] || LETTER_BIAS['A'];
    for (let i = 0; i < DIMENSIONS.length; i++) {
      base[i] += bias[i];
    }
  });
  const out: Record<string, number> = {};
  DIMENSIONS.forEach((dim, i) => {
    out[dim] = Math.min(100, Math.max(15, base[i]));
  });
  return out;
}

export function buildDevelopmentPlanNotes(
  situation: string | null,
  careerPath: string | null,
  skillScores: Record<string, number>
): string {
  const weakest = Object.entries(skillScores).sort((a, b) => a[1] - b[1])[0];
  const track = careerPath || 'your track';
  return (
    `Focus next: strengthen ${weakest[0]} for ${track}. ` +
    `Situation: ${situation || 'n/a'}. Complete a guided assessment in SmartHire to validate skills.`
  );
}
