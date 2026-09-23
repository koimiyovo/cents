package com.kyovo.cents.ui.transaction

import com.kyovo.cents.domain.model.AccountId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.uuid.Uuid

class AmountInputFilterTest
{
    @ParameterizedTest
    @ValueSource(strings = ["", "1", "12", "12,", "12.", "12,5", "12,50", "12.50", "0,5", "123456789"])
    fun `accepts every state of a well-formed amount while it is being typed`(text: String)
    {
        assertThat(acceptsAmountInput(text)).isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["a", "12a", "-5", "12,505", "1,2,3", "12 €", " 12", "12 ", ",5", "1234567890"])
    fun `rejects letters, signs, spaces, a second separator, a third decimal or too many digits`(text: String)
    {
        assertThat(acceptsAmountInput(text)).isFalse()
    }

    @ParameterizedTest
    @ValueSource(strings = ["12,", "12.", "0,5", "7"])
    fun `every non-zero state the filter accepts is also an amount that can be saved`(text: String)
    {
        // The field must never let the user type something the form then refuses as malformed:
        // "12," (a trailing separator) counts as 12.00.
        assertThat(acceptsAmountInput(text)).isTrue()
        assertThat(parseAmountToCents(text)).isNotNull()
    }
}

class TransactionFormDayTest
{
    private val paris = ZoneId.of("Europe/Paris")
    private val now = Instant.parse("2026-09-23T14:30:00Z") // 16:30 in Paris
    private val today = LocalDate.of(2026, 9, 23)

    private fun aForm(date: Instant = now) = TransactionFormState(
        type = TransactionFormType.EXPENSE,
        accountId = AccountId(Uuid.random()),
        date = date,
    )

    @Test
    fun `day is the calendar day in the given zone`()
    {
        // 23:30 UTC on the 23rd is already the 24th in Paris
        val form = aForm(date = Instant.parse("2026-09-23T23:30:00Z"))

        assertThat(form.day(ZoneId.of("UTC"))).isEqualTo(LocalDate.of(2026, 9, 23))
        assertThat(form.day(paris)).isEqualTo(LocalDate.of(2026, 9, 24))
    }

    @Test
    fun `choosing today keeps the exact current instant`()
    {
        val form = aForm().withDay(today, now, paris)

        assertThat(form.date).isEqualTo(now)
    }

    @Test
    fun `choosing another day keeps the current time of day`()
    {
        val form = aForm().withDay(today.minusDays(1), now, paris)

        assertThat(form.date).isEqualTo(Instant.parse("2026-09-22T14:30:00Z"))
        assertThat(form.day(paris)).isEqualTo(today.minusDays(1))
    }

    @Test
    fun `saving refreshes a form still dated today to the moment of saving`()
    {
        val openedAt = Instant.parse("2026-09-23T10:00:00Z")
        val savedAt = Instant.parse("2026-09-23T14:30:00Z")

        val stamped = aForm(date = openedAt).stampedAt(savedAt, paris)

        assertThat(stamped.date).isEqualTo(savedAt)
    }

    @Test
    fun `saving leaves a day picked on purpose alone`()
    {
        val picked = Instant.parse("2026-09-01T09:15:00Z")

        val stamped = aForm(date = picked).stampedAt(now, paris)

        assertThat(stamped.date).isEqualTo(picked)
    }
}
