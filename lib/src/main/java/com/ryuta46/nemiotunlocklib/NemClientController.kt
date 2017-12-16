package com.ryuta46.nemiotunlocklib

import com.ryuta46.nemkotlin.account.AccountGenerator
import com.ryuta46.nemkotlin.client.RxNemApiClient
import com.ryuta46.nemkotlin.client.RxNemWebSocketClient
import com.ryuta46.nemkotlin.enums.TransactionType
import com.ryuta46.nemkotlin.enums.Version
import com.ryuta46.nemkotlin.model.Block
import com.ryuta46.nemkotlin.model.Mosaic
import com.ryuta46.nemkotlin.model.TransferTransaction
import com.ryuta46.nemkotlin.util.ConvertUtils
import com.ryuta46.nemkotlin.util.Logger
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import java.security.SecureRandom

class NemClientController(mainHosts: List<String>, private val logger: Logger) {
    data class Client(val rx: RxNemApiClient,
                      val webSocket: RxNemWebSocketClient)

    // Address -> Subscription
    private val subscriptions = mutableMapOf<String, (String, String, List<Mosaic>) -> Unit>()

    private val mainClients: List<Client> = mainHosts.map {
        Client(RxNemApiClient("http://$it:7890", logger = logger), RxNemWebSocketClient("http://$it:7778", logger = logger))
    }

    var mainHeight: Int = 0

    fun subscribeBlocks(callback:(Block) -> Unit = {}) {
        // Start monitoring
        mainClients.forEach {
            subscribeBlock(it, result = {
                notifyResult(it, Version.Main)
                callback(it)
            }) }
    }

    private fun subscribeBlock(client: Client, result:(Block) -> Unit = {}, fail:(String) -> Unit = {}) {
        client.webSocket.blocks()
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.newThread())
                .onErrorResumeNext { e: Throwable ->
                    // auto retry
                    logger.log(Logger.Level.Debug, "Retry WebSocket connection of blockHeight: ${e.message}")
                    subscribeBlock(client, result, fail)
                    Observable.empty()
                }
                .subscribe { result(it) }
        // do not unsubscribe
    }

    private fun notifyResult(block: Block, version: Version) {
        synchronized(this@NemClientController) {
            if (mainHeight >= block.height) {
                return
            }
            mainHeight = block.height
        }

        block.transactions.forEach {
            val transfer = it.asTransfer ?: it.asMultisig?.otherTrans?.asTransfer
            if (transfer != null) {
                notifyResult(transfer, version)
            }
        }
    }
    private fun notifyResult(transaction: TransferTransaction, version: Version) {
        if (transaction.type != TransactionType.Transfer.rawValue) {
            return
        }
        val sender = AccountGenerator.calculateAddress(ConvertUtils.toByteArray(transaction.signer), version)
        val recipient = transaction.recipient

        subscriptions[recipient]?.let { it(sender, transaction.message?.payload ?: "", transaction.mosaics) }

    }

    fun subscribeTransaction(address: String, result:(String, String, List<Mosaic>) -> Unit) {
        synchronized(this) {
            subscriptions.put(address, result)
        }
    }

    fun balance(address: String, result:(List<Mosaic>) -> Unit, fail:(String) -> Unit = {}, retryCount: Int = 10) {
        val client = selectClient()

        client.rx.accountMosaicOwned(address)
                .subscribeOn(Schedulers.io())
                .onErrorResumeNext { _: Throwable ->
                    if (retryCount > 0) {
                        balance(address, result, fail, retryCount - 1)
                    } else {
                        fail("$address\n failed.")
                    }
                    Observable.empty()
                }
                .subscribe { mosaics -> result(mosaics) }
    }

    private fun selectClient(): Client = mainClients[SecureRandom().nextInt(mainClients.size)]

}