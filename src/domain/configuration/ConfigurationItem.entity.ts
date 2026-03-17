import { Column, Entity, UpdateDateColumn } from "typeorm";
import { ConfigKey } from "../../contracts/enums";

/**
 * ConfigurationItem – key/value pairs for system configuration.
 * PK is the config key string (no separate ID column).
 * Changes apply to future executions only (locked design decision).
 * No version column – config updates are authoritative overwrites by DevOps Admin.
 */
@Entity("DA_CONFIGURATION_ITEM")
export class ConfigurationItemEntity {
  @Column({ type: "varchar", length: 100, primary: true, name: "config_key" })
  configKey!: ConfigKey;

  @Column({ type: "varchar", length: 2000, name: "config_value" })
  configValue!: string;

  @Column({ type: "varchar", length: 500, nullable: true, name: "description" })
  description!: string | null;

  @Column({ type: "varchar", length: 255, name: "updated_by" })
  updatedBy!: string;

  @UpdateDateColumn({ name: "updated_at" })
  updatedAt!: Date;
}
