import { FastifyInstance } from "fastify";
import { z } from "zod";
import { CONFIG_KEYS, ConfigKey } from "../../contracts/enums";
import { ValidationError } from "../../errors/AppError";
import { ConfigurationService } from "../../domain/configuration/ConfigurationService";
import { requireRole } from "../middleware/auth";

const UpdateConfigBodySchema = z.object({
  key: z.enum(CONFIG_KEYS),
  value: z.string().min(1),
  description: z.string().optional(),
});

type UpdateConfigBody = z.infer<typeof UpdateConfigBodySchema>;

/**
 * Configuration management endpoints (T2.2).
 *
 * GET  /api/deployment-agent/config       – list all config items (any authenticated user)
 * POST /api/deployment-agent/config       – upsert a config item (DevOps Admin only)
 */
export function registerConfigurationRoutes(
  app: FastifyInstance,
  configService: ConfigurationService
): void {
  app.get("/api/deployment-agent/config", async (_req, reply) => {
    const items = await configService.listAll();
    return reply.send({
      data: items.map((item) => ({
        key: item.configKey,
        value: item.configValue,
        description: item.description,
        updatedBy: item.updatedBy,
        updatedAt: item.updatedAt,
      })),
    });
  });

  app.post<{ Body: UpdateConfigBody }>(
    "/api/deployment-agent/config",
    async (req, reply) => {
      // Enforce DevOps Admin authorization server-side.
      requireRole(req, "config_update", "DEVOPS_ADMIN");

      const parsed = UpdateConfigBodySchema.safeParse(req.body);
      if (!parsed.success) {
        throw new ValidationError("Invalid config update payload", parsed.error.flatten());
      }

      const { key, value, description } = parsed.data;
      const saved = await configService.upsert(
        { key: key as ConfigKey, value, description },
        req.userContext
      );

      return reply.send({
        key: saved.configKey,
        value: saved.configValue,
        description: saved.description,
        updatedBy: saved.updatedBy,
        updatedAt: saved.updatedAt,
      });
    }
  );
}
