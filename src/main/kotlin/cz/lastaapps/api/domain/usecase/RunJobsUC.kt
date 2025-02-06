package cz.lastaapps.api.domain.usecase

import cz.lastaapps.api.data.PostsRepo

class RunJobsUC(
    private val repo: PostsRepo,
) {
    suspend operator fun invoke() {
        repo.requestNow()
    }
}
