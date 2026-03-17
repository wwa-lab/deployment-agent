import { DataSource, EntityManager } from "typeorm";
import { Stage } from "../../contracts/enums";
import { RequestEntity } from "./Request.entity";

export class RequestRepository {
  private readonly repo;

  constructor(ds: DataSource) {
    this.repo = ds.getRepository(RequestEntity);
  }

  private repoFor(em?: EntityManager) {
    return em ? em.getRepository(RequestEntity) : this.repo;
  }

  async findById(id: string, em?: EntityManager): Promise<RequestEntity | null> {
    return this.repoFor(em).findOne({ where: { id }, relations: ["tasks"] });
  }

  async findByReleaseFlowId(
    releaseFlowId: string,
    em?: EntityManager
  ): Promise<RequestEntity[]> {
    return this.repoFor(em).find({
      where: { releaseFlowId },
      relations: ["tasks"],
      order: { createdAt: "ASC" },
    });
  }

  async findByReleaseFlowAndStage(
    releaseFlowId: string,
    stage: Stage,
    em?: EntityManager
  ): Promise<RequestEntity | null> {
    return this.repoFor(em).findOne({
      where: { releaseFlowId, stage },
      relations: ["tasks"],
    });
  }

  async save(entity: RequestEntity, em?: EntityManager): Promise<RequestEntity> {
    return this.repoFor(em).save(entity);
  }

  async updateStatus(
    id: string,
    requestStatus: RequestEntity["requestStatus"],
    em?: EntityManager
  ): Promise<void> {
    await this.repoFor(em).update({ id }, { requestStatus });
  }
}
