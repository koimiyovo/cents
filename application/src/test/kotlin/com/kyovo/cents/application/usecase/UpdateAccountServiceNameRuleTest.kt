package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.domain.exception.DuplicateAccountNameException
import com.kyovo.cents.domain.model.AccountDescription
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.port.input.UpdateAccountCommand
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * The naming rule when renaming, the same one as when opening or unarchiving an account: **only
 * active accounts have to be unique**. An archived account's name is free to reuse, and an
 * archived account holds no claim on it — whether it is the one being renamed or another.
 */
class UpdateAccountServiceNameRuleTest
{
    private val archivedAt = anInstant("2026-01-01T00:00:00Z")
    private val id = anAccountId()
    private val otherId = anAccountId("22222222-2222-2222-2222-222222222222")
    private val thirdId = anAccountId("33333333-3333-3333-3333-333333333333")

    private fun renameTo(name: String, accountId: AccountId = id) =
        UpdateAccountCommand(accountId, AccountName(name), AccountType.CHECKING, description = null)

    @Test
    fun `allows renaming an account to a name only an archived account holds`()
    {
        // GIVEN
        val repository = InMemoryAccountRepository()
        repository.save(anAccount(id = id, name = AccountName("Livret A")))
        repository.save(anAccount(id = otherId, name = AccountName("Compte courant"), archivedAt = archivedAt))
        val service = UpdateAccountService(repository)

        // WHEN
        val result = service.update(renameTo("Compte courant"))

        // THEN
        assertThat(result.name).isEqualTo(AccountName("Compte courant"))
        assertThat(repository.findById(id)).isEqualTo(result)
    }

    @Test
    fun `still refuses a name held by an active account when an archived account holds it too`()
    {
        // GIVEN the archived twin must not hide the active one
        val repository = InMemoryAccountRepository()
        repository.save(anAccount(id = id, name = AccountName("Livret A")))
        repository.save(anAccount(id = otherId, name = AccountName("Compte courant"), archivedAt = archivedAt))
        repository.save(anAccount(id = thirdId, name = AccountName("Compte courant")))
        val service = UpdateAccountService(repository)

        // WHEN / THEN
        assertThatThrownBy { service.update(renameTo("Compte courant")) }
            .isInstanceOf(DuplicateAccountNameException::class.java)
    }

    @ParameterizedTest
    @ValueSource(strings = ["Compte courant", "compte courant", "  COMPTE COURANT  "])
    fun `refuses any case or spacing variant of an active account's name`(variant: String)
    {
        // GIVEN
        val repository = InMemoryAccountRepository()
        repository.save(anAccount(id = id, name = AccountName("Livret A")))
        repository.save(anAccount(id = otherId, name = AccountName("Compte courant")))
        val service = UpdateAccountService(repository)

        // WHEN / THEN
        assertThatThrownBy { service.update(renameTo(variant)) }
            .isInstanceOf(DuplicateAccountNameException::class.java)
    }

    @Test
    fun `refuses renaming an archived account to a name held by an active account`()
    {
        // GIVEN it would only create a conflict waiting to happen at unarchiving time
        val repository = InMemoryAccountRepository()
        repository.save(anAccount(id = id, name = AccountName("Livret A"), archivedAt = archivedAt))
        repository.save(anAccount(id = otherId, name = AccountName("Compte courant")))
        val service = UpdateAccountService(repository)

        // WHEN / THEN
        assertThatThrownBy { service.update(renameTo("Compte courant")) }
            .isInstanceOf(DuplicateAccountNameException::class.java)
    }

    @Test
    fun `allows renaming an archived account to a name only other archived accounts hold`()
    {
        // GIVEN
        val repository = InMemoryAccountRepository()
        repository.save(anAccount(id = id, name = AccountName("Livret A"), archivedAt = archivedAt))
        repository.save(anAccount(id = otherId, name = AccountName("Compte courant"), archivedAt = archivedAt))
        val service = UpdateAccountService(repository)

        // WHEN
        val result = service.update(renameTo("Compte courant"))

        // THEN
        assertThat(result.name).isEqualTo(AccountName("Compte courant"))
    }

    @Test
    fun `lets an archived account keep its own name when only its description changes`()
    {
        // GIVEN
        val repository = InMemoryAccountRepository()
        repository.save(anAccount(id = id, name = AccountName("Livret A"), archivedAt = archivedAt))
        val service = UpdateAccountService(repository)
        val command = UpdateAccountCommand(
            id = id,
            name = AccountName("Livret A"),
            type = AccountType.CHECKING,
            description = AccountDescription.of("Clos en 2026")
        )

        // WHEN
        val result = service.update(command)

        // THEN
        assertThat(result.description).isEqualTo(AccountDescription.of("Clos en 2026"))
    }

    @Test
    fun `renaming an archived account leaves it archived`()
    {
        // GIVEN
        val repository = InMemoryAccountRepository()
        repository.save(anAccount(id = id, name = AccountName("Livret A"), archivedAt = archivedAt))
        val service = UpdateAccountService(repository)

        // WHEN
        val result = service.update(renameTo("Livret B"))

        // THEN
        assertThat(result.archivedAt).isEqualTo(archivedAt)
        assertThat(repository.findById(id)!!.archivedAt).isEqualTo(archivedAt)
    }
}
