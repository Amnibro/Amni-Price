package com.amniscient.price.domain

/** One line of a shopping list. [key] identifies the list row; [productId] is null for unmatched free text. */
data class TripItem(val key: Long, val productId: Long?, val quantity: Int = 1)

data class StoreOption(val storeId: Long, val totalCents: Long, val covered: Int)

data class Assignment(val itemKey: Long, val storeId: Long, val unitCents: Long, val quantity: Int)

data class SplitPlan(
    val storeIds: List<Long>,
    val totalCents: Long,
    val covered: Int,
    val assignments: List<Assignment>,
)

data class TripPlan(
    /** Every store, best first: most items available, then lowest total. */
    val singleStore: List<StoreOption>,
    /** Cheapest way to buy everything that has a price using at most `maxStores` stores. */
    val best: SplitPlan?,
    /** List rows with no known price anywhere. */
    val unpriced: List<Long>,
    /** What the best plan saves vs. paying the highest known price for each item. */
    val savingsVsHighest: Long,
    val pricedCount: Int,
)

object TripPlanner {
    /**
     * @param prices productId → (storeId → latest price in cents)
     */
    fun plan(items: List<TripItem>, prices: Map<Long, Map<Long, Long>>, maxStores: Int = 2): TripPlan {
        val priced = items.filter { it.productId != null && !prices[it.productId].isNullOrEmpty() }
        val unpriced = items.filterNot { it in priced }.map { it.key }
        val storeIds = priced.flatMap { prices.getValue(it.productId!!).keys }.distinct()

        val single = storeIds.map { store ->
            var total = 0L
            var covered = 0
            priced.forEach { item ->
                prices.getValue(item.productId!!)[store]?.let { total += it * item.quantity; covered++ }
            }
            StoreOption(store, total, covered)
        }.sortedWith(compareByDescending<StoreOption> { it.covered }.thenBy { it.totalCents })

        // Exhaustive search over store combinations, pruned to the 10 best-covering stores.
        val candidates = single.take(10).map { it.storeId }
        var best: SplitPlan? = null
        for (combo in combinations(candidates, maxStores)) {
            val assignments = priced.mapNotNull { item ->
                val options = prices.getValue(item.productId!!).filterKeys { it in combo }
                options.minByOrNull { it.value }?.let { (store, cents) -> Assignment(item.key, store, cents, item.quantity) }
            }
            val used = assignments.map { it.storeId }.distinct()
            val plan = SplitPlan(used, assignments.sumOf { it.unitCents * it.quantity }, assignments.size, assignments)
            if (best == null || better(plan, best)) best = plan
        }

        val savings = best?.assignments?.sumOf { a ->
            val item = priced.first { it.key == a.itemKey }
            (prices.getValue(item.productId!!).values.max() - a.unitCents) * a.quantity
        } ?: 0L

        return TripPlan(single, best, unpriced, savings, priced.size)
    }

    private fun better(a: SplitPlan, b: SplitPlan): Boolean = when {
        a.covered != b.covered -> a.covered > b.covered
        a.totalCents != b.totalCents -> a.totalCents < b.totalCents
        else -> a.storeIds.size < b.storeIds.size
    }

    private fun combinations(ids: List<Long>, maxSize: Int): Sequence<List<Long>> = sequence {
        for (size in 1..minOf(maxSize, ids.size)) {
            yieldAll(combos(ids, size, 0))
        }
    }

    private fun combos(ids: List<Long>, size: Int, start: Int): Sequence<List<Long>> = sequence {
        if (size == 0) {
            yield(emptyList())
            return@sequence
        }
        for (i in start..ids.size - size) {
            combos(ids, size - 1, i + 1).forEach { yield(listOf(ids[i]) + it) }
        }
    }
}
