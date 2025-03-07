package cz.lastaapps.api.domain.usecase

import cz.lastaapps.api.data.repo.ProcessingRepo

class RunJobsUC(
    private val repo: ProcessingRepo,
) {
    suspend operator fun invoke() {
        repo.requestNow()
    }
}
