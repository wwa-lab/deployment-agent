/**
 * Task input validation – pure function, extensible by task type.
 *
 * Currently accepts all valid JSON (minimal validation).
 * Extend this function when task-type-specific schemas are finalized.
 *
 * @param _taskType The task type (e.g., "deploy", "smoke_test")
 * @param input The parsed input object to validate
 * @returns null if valid, error string if invalid
 */
export function validateTaskInput(_taskType: string, input: unknown): string | null {
  // Placeholder: accept all non-null input for now
  // To extend: add task-type-specific JSON schemas (e.g., via Zod or similar)
  if (input === undefined) {
    return "Task input cannot be undefined";
  }

  return null;
}
