# Changelog

## Unreleased

- Added working Excel template download support in the upload dialog.
- Restored local-profile authentication filters so session-backed user context is available during frontend testing.
- Added a Rundown Information panel on the release-flow detail page for each stage tab.
- Added editable stage-level rundown fields and API support for SNOW group, application, site, and estimated remaining time.
- Added request-level Refresh, Start Deployment, and Mark as Failed actions to the release-flow detail rundown panel.
- Expanded Execution Mix to show both task counts and manual/auto percentages.
- Exposed task-level category from imported Activity category and displayed it in the release detail table.
- Realigned task-row actions with the prototype by always showing Edit, View Result, and a Decision dropdown, with state-based disabling.
- Added tooltips to disabled task actions so users can see whether an action is blocked by role or task status.
- Simplified task action permissions so only the task owner or a DEVOPS_ADMIN can edit input, record results, submit auto execution, or apply decisions.
- Moved MANUAL task result submission into the Edit dialog so task-row actions stay closer to the prototype.
- Wired View Result to execution history so external job links and stored execution output can be viewed from the task result modal.
- Reworked the frontend shell from a standalone Deployment Agent sidebar into a WWA workspace with second-level navigation for Deployment Agent, Template Management, Configuration Management, and Audit Log.
- Redesigned Template Management details to treat each template as a multi-task deployment blueprint, with task tables aligned to the current deployment task structure.
- Added a lightweight More menu for each release template row with Clone, Edit, and Delete as template-maintenance entry points.
- Added a Create New Template modal with Manual Entry and Upload Excel tabs, reusing the current Excel-template download capability and creating local template drafts for frontend preview.
- Added local task authoring for template drafts, including Add Task, Edit, Delete, dependency maintenance, and automatic handoff from Manual Entry into the first task-creation step.
- Added Edit Template support by reusing the template-creation form for metadata maintenance from both the details header and the template-row More menu.
- Added working Clone behavior for template rows so a new local draft can be created with copied metadata and task definitions.
- Added a real Delete Template confirmation flow that removes local template drafts from the list and safely resets the selection state.
- Simplified template selection by making table rows switch the shared Template Details workspace directly and removing the redundant View Details button.
