package cz.lastaapps.api.domain.usecase

import arrow.core.right
import cz.lastaapps.api.data.Repository
import cz.lastaapps.api.domain.error.Outcome
import cz.lastaapps.api.domain.model.Page
import cz.lastaapps.api.domain.model.toPages

class GetAuthorizedPagesUC(
    private val repo: Repository,
) {
    suspend operator fun invoke(): Outcome<List<Page>> = repo.loadAuthorizedPages().toPages().right()
}
