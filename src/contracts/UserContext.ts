import { Role } from "./enums";

/**
 * Resolved user identity injected by WWA auth middleware.
 * Contract is intentionally minimal – exact claims TBD (RESOLVE-Q5).
 */
export interface UserContext {
  userId: string;
  role: Role;
  displayName?: string;
}
