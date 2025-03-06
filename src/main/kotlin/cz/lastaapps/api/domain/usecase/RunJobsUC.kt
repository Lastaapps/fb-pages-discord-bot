package cz.lastaapps.api.domain.usecase

import cz.lastaapps.api.data.repo.PostsRepo

class RunJobsUC(
    private val repo: PostsRepo,
) {
    suspend operator fun invoke() {
        repo.requestNow()
    }
}
