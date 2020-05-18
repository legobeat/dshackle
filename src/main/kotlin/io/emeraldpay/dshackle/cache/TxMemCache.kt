/**
 * Copyright (c) 2020 EmeraldPay, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.emeraldpay.dshackle.cache

import io.emeraldpay.dshackle.data.BlockContainer
import io.emeraldpay.dshackle.data.BlockId
import io.emeraldpay.dshackle.data.TxContainer
import io.emeraldpay.dshackle.data.TxId
import io.emeraldpay.dshackle.reader.Reader
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Memory cache for transactions
 */
open class TxMemCache(
        // usually there is 100-150 tx per block on Ethereum, we keep data for about 32 blocks by default
        private val maxSize: Int = 125 * 32
) : Reader<TxId, TxContainer> {

    companion object {
        private val log = LoggerFactory.getLogger(TxMemCache::class.java)
    }

    private val mapping = ConcurrentHashMap<TxId, TxContainer>()
    private val queue = ConcurrentLinkedQueue<TxId>()

    override fun read(key: TxId): Mono<TxContainer> {
        return Mono.justOrEmpty(mapping[key])
    }

    open fun evict(block: BlockContainer) {
        block.transactions.forEach {
            mapping.remove(it)
        }
    }

    open fun evict(block: BlockId) {
        val ids = mapping.filter { it.value.blockId == block }
        ids.forEach {
            mapping.remove(it.key)
        }
    }

    open fun add(tx: TxContainer) {
        //do not cache fresh transactions
        if (tx.blockId == null) {
            return
        }
        mapping.put(tx.hash, tx)
        queue.add(tx.hash)

        while (queue.size > maxSize) {
            val old = queue.remove()
            mapping.remove(old)
        }
    }

}