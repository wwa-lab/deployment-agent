/**
 * Canonical error codes used across domain and HTTP layers.
 * HTTP status mapping is in src/http/server.ts error handler.
 */
export type ErrorCode =
  | "NOT_FOUND"
  | "CONFLICT"
  | "VALIDATION_ERROR"
  | "FORBIDDEN"
  | "UNAUTHORIZED"
  | "OPTIMISTIC_LOCK_CONFLICT"
  | "INVALID_STATE_TRANSITION"
  | "INTERNAL_ERROR";

export class AppError extends Error {
  constructor(
    public readonly code: ErrorCode,
    public readonly statusCode: number,
    message: string,
    public readonly details?: unknown
  ) {
    super(message);
    this.name = "AppError";
    Object.setPrototypeOf(this, new.target.prototype);
  }
}

export class NotFoundError extends AppError {
  constructor(resource: string, id: string) {
    super("NOT_FOUND", 404, `${resource} not found: ${id}`);
    this.name = "NotFoundError";
  }
}

export class ConflictError extends AppError {
  constructor(message: string, details?: unknown) {
    super("CONFLICT", 409, message, details);
    this.name = "ConflictError";
  }
}

/** Thrown when TypeORM optimistic lock version mismatch is detected. */
export class OptimisticLockConflictError extends AppError {
  constructor(resource: string) {
    super(
      "OPTIMISTIC_LOCK_CONFLICT",
      409,
      `Concurrent update conflict on ${resource}. Reload and retry.`
    );
    this.name = "OptimisticLockConflictError";
  }
}

export class ValidationError extends AppError {
  constructor(message: string, details?: unknown) {
    super("VALIDATION_ERROR", 400, message, details);
    this.name = "ValidationError";
  }
}

export class ForbiddenError extends AppError {
  constructor(action: string) {
    super("FORBIDDEN", 403, `Access denied: insufficient role for action '${action}'`);
    this.name = "ForbiddenError";
  }
}

export class UnauthorizedError extends AppError {
  constructor() {
    super("UNAUTHORIZED", 401, "Authentication required");
    this.name = "UnauthorizedError";
  }
}

export class InvalidStateTransitionError extends AppError {
  constructor(from: string, to: string, resource?: string) {
    super(
      "INVALID_STATE_TRANSITION",
      409,
      `Invalid state transition${resource ? ` on ${resource}` : ""}: ${from} → ${to}`
    );
    this.name = "InvalidStateTransitionError";
  }
}
