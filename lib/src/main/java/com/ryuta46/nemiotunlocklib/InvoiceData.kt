package com.ryuta46.nemiotunlocklib

data class InvoiceData(
        private val name: String,
        private val addr: String,
        private val amount: Long,
        private val msg: String
)

data class InvoiceContainer(
        private val v: Int = 2,
        private val type: Int = 2,
        private val data: InvoiceData
)