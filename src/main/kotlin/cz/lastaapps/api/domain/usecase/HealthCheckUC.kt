package cz.lastaapps.api.domain.usecase

import cz.lastaapps.api.data.repo.ProcessingRepo

class HealthCheckUC(
    private val repo: ProcessingRepo,
) {
    operator fun invoke() = repo.didLastJobFinishOnTime()
}
