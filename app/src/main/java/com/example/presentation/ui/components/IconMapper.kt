package com.example.presentation.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

object IconMapper {
    private val iconMap = mapOf(
        "restaurant" to Icons.Default.Restaurant,
        "directions_car" to Icons.Default.DirectionsCar,
        "shopping_bag" to Icons.Default.LocalMall,
        "sports_esports" to Icons.Default.SportsEsports,
        "receipt_long" to Icons.Default.ReceiptLong,
        "medical_services" to Icons.Default.MedicalServices,
        "category" to Icons.Default.Category,
        "payments" to Icons.Default.Payments,
        "trending_up" to Icons.Default.TrendingUp,
        "trending_down" to Icons.Default.TrendingDown,
        "storefront" to Icons.Default.Storefront,
        "account_balance" to Icons.Default.AccountBalance,
        "wallet" to Icons.Default.Wallet,
        "credit_card" to Icons.Default.CreditCard,
        "assessment" to Icons.Default.Assessment,
        "settings" to Icons.Default.Settings,
        "home" to Icons.Default.Home,
        "history" to Icons.Default.History,
        "person" to Icons.Default.Person,
        "school" to Icons.Default.School,
        "flight" to Icons.Default.Flight,
        "fitness_center" to Icons.Default.FitnessCenter,
        "spa" to Icons.Default.Spa,
        "local_hospital" to Icons.Default.LocalHospital,
        "work" to Icons.Default.Work
    )

    fun getIconByName(name: String): ImageVector {
        return iconMap[name.lowercase()] ?: Icons.Default.Category
    }

    fun getAllIconNames(): List<String> {
        return iconMap.keys.toList()
    }
}
