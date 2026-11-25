package com.example.data

import com.example.model.DhikrPreset
import com.example.model.GlobalCity
import com.example.model.Reciter

object GlobalCities {
    val cities: List<GlobalCity> = listOf(
        GlobalCity("Makkah", "Saudi Arabia", 21.4225, 39.8262, "Asia/Riyadh"),
        GlobalCity("Madinah", "Saudi Arabia", 24.4672, 39.6111, "Asia/Riyadh"),
        GlobalCity("Riyadh", "Saudi Arabia", 24.7136, 46.6753, "Asia/Riyadh"),
        GlobalCity("Jeddah", "Saudi Arabia", 21.4858, 39.1925, "Asia/Riyadh"),
        GlobalCity("Dubai", "United Arab Emirates", 25.2048, 55.2708, "Asia/Dubai"),
        GlobalCity("Abu Dhabi", "United Arab Emirates", 24.4539, 54.3773, "Asia/Dubai"),
        GlobalCity("Doha", "Qatar", 25.2854, 51.5310, "Asia/Qatar"),
        GlobalCity("Kuwait City", "Kuwait", 29.3759, 47.9774, "Asia/Kuwait"),
        GlobalCity("Muscat", "Oman", 23.5880, 58.3829, "Asia/Muscat"),
        GlobalCity("Amman", "Jordan", 31.9454, 35.9284, "Asia/Amman"),
        GlobalCity("Jerusalem", "Palestine", 31.7683, 35.2137, "Asia/Jerusalem"),
        GlobalCity("Cairo", "Egypt", 30.0444, 31.2357, "Africa/Cairo"),
        GlobalCity("Alexandria", "Egypt", 31.2001, 29.9187, "Africa/Cairo"),
        GlobalCity("Istanbul", "Turkey", 41.0082, 28.9784, "Europe/Istanbul"),
        GlobalCity("Ankara", "Turkey", 39.9334, 32.8597, "Europe/Istanbul"),
        GlobalCity("Casablanca", "Morocco", 33.5731, -7.5898, "Africa/Casablanca"),
        GlobalCity("Rabat", "Morocco", 34.0209, -6.8416, "Africa/Casablanca"),
        GlobalCity("Algiers", "Algeria", 36.7538, 3.0588, "Africa/Algiers"),
        GlobalCity("Tunis", "Tunisia", 36.8065, 10.1815, "Africa/Tunis"),
        GlobalCity("Lagos", "Nigeria", 6.5244, 3.3792, "Africa/Lagos"),
        GlobalCity("Nairobi", "Kenya", -1.2921, 36.8219, "Africa/Nairobi"),
        GlobalCity("Johannesburg", "South Africa", -26.2041, 28.0473, "Africa/Johannesburg"),
        GlobalCity("London", "United Kingdom", 51.5072, -0.1276, "Europe/London"),
        GlobalCity("Birmingham", "United Kingdom", 52.4862, -1.8904, "Europe/London"),
        GlobalCity("Manchester", "United Kingdom", 53.4808, -2.2426, "Europe/London"),
        GlobalCity("Paris", "France", 48.8566, 2.3522, "Europe/Paris"),
        GlobalCity("Berlin", "Germany", 52.5200, 13.4050, "Europe/Berlin"),
        GlobalCity("Amsterdam", "Netherlands", 52.3676, 4.9041, "Europe/Amsterdam"),
        GlobalCity("Madrid", "Spain", 40.4168, -3.7038, "Europe/Madrid"),
        GlobalCity("Rome", "Italy", 41.9028, 12.4964, "Europe/Rome"),
        GlobalCity("New York", "United States", 40.7128, -74.0060, "America/New_York"),
        GlobalCity("Chicago", "United States", 41.8781, -87.6298, "America/Chicago"),
        GlobalCity("Houston", "United States", 29.7604, -95.3698, "America/Chicago"),
        GlobalCity("Los Angeles", "United States", 34.0522, -118.2437, "America/Los_Angeles"),
        GlobalCity("San Francisco", "United States", 37.7749, -122.4194, "America/Los_Angeles"),
        GlobalCity("Toronto", "Canada", 43.6532, -79.3832, "America/Toronto"),
        GlobalCity("Montreal", "Canada", 45.5017, -73.5673, "America/Toronto"),
        GlobalCity("Karachi", "Pakistan", 24.8607, 67.0011, "Asia/Karachi"),
        GlobalCity("Lahore", "Pakistan", 31.5204, 74.3587, "Asia/Karachi"),
        GlobalCity("Islamabad", "Pakistan", 33.6844, 73.0479, "Asia/Karachi"),
        GlobalCity("Dhaka", "Bangladesh", 23.8103, 90.4125, "Asia/Dhaka"),
        GlobalCity("New Delhi", "India", 28.6139, 77.2090, "Asia/Kolkata"),
        GlobalCity("Mumbai", "India", 19.0760, 72.8777, "Asia/Kolkata"),
        GlobalCity("Hyderabad", "India", 17.3850, 78.4867, "Asia/Kolkata"),
        GlobalCity("Jakarta", "Indonesia", -6.2088, 106.8456, "Asia/Jakarta"),
        GlobalCity("Bandung", "Indonesia", -6.9175, 107.6191, "Asia/Jakarta"),
        GlobalCity("Kuala Lumpur", "Malaysia", 3.1390, 101.6869, "Asia/Kuala_Lumpur"),
        GlobalCity("Singapore", "Singapore", 1.3521, 103.8198, "Asia/Singapore"),
        GlobalCity("Tokyo", "Japan", 35.6762, 139.6503, "Asia/Tokyo"),
        GlobalCity("Seoul", "South Korea", 37.5665, 126.9780, "Asia/Seoul"),
        GlobalCity("Sydney", "Australia", -33.8688, 151.2093, "Australia/Sydney"),
        GlobalCity("Melbourne", "Australia", -37.8136, 144.9631, "Australia/Melbourne")
    )

    fun defaultForTimeZone(tzId: String): GlobalCity {
        return cities.firstOrNull { it.timeZoneId == tzId } ?: cities.first()
    }
}

object QuranReciters {
    val list: List<Reciter> = listOf(
        Reciter("ar.alafasy", "Mishary Rashid Alafasy", "Murattal • Kuwait"),
        Reciter("ar.abdulbasitmurattal", "Abdul Basit Abdus Samad", "Murattal • Egypt"),
        Reciter("ar.mahermuaiqly", "Maher Al-Muaiqly", "Haramain • Makkah"),
        Reciter("ar.husary", "Mahmoud Khalil Al-Husary", "Classic Tajweed • Egypt"),
        Reciter("ar.minshawi", "Muhammad Siddiq Al-Minshawi", "Murattal • Egypt")
    )
}

object DefaultDhikrPresets {
    val list: List<DhikrPreset> = listOf(
        DhikrPreset(
            id = "subhanallah",
            arabic = "سُبْحَانَ اللَّهِ",
            transliteration = "SubhanAllah",
            meaning = "Glory be to Allah",
            targetCount = 33
        ),
        DhikrPreset(
            id = "alhamdulillah",
            arabic = "الْحَمْدُ لِلَّهِ",
            transliteration = "Alhamdulillah",
            meaning = "All praise is due to Allah",
            targetCount = 33
        ),
        DhikrPreset(
            id = "allahuakbar",
            arabic = "اللَّهُ أَكْبَرُ",
            transliteration = "Allahu Akbar",
            meaning = "Allah is the Greatest",
            targetCount = 33
        ),
        DhikrPreset(
            id = "tahlil",
            arabic = "لَا إِلَٰهَ إِلَّا اللَّهُ",
            transliteration = "La ilaha illallah",
            meaning = "There is no deity worthy of worship except Allah",
            targetCount = 100
        ),
        DhikrPreset(
            id = "istighfar",
            arabic = "أَسْتَغْفِرُ اللَّهَ الْعَظِيمَ",
            transliteration = "Astaghfirullah al-Azim",
            meaning = "I seek forgiveness from Allah, the Almighty",
            targetCount = 100
        ),
        DhikrPreset(
            id = "salawat",
            arabic = "اللَّهُمَّ صَلِّ عَلَىٰ مُحَمَّدٍ",
            transliteration = "Allahumma salli 'ala Muhammad",
            meaning = "O Allah, send peace and blessings upon Muhammad",
            targetCount = 100
        )
    )
}
