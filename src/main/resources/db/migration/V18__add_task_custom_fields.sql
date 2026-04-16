-- V18: MVP Foundation Seam — per-agent custom fields on Task.
--
-- Adds a JSON blob column to DA_TASK so that individual agents can define
-- their own template columns (via the TemplateSchemaRegistry) and persist
-- those values without adding physical columns that every other agent would
-- also have to carry. Zero runtime behavior in MVP: the default template
-- schema is shared across all agents, so this column stays NULL. Exists so
-- that later phases can introduce agent-specific template customization
-- without a schema migration per field.
--
-- Pair with: com.wwa.agenthub.domain.fileimport.TemplateSchemaRegistry
-- See docs/04-architecture/architecture.md §MVP Foundation Seams.

ALTER TABLE DA_TASK
    ADD (custom_fields CLOB);
