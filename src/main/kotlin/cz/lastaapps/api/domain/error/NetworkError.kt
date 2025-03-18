/*
 *    Copyright 2024, Petr Laštovička as Lasta apps, All rights reserved
 *
 *     This file is part of Menza.
 *
 *     Menza is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Menza is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Menza.  If not, see <https://www.gnu.org/licenses/>.
 */

package cz.lastaapps.api.domain.error

import cz.lastaapps.api.data.model.FBError
import io.ktor.http.HttpStatusCode

sealed interface NetworkError : DomainError {
    data object Timeout : NetworkError

    data object NoInternet : NetworkError

    data object ConnectionClosed : NetworkError

    data class SerializationError(
        override val throwable: Throwable,
        val responseBody: String? = null,
    ) : NetworkError

    data class FBAPIError(
        val httpCode: HttpStatusCode,
        // yes, I'm using a data model in domain layer
        val error: FBError,
    ) : NetworkError {
        val isRateLimit get() = error.code == 4
    }
}
