package com.kyovo.cents.ui.home

import com.kyovo.cents.domain.model.Account

/**
 * Whether an account's page offers its "+ Transaction" button. An archived account takes no new
 * transaction (a domain rule), so there is no button rather than a button leading to an error; an
 * account that is gone (deleted while its page was open) has none either.
 */
internal fun canAddTransactionTo(account: Account?): Boolean = account != null && account.archivedAt == null
