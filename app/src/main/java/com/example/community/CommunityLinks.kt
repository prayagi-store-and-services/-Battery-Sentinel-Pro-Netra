package com.example.community

/** Fixed public links. No device identifiers or automatic network requests. */
object CommunityLinks {
    data class Link(val label: String, val icon: String, val url: String)
    val social = listOf(
        Link("YouTube - subscribe", "▶", "https://www.youtube.com/@NetraEcosystem"),
        Link("Instagram - follow", "◎", "https://www.instagram.com/netraecosystem/"),
        Link("Facebook - follow", "f", "https://www.facebook.com/netraecosystem"),
        Link("X - follow", "X", "https://x.com/netraecosystem"),
        Link("Telegram - join", "➤", "https://t.me/Netraecosystem")
    )
    const val WEBSITE = "https://prayagi-store-and-services.github.io/-Battery-Sentinel-Pro-Netra/"
    const val CONTACT = "https://prayagi-store-and-services.github.io/netra-eco/contact.html?app=battery-sentinel"
}
