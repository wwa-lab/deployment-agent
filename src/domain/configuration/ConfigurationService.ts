import { ValidationError } from "../../errors/AppError";
import { ConfigKey, CONFIG_KEYS } from "../../contracts/enums";
import { UserContext } from "../../contracts/UserContext";
import { ConfigurationItemEntity } from "./ConfigurationItem.entity";
import { ConfigurationRepository } from "./ConfigurationRepository";
import { AuditLoggerService } from "../audit/AuditLoggerService";

const URL_PATTERN = /^https?:\/\/.+/i;
const HTTPS_PATTERN = /^https:\/\/.+/i;

/**
 * Validation rules for each known config key.
 * Locked keys: jenkins_url, ansible_url, execution_callback_endpoint.
 */
const CONFIG_VALIDATION: Record<ConfigKey, (value: string) => string | null> = {
  jenkins_url: (v) =>
    URL_PATTERN.test(v) ? null : "jenkins_url must be a valid URI (http/https)",
  ansible_url: (v) =>
    URL_PATTERN.test(v) ? null : "ansible_url must be a valid URI (http/https)",
  execution_callback_endpoint: (v) =>
    HTTPS_PATTERN.test(v)
      ? null
      : "execution_callback_endpoint must use HTTPS",
};

export interface UpdateConfigInput {
  key: ConfigKey;
  value: string;
  description?: string;
}

/**
 * ConfigurationService – CRUD for system configuration items.
 * Changes apply to future executions only (locked design decision).
 * Updates are gated to DevOps Admin role – enforced in the HTTP handler.
 */
export class ConfigurationService {
  constructor(
    private readonly configRepo: ConfigurationRepository,
    private readonly auditLogger: AuditLoggerService
  ) {}

  async listAll(): Promise<ConfigurationItemEntity[]> {
    return this.configRepo.findAll();
  }

  async getByKey(key: ConfigKey): Promise<ConfigurationItemEntity | null> {
    if (!CONFIG_KEYS.includes(key)) {
      throw new ValidationError(`Unknown configuration key: ${key}`);
    }
    return this.configRepo.findByKey(key);
  }

  /**
   * Create or update a configuration item.
   * Validates value format per key rules.
   * Applies to future executions only – no in-flight task modification.
   * Creates audit log entry on success.
   */
  async upsert(input: UpdateConfigInput, user: UserContext): Promise<ConfigurationItemEntity> {
    if (!CONFIG_KEYS.includes(input.key)) {
      throw new ValidationError(`Unknown configuration key: '${input.key}'`);
    }

    const validationError = CONFIG_VALIDATION[input.key](input.value);
    if (validationError) {
      throw new ValidationError(validationError, { key: input.key, value: input.value });
    }

    const existing = await this.configRepo.findByKey(input.key);
    const oldValue = existing?.configValue ?? null;

    const entity = existing ?? new ConfigurationItemEntity();
    entity.configKey = input.key;
    entity.configValue = input.value;
    entity.description = input.description ?? existing?.description ?? null;
    entity.updatedBy = user.userId;

    const saved = await this.configRepo.save(entity);

    await this.auditLogger.log({
      user,
      actionType: "config_update",
      context: {
        configKey: input.key,
        oldValue,
        newValue: input.value,
      },
    });

    return saved;
  }
}
