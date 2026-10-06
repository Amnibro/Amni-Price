package com.amniscient.price.domain

enum class Category(val label: String) {
    PRODUCE("Produce"),
    DAIRY("Dairy & eggs"),
    MEAT("Meat & seafood"),
    BAKERY("Bakery"),
    PANTRY("Pantry"),
    FROZEN("Frozen"),
    SNACKS("Snacks"),
    BEVERAGES("Beverages"),
    HOUSEHOLD("Household"),
    PERSONAL("Personal care"),
    BABY_PET("Baby & pet"),
    MEALS("Meals & menu"),
    OTHER("Other"),
}

/**
 * Keyword-based auto-categorization. Longer phrases win over single words, so
 * "peanut butter" lands in Pantry even though "butter" is Dairy. Add keywords freely.
 */
object Categorizer {
    private val rules: Map<Category, List<String>> = mapOf(
        Category.FROZEN to listOf("frozen", "ice cream", "popsicle", "gelato", "sorbet", "frozen pizza", "waffles"),
        Category.BABY_PET to listOf(
            "diaper", "diapers", "wipes", "formula", "baby", "dog", "cat", "pet", "kibble", "litter", "puppy", "kitten",
        ),
        Category.HOUSEHOLD to listOf(
            "detergent", "bleach", "paper towel", "paper towels", "toilet paper", "tissue", "trash bag", "trash bags",
            "foil", "dish soap", "cleaner", "sponge", "batteries", "light bulb", "napkins", "laundry", "softener",
        ),
        Category.PERSONAL to listOf(
            "shampoo", "conditioner", "toothpaste", "toothbrush", "deodorant", "body wash", "lotion", "razor",
            "soap", "floss", "sunscreen", "vitamins", "ibuprofen", "acetaminophen", "mouthwash",
        ),
        Category.BEVERAGES to listOf(
            "water", "soda", "cola", "juice", "coffee", "tea", "sparkling", "lemonade", "beer", "wine",
            "energy drink", "kombucha", "seltzer", "gatorade",
        ),
        Category.DAIRY to listOf(
            "milk", "cheese", "yogurt", "butter", "eggs", "egg", "cream", "sour cream", "cottage", "creamer",
            "mozzarella", "cheddar", "parmesan", "almond milk", "oat milk",
        ),
        Category.MEAT to listOf(
            "chicken", "beef", "pork", "turkey", "bacon", "sausage", "ham", "steak", "salmon", "tuna", "shrimp",
            "fish", "ground beef", "lamb", "hot dogs", "jerky",
        ),
        Category.PRODUCE to listOf(
            "apple", "apples", "banana", "bananas", "orange", "oranges", "lettuce", "tomato", "tomatoes", "potato",
            "potatoes", "onion", "onions", "carrot", "carrots", "avocado", "avocados", "grapes", "berries",
            "strawberries", "blueberries", "spinach", "broccoli", "pepper", "peppers", "cucumber", "lemon", "lemons",
            "lime", "limes", "celery", "garlic", "mushrooms", "salad", "kale",
        ),
        Category.BAKERY to listOf(
            "bread", "bagel", "bagels", "muffin", "muffins", "tortilla", "tortillas", "buns", "rolls", "croissant",
            "cake", "donut", "donuts", "baguette", "pita",
        ),
        Category.SNACKS to listOf(
            "chips", "cookies", "cookie", "crackers", "pretzels", "popcorn", "candy", "chocolate", "granola bar",
            "nuts", "almonds", "trail mix", "oreo", "doritos",
        ),
        Category.PANTRY to listOf(
            "rice", "pasta", "spaghetti", "flour", "sugar", "salt", "oil", "olive oil", "cereal", "oats", "oatmeal",
            "beans", "soup", "sauce", "ketchup", "mustard", "mayo", "mayonnaise", "peanut butter", "jelly", "jam",
            "honey", "syrup", "spice", "vinegar", "noodles", "canned", "broth", "stock",
        ),
    )

    private val abbreviations: Map<Category, List<String>> = mapOf(
        Category.DAIRY to listOf("mlk", "chs", "ygrt", "yog", "yogrt", "btr", "bttr", "crm", "eggs", "lg eggs", "lrg eggs", "chdr", "mozz", "parm"),
        Category.MEAT to listOf("chkn", "chk", "brst", "grnd bf", "grd bf", "prk", "sausg", "bcn", "trky", "slmn", "shrmp"),
        Category.PRODUCE to listOf("bnna", "bnnas", "tmto", "tmtos", "ptto", "ptts", "onin", "letc", "avcdo", "strwb", "blubry", "spnch", "brcli", "org bnna"),
        Category.BAKERY to listOf("brd", "wht brd", "wht bread", "bgl", "bgls", "tortla", "tort"),
        Category.PANTRY to listOf("crl", "pnut btr", "pst", "spag", "sce", "snce"),
        Category.BEVERAGES to listOf("wtr", "spkl", "jce", "coff"),
        Category.SNACKS to listOf("chps", "ckies", "crkrs", "choc"),
        Category.HOUSEHOLD to listOf("pprtwl", "ppr twl", "dtrgnt"),
    )
    private val phrases: List<Pair<String, Category>> =
        (rules.flatMap { (cat, words) -> words.map { it to cat } } + abbreviations.flatMap { (cat, words) -> words.map { it to cat } })
            .sortedByDescending { (phrase, _) -> phrase.count { it == ' ' } }

    fun categorize(name: String): Category {
        val padded = " " + normalizeName(name) + " "
        return phrases.firstOrNull { (phrase, _) -> padded.contains(" $phrase ") }?.second ?: Category.OTHER
    }
}
