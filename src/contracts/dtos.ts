import { z } from "zod";
import { TaskStatus, RequestStatus, FlowStatus, ExecutionStatus, AuditActionType, ExecutionType } from "./enums";

/**
 * Data Transfer Objects (DTOs) for API responses and request bodies.
 * Includes Zod schemas for validation.
 */

// ===== Request Body DTOs =====

export const DecisionRequestDtoSchema = z.object({
  decision: z.enum(["approve", "reject", "rerun", "skip"]),
  comment: z.string().optional(),
});

export type DecisionRequestDto = z.infer<typeof DecisionRequestDtoSchema>;

// ===== Response DTOs =====

export interface TaskExecutionHistoryDto {
  id: string;
  taskId: string;
  attemptNumber: number;
  executionStatus: ExecutionStatus;
  inputSnapshot: Record<string, unknown> | null;
  resultSummary: Record<string, unknown> | null;
  resultLogs: string | null;
  startTime: string; // ISO UTC
  endTime: string | null; // ISO UTC
}

export interface TaskDto {
  id: string;
  requestId: string;
  taskGroupId: string;
  taskGroupName: string;
  stepSeq: number;
  taskName: string;
  executionType: ExecutionType;
  taskStatus: TaskStatus;
  inputParameters: Record<string, unknown> | null;
  expectedOutput: string | null;
  owner: string | null;
  plannedStartTime: string | null; // ISO UTC
  plannedEndTime: string | null;   // ISO UTC
  currentResultSummary: Record<string, unknown> | null;
  latestExecutionId: string | null;
  version: number;
}

export interface RequestDto {
  id: string;
  releaseFlowId: string;
  stage: string;
  requestStatus: RequestStatus;
  tasks?: TaskDto[];
}

export interface ReleaseFlowListItemDto {
  id: string;
  projectId: string;
  projectName: string;
  releaseId: string | null;
  normalizedReleaseId: string;
  currentStage: string;
  flowStatus: FlowStatus;
  reviewStatus: string;
}

export interface ReleaseFlowDetailDto extends ReleaseFlowListItemDto {
  requests: RequestDto[];
}

export interface AuditLogEntryDto {
  id: string;
  timestamp: string; // ISO UTC
  operatorId: string;
  operatorRole: string;
  actionType: AuditActionType;
  releaseFlowId: string | null;
  requestId: string | null;
  taskId: string | null;
  contextPayload: Record<string, unknown> | null;
}

/**
 * Generic paginated response wrapper.
 */
export interface PaginatedResponseDto<T> {
  data: T[];
  total: number;
  page: number;
  size: number;
}

/**
 * Error response DTO.
 */
export interface ErrorResponseDto {
  code: string;
  message: string;
  details?: unknown;
}
