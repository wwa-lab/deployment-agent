import { DataSource, EntityManager } from "typeorm";
import { ConfigKey } from "../../contracts/enums";
import { ConfigurationItemEntity } from "./ConfigurationItem.entity";

export class ConfigurationRepository {
  private readonly repo;

  constructor(ds: DataSource) {
    this.repo = ds.getRepository(ConfigurationItemEntity);
  }

  private repoFor(em?: EntityManager) {
    return em ? em.getRepository(ConfigurationItemEntity) : this.repo;
  }

  async findAll(em?: EntityManager): Promise<ConfigurationItemEntity[]> {
    return this.repoFor(em).find({ order: { configKey: "ASC" } });
  }

  async findByKey(
    key: ConfigKey,
    em?: EntityManager
  ): Promise<ConfigurationItemEntity | null> {
    return this.repoFor(em).findOne({ where: { configKey: key } });
  }

  async save(
    entity: ConfigurationItemEntity,
    em?: EntityManager
  ): Promise<ConfigurationItemEntity> {
    return this.repoFor(em).save(entity);
  }
}
