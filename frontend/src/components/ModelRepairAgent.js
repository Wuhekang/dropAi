export function repairModel(project = {}, failedGroup, quality = {}) {
  if (failedGroup?.userData) {
    failedGroup.userData = {
      ...failedGroup.userData,
      repairSkipped: true,
      repairReason: quality.code || 'PREVIEW_NOT_READY',
      backendStage: project.stage || project.generationStage || ''
    }
  }
  return failedGroup
}
