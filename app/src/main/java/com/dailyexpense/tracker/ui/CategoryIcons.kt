package com.dailyexpense.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * An icon a type can use. [key] is what gets stored in the database, so never rename a key.
 * [color] is the circle colour a type gets when this icon is picked.
 */
data class TypeIcon(val key: String, val label: String, val vector: ImageVector, val color: Long)

/** Every icon offered in the picker, in display order. */
val TYPE_ICONS: List<TypeIcon> = listOf(
    TypeIcon("food", "Food", Icons.Filled.Restaurant, 0xFFE57373),
    TypeIcon("grocery", "Grocery", Icons.Filled.LocalGroceryStore, 0xFF81C784),
    TypeIcon("cafe", "Cafe", Icons.Filled.LocalCafe, 0xFFA1887F),
    TypeIcon("transport", "Transport", Icons.Filled.DirectionsCar, 0xFF64B5F6),
    TypeIcon("fuel", "Fuel", Icons.Filled.LocalGasStation, 0xFFFF8A65),
    TypeIcon("flight", "Travel", Icons.Filled.Flight, 0xFF4FC3F7),
    TypeIcon("shopping", "Shopping", Icons.Filled.ShoppingBag, 0xFFBA68C8),
    TypeIcon("fashion", "Clothes", Icons.Filled.Checkroom, 0xFFF06292),
    TypeIcon("bills", "Bills", Icons.AutoMirrored.Filled.ReceiptLong, 0xFFFFB74D),
    TypeIcon("electricity", "Electricity", Icons.Filled.Bolt, 0xFFFFD54F),
    TypeIcon("internet", "Internet", Icons.Filled.Wifi, 0xFF4DD0E1),
    TypeIcon("mobile", "Mobile", Icons.Filled.PhoneAndroid, 0xFF7986CB),
    TypeIcon("telephone", "Telephone", Icons.Filled.Call, 0xFFA1887F),
    TypeIcon("home", "Home", Icons.Filled.Home, 0xFF8D6E63),
    TypeIcon("entertainment", "Entertainment", Icons.Filled.Movie, 0xFF4DB6AC),
    TypeIcon("social", "Social", Icons.Filled.Groups, 0xFF4FC3F7),
    TypeIcon("health", "Health", Icons.Filled.LocalHospital, 0xFF81C784),
    TypeIcon("fitness", "Fitness", Icons.Filled.FitnessCenter, 0xFFFF8A65),
    TypeIcon("baby", "Baby", Icons.Filled.ChildCare, 0xFFF06292),
    TypeIcon("school", "School", Icons.Filled.Backpack, 0xFFFFB74D),
    TypeIcon("education", "Education", Icons.Filled.School, 0xFF9575CD),
    TypeIcon("office", "Office", Icons.Filled.Work, 0xFF7986CB),
    TypeIcon("gift", "Gifts", Icons.Filled.CardGiftcard, 0xFFE57373),
    TypeIcon("pets", "Pets", Icons.Filled.Pets, 0xFFA1887F),
    TypeIcon("salary", "Salary", Icons.Filled.Payments, 0xFF4FC3F7),
    TypeIcon("refund", "Refunds", Icons.Filled.CurrencyExchange, 0xFF4DB6AC),
    TypeIcon("investment", "Investment", Icons.AutoMirrored.Filled.TrendingUp, 0xFF66BB6A),
    TypeIcon("savings", "Savings", Icons.Filled.Savings, 0xFFF48FB1),
    // Generic icons for anything else
    TypeIcon("star", "Star", Icons.Filled.Star, 0xFFFFCA28),
    TypeIcon("tag", "Tag", Icons.Filled.LocalOffer, 0xFF9575CD),
    TypeIcon("heart", "Heart", Icons.Filled.Favorite, 0xFFEF5350),
    TypeIcon("wallet", "Wallet", Icons.Filled.AccountBalanceWallet, 0xFF26A69A),
    TypeIcon("other", "Other", Icons.Filled.MoreHoriz, 0xFF90A4AE),
)

private val ICONS_BY_KEY = TYPE_ICONS.associateBy { it.key }

/**
 * Name words (lower-case) that suggest an icon, for types with no icon chosen yet
 * (e.g. the starter types). A name matches if it equals or starts with one of the words.
 */
private val NAME_HINTS: List<Pair<List<String>, String>> = listOf(
    listOf("food", "dining", "restaurant", "eating out", "lunch", "dinner", "breakfast", "snacks") to "food",
    listOf("grocery", "groceries", "vegetables", "supermarket") to "grocery",
    listOf("coffee", "cafe", "tea") to "cafe",
    listOf("transport", "travel", "commute", "cab", "taxi", "auto", "car", "bus", "metro", "train") to "transport",
    listOf("fuel", "petrol", "diesel", "gas") to "fuel",
    listOf("flight", "trip", "vacation", "holiday") to "flight",
    listOf("shopping", "clothes", "clothing") to "shopping",
    listOf("fashion", "apparel") to "fashion",
    listOf("bills", "bill", "utilities", "emi") to "bills",
    listOf("electricity", "power") to "electricity",
    listOf("internet", "wifi", "broadband") to "internet",
    listOf("mobile", "phone", "recharge") to "mobile",
    listOf("telephone", "landline") to "telephone",
    listOf("rent", "home", "house", "housing", "maintenance") to "home",
    listOf("entertainment", "movies", "movie", "fun", "subscriptions", "ott") to "entertainment",
    listOf("social", "friends", "party", "outing") to "social",
    listOf("health", "medical", "medicine", "doctor", "hospital", "pharmacy") to "health",
    listOf("fitness", "gym", "sports") to "fitness",
    listOf("school", "kids school", "tuition") to "school",
    listOf("education", "college", "books", "course", "fees") to "education",
    listOf("baby", "kids", "child", "children", "diapers") to "baby",
    listOf("office", "work", "business") to "office",
    listOf("gift", "gifts", "donation", "charity") to "gift",
    listOf("pets", "pet") to "pets",
    listOf("salary", "income", "wages", "bonus", "freelance") to "salary",
    listOf("refund", "refunds", "cashback", "reimbursement") to "refund",
    listOf("investment", "investments", "stocks", "mutual funds", "dividend", "interest") to "investment",
    listOf("savings", "saving") to "savings",
    listOf("other", "others", "misc", "miscellaneous") to "other",
)

/** Icon suggested by a type's name, or null if the name isn't recognised. */
fun suggestIconForName(name: String?): TypeIcon? {
    val n = name?.trim()?.lowercase() ?: return null
    if (n.isEmpty()) return null
    val key = NAME_HINTS.firstOrNull { (words, _) ->
        words.any { w -> n == w || n.startsWith("$w ") || n.startsWith("$w&") || n.startsWith("$w/") || n.startsWith("$w,") }
    }?.second
    return key?.let { ICONS_BY_KEY[it] }
}

/** The icon to show for a type: the one picked for it, otherwise one suggested by its name. */
fun iconFor(iconKey: String?, name: String?): TypeIcon? =
    iconKey?.let { ICONS_BY_KEY[it] } ?: suggestIconForName(name)

/** A white icon on a circle of [color]. */
@Composable
fun IconCircle(icon: ImageVector, color: Long, size: Dp) {
    Box(Modifier.size(size).background(Color(color), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.58f))
    }
}

/**
 * Shows a type's icon on a circle in the type's colour. A type with no icon (and "Uncategorized")
 * shows a coloured dot, centred in the same space so list rows stay aligned.
 */
@Composable
fun CategoryBadge(name: String?, iconKey: String?, color: Long?, size: Dp = 36.dp) {
    val bg = color ?: 0xFFBDBDBD
    val icon = iconFor(iconKey, name)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (icon != null) IconCircle(icon.vector, bg, size)
        else Box(Modifier.size(size * 0.4f).background(Color(bg), CircleShape))
    }
}
