import { FastifyRequest, FastifyReply } from "fastify";
import { Role, ROLES } from "../../contracts/enums";
import { UserContext } from "../../contracts/UserContext";
import { ForbiddenError, UnauthorizedError } from "../../errors/AppError";

// Declaration merging to add userContext to FastifyRequest.
declare module "fastify" {
  interface FastifyRequest {
    userContext: UserContext;
  }
}

/**
 * WWA Auth middleware – placeholder for RESOLVE-Q5.
 * Reads identity from request headers that the WWA platform is expected to inject:
 *   X-User-Id   : user identifier
 *   X-User-Role : one of DEVELOPER | TL | DEVOPS_ADMIN | AUDIT | MANAGEMENT
 *
 * Replace this with JWT/session validation once RESOLVE-Q5 is resolved.
 */
export async function extractUserContext(
  req: FastifyRequest,
  _reply: FastifyReply
): Promise<void> {
  const userId = req.headers["x-user-id"];
  const userRole = req.headers["x-user-role"];

  if (!userId || typeof userId !== "string") {
    throw new UnauthorizedError();
  }
  if (
    !userRole ||
    typeof userRole !== "string" ||
    !ROLES.includes(userRole as Role)
  ) {
    throw new UnauthorizedError();
  }

  req.userContext = {
    userId,
    role: userRole as Role,
    displayName: (req.headers["x-user-display-name"] as string | undefined),
  };
}

/**
 * Asserts the current user holds one of the required roles.
 * Throws ForbiddenError if the role check fails.
 */
export function requireRole(
  req: FastifyRequest,
  action: string,
  ...allowedRoles: Role[]
): void {
  if (!allowedRoles.includes(req.userContext.role)) {
    throw new ForbiddenError(action);
  }
}
