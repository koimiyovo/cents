package com.kyovo.cents.domain.port.input

interface ImportDataUseCase
{
    suspend fun import(text: String)
}