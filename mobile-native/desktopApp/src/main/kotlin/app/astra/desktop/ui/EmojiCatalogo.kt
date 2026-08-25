package app.astra.desktop.ui

import java.util.Locale

data class CategoriaEmoji(val id: String, val nome: String, val atalho: String, val itens: List<String>)

private fun cat(id: String, nome: String, atalho: String, glifos: String) =
    CategoriaEmoji(id, nome, atalho, glifos.trim().split(Regex("\\s+")).filter { it.isNotBlank() })

val CATEGORIAS_EMOJI: List<CategoriaEmoji> = listOf(
    cat("rostos", "rostos", "😀", """
        😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 🫠 😉 😊 😇 🥰 😍 🤩 😘 😗 😚 😙 🥲
        😋 😛 😜 🤪 😝 🤑 🤗 🤭 🫢 🫣 🤫 🤔 🫡 🤐 🤨 😐 😑 😶 🫥 😏 😒 🙄
        😬 🫨 🤥 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🤧 🥵 🥶 🥴 😵 🤯 🤠 🥳 🥸
        😎 🤓 🧐 😕 🫤 😟 🙁 😮 😯 😲 😳 🥺 🥹 😦 😧 😨 😰 😥 😢 😭 😱 😖
        😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿 💀 ☠️ 💩 🤡 👹 👺 👻 👽 👾 🤖
        😺 😸 😹 😻 😼 😽 🙀 😿 😾 🙈 🙉 🙊
    """),
    cat("pessoas", "pessoas", "🙌", """
        👋 🤚 🖐 ✋ 🖖 🫱 🫲 🫳 🫴 👌 🤌 🤏 ✌️ 🤞 🫰 🤟 🤘 🤙 👈 👉 👆 🖕
        👇 ☝️ 🫵 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 🫶 👐 🤲 🤝 🙏 ✍️ 💅 🤳 💪 🦾 🦵
        🦿 🦶 👣 👂 🦻 👃 🧠 🫀 🫁 🦷 🦴 👀 👁 👅 👄 🫦 💋 🩸
        👶 🧒 👦 👧 🧑 👨 👩 🧓 👴 👵 🙍 🙎 🙅 🙆 💁 🙋 🧏 🙇 🤦 🤷
        👮 🕵️ 💂 🥷 👷 🫅 🤴 👸 👳 👲 🧕 🤵 👰 🤰 🫄 🫃 🤱 👼 🎅 🤶 🦸 🦹
        🧙 🧚 🧛 🧜 🧝 🧞 🧟 🧌 💆 💇 🚶 🧍 🧎 🏃 💃 🕺 🕴 👯 🧖 🧗
        👫 👬 👭 💏 💑 👪 🗣 👤 👥 🫂
    """),
    cat("natureza", "natureza", "🌿", """
        🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐻‍❄️ 🐨 🐯 🦁 🐮 🐷 🐽 🐸 🐵 🙈 🙉 🙊 🐒 🐔
        🐧 🐦 🐤 🐣 🐥 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🪱 🐛 🦋 🐌 🐞 🐜 🪰
        🪲 🪳 🦟 🦗 🕷 🕸 🦂 🐢 🐍 🦎 🦖 🦕 🐙 🦑 🦐 🦞 🦀 🐡 🐠 🐟 🐬 🐳
        🐋 🦈 🦭 🐊 🐅 🐆 🦓 🦍 🦧 🦣 🐘 🦛 🦏 🐪 🐫 🦒 🦘 🦬 🐃 🐂 🐄 🐎
        🐖 🐏 🐑 🦙 🐐 🦌 🐕 🐩 🦮 🐈 🪶 🐓 🦃 🦤 🦚 🦜 🦢 🦩 🕊 🐇 🦝 🦨
        🦡 🦫 🦦 🦥 🐁 🐀 🐿 🦔 🐾 🐉 🐲 🌵 🎄 🌲 🌳 🌴 🪵 🌱 🌿 ☘️ 🍀 🎍
        🪴 🎋 🍃 🍂 🍁 🍄 🐚 🪸 🪨 🌾 💐 🌷 🌹 🥀 🌺 🌸 🌼 🌻 🌞 🌝 🌛 🌜
        🌚 🌕 🌖 🌗 🌘 🌑 🌒 🌓 🌔 🌙 🌎 🌍 🌏 🪐 💫 ⭐ 🌟 ✨ ⚡ ☄️ 💥 🔥
        🌪 🌈 ☀️ 🌤 ⛅ 🌥 ☁️ 🌦 🌧 ⛈ 🌩 🌨 ❄️ ☃️ ⛄ 🌬 💨 💧 💦 🫧 ☔ ☂️
        🌊 🌫
    """),
    cat("comida", "comida", "🍕", """
        🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍈 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🥬
        🥒 🌶 🫑 🌽 🥕 🫒 🧄 🧅 🥔 🍠 🫘 🥐 🥯 🍞 🥖 🥨 🧀 🥚 🍳 🧈 🥞 🧇
        🥓 🥩 🍗 🍖 🦴 🌭 🍔 🍟 🍕 🫓 🥪 🥙 🧆 🌮 🌯 🫔 🥗 🥘 🫕 🥫 🍝 🍜
        🍲 🍛 🍣 🍱 🥟 🦪 🍤 🍙 🍚 🍘 🍥 🥠 🥮 🍢 🍡 🍧 🍨 🍦 🥧 🧁 🍰 🎂
        🍮 🍭 🍬 🍫 🍿 🍩 🍪 🌰 🥜 🍯 🥛 🍼 🫖 ☕ 🍵 🧃 🥤 🧋 🍶 🍺 🍻 🥂
        🍷 🥃 🍸 🍹 🧉 🍾 🧊 🥄 🍴 🍽 🥢 🧂
    """),
    cat("atividades", "atividades", "⚽", """
        ⚽ 🏀 🏈 ⚾ 🥎 🎾 🏐 🏉 🥏 🎱 🪀 🏓 🏸 🏒 🏑 🥍 🏏 🪃 🥅 ⛳ 🪁 🏹
        🎣 🤿 🥊 🥋 🎽 🛹 🛼 🛷 ⛸ 🥌 🎿 ⛷ 🏂 🪂 🏋️ 🤼 🤸 ⛹️ 🤺 🤾 🏌️ 🏇
        🧘 🏄 🏊 🤽 🚣 🧗 🚵 🚴 🏆 🥇 🥈 🥉 🏅 🎖 🏵 🎗 🎫 🎟 🎪 🤹 🎭 🩰
        🎨 🎬 🎤 🎧 🎼 🎹 🥁 🪘 🎷 🎺 🪗 🎸 🪕 🎻 🎲 ♟ 🎯 🎳 🎮 🎰 🧩 🪄
        🎊 🎉 🎈 🎁 🎀 🪅 🪩
    """),
    cat("viagem", "viagem", "✈️", """
        🚗 🚕 🚙 🚌 🚎 🏎 🚓 🚑 🚒 🚐 🛻 🚚 🚛 🚜 🦯 🦽 🦼 🛴 🚲 🛵 🏍 🛺
        🚨 🚔 🚍 🚘 🚖 🚡 🚠 🚟 🚃 🚋 🚞 🚝 🚄 🚅 🚈 🚂 🚆 🚇 🚊 🚉 ✈️ 🛫
        🛬 🛩 💺 🛰 🚀 🛸 🚁 🛶 ⛵ 🚤 🛥 🛳 ⛴ 🚢 ⚓ 🪝 ⛽ 🚧 🚦 🚥 🚏 🗺
        🗿 🗽 🗼 🏰 🏯 🏟 🎡 🎢 🎠 ⛲ ⛱ 🏖 🏝 🏜 🌋 ⛰ 🏔 🗻 🏕 ⛺ 🛖 🏠
        🏡 🏘 🏚 🏗 🏭 🏢 🏬 🏣 🏤 🏥 🏦 🏨 🏪 🏫 🏩 💒 🏛 ⛪ 🕌 🕍 🛕 🕋
        ⛩ 🛤 🛣 🗾 🎑 🏞 🌅 🌄 🌠 🎇 🎆 🌇 🌆 🏙 🌃 🌌 🌉 🌁
    """),
    cat("objetos", "objetos", "💡", """
        ⌚ 📱 📲 💻 ⌨️ 🖥 🖨 🖱 🖲 🕹 🗜 💽 💾 💿 📀 📼 📷 📸 📹 🎥 📽 🎞
        📞 ☎️ 📟 📠 📺 📻 🎙 🎚 🎛 🧭 ⏱ ⏲ ⏰ 🕰 ⌛ ⏳ 📡 🔋 🪫 🔌 💡 🔦
        🕯 🪔 🧯 🛢 💸 💵 💴 💶 💷 🪙 💰 💳 🪪 💎 ⚖️ 🪜 🧰 🪛 🔧 🔨 ⚒ 🛠
        ⛏ 🪚 🔩 ⚙️ 🪤 🧱 ⛓ 🧲 🔫 💣 🧨 🪓 🔪 🗡 ⚔️ 🛡 🚬 ⚰️ 🪦 ⚱️ 🏺 🔮
        📿 🧿 🪬 💈 ⚗️ 🔭 🔬 🕳 🩻 🩹 🩺 💊 💉 🩸 🧬 🦠 🧫 🧪 🌡 🧹 🪠 🧺
        🧻 🚽 🚰 🚿 🛁 🛀 🧼 🪥 🪒 🧽 🪣 🧴 🛎 🔑 🗝 🚪 🪑 🛋 🛏 🛌 🧸 🪆
        🖼 🪞 🪟 🛍 🛒 🎁 🎈 🎏 🎀 🪄 🪅 🎊 🎎 🏮 🎐 🧧 ✉️ 📩 📨 📧 💌 📥
        📤 📦 🏷 📪 📫 📬 📭 📮 📯 📜 📃 📄 📑 🧾 📊 📈 📉 🗒 🗓 📆 📅 🗑
        📇 🗃 🗳 🗄 📋 📁 📂 🗂 🗞 📰 📓 📔 📒 📕 📗 📘 📙 📚 📖 🔖 🧷 🔗
        📎 🖇 📐 📏 🧮 📌 📍 ✂️ 🖊 🖋 ✒️ 🖌 🖍 📝 ✏️ 🔍 🔎 🔏 🔐 🔒 🔓
    """),
    cat("simbolos", "símbolos", "💜", """
        ❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 ☮️ ✝️ ☪️
        🕉 ☸️ ✡️ 🔯 🕎 ☯️ ☦️ 🛐 ⛎ ♈ ♉ ♊ ♋ ♌ ♍ ♎ ♏ ♐ ♑ ♒ ♓ 🆔 ⚛️
        🉑 ☢️ ☣️ 📴 📳 🈶 🈚 🈸 🈺 🈷️ ✴️ 🆚 💮 🉐 ㊙️ ㊗️ 🈴 🈵 🈹 🈲 🅰️ 🅱️
        🆎 🆑 🅾️ 🆘 ❌ ⭕ 🛑 ⛔ 📛 🚫 💯 💢 ♨️ 🚷 🚯 🚳 🚱 🔞 📵 🚭 ❗ ❕
        ❓ ❔ ‼️ ⁉️ 🔅 🔆 〽️ ⚠️ 🚸 🔱 ⚜️ 🔰 ♻️ ✅ 🈯 💹 ❇️ ✳️ ❎ 🌐 💠 Ⓜ️
        🌀 💤 🏧 🚾 ♿ 🅿️ 🛗 🈳 🈂️ 🛂 🛃 🛄 🛅 🚹 🚺 🚼 ⚧ 🚻 🚮 🎦 📶 🈁
        🔣 ℹ️ 🔤 🔡 🔠 🆖 🆗 🆙 🆒 🆕 🆓 0️⃣ 1️⃣ 2️⃣ 3️⃣ 4️⃣ 5️⃣ 6️⃣ 7️⃣ 8️⃣ 9️⃣ 🔟
        🔢 #️⃣ *️⃣ ⏏️ ▶️ ⏸ ⏯ ⏹ ⏺ ⏭ ⏮ ⏩ ⏪ ⏫ ⏬ ◀️ 🔼 🔽 ➡️ ⬅️ ⬆️ ⬇️
        ↗️ ↘️ ↙️ ↖️ ↕️ ↔️ ↪️ ↩️ ⤴️ ⤵️ 🔀 🔁 🔂 🔄 🔃 🎵 🎶 ➕ ➖ ➗ ✖️ 🟰
        ♾ 💲 💱 ™️ ©️ ®️ 👁‍🗨 🔚 🔙 🔛 🔝 🔜 〰️ ➰ ➿ ✔️ ☑️ 🔘 🔴 🟠 🟡 🟢
        🔵 🟣 ⚫ ⚪ 🟤 🔺 🔻 🔸 🔹 🔶 🔷 🔳 🔲 ▪️ ▫️ ◾ ◽ ◼️ ◻️ 🟥 🟧 🟨
        🟩 🟦 🟪 ⬛ ⬜ 🟫 🔈 🔇 🔉 🔊 🔔 🔕 📣 📢 💬 💭 🗯 ♠️ ♣️ ♥️ ♦️ 🃏 🎴
    """),
)

private val APELIDOS: Map<String, String> = mapOf(
    "coracao" to "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 💕 💖 💗 💘 💝 ❣️",
    "coração" to "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 💕 💖 💗 💘 💝 ❣️",
    "risada" to "😂 🤣 😆 😹 😄 😁",
    "rindo" to "😂 🤣 😆 😹",
    "chorando" to "😭 😢 🥲 😿",
    "triste" to "😢 😭 😞 😔 🙁 ☹️",
    "raiva" to "😡 😠 🤬 👿 💢",
    "bravo" to "😡 😠 🤬 👿",
    "fogo" to "🔥",
    "joia" to "👍 💎",
    "joinha" to "👍",
    "legal" to "👍 😎 🆒",
    "amor" to "❤️ 😍 🥰 😘 💕 💖",
    "festa" to "🎉 🎊 🥳 🎈 🍾",
    "bolo" to "🎂 🍰 🧁",
    "aniversario" to "🎂 🎉 🎈 🎁",
    "caveira" to "💀 ☠️",
    "morto" to "💀 ☠️ 😵",
    "palmas" to "👏",
    "reza" to "🙏",
    "oracao" to "🙏",
    "obrigado" to "🙏 🙇",
    "olhos" to "👀 👁",
    "olho" to "👀 👁",
    "estrela" to "⭐ 🌟 ✨ 💫",
    "brilho" to "✨ 🌟 💫",
    "lua" to "🌙 🌛 🌜 🌚 🌝",
    "sol" to "☀️ 🌞 🌅",
    "gato" to "🐱 🐈 😺 😸 😻",
    "cachorro" to "🐶 🐕 🐩",
    "comida" to "🍕 🍔 🍟 🌭 🍜 🍣",
    "pizza" to "🍕",
    "cafe" to "☕",
    "café" to "☕",
    "cerveja" to "🍺 🍻",
    "jogo" to "🎮 🕹 🎲",
    "musica" to "🎵 🎶 🎧 🎤 🎸",
    "música" to "🎵 🎶 🎧 🎤 🎸",
    "dinheiro" to "💰 💵 💸 🪙 💳",
    "certo" to "✅ ✔️ ☑️ 👍",
    "errado" to "❌ ✖️ 🚫",
    "sono" to "😴 😪 💤 🥱",
    "dormindo" to "😴 💤",
    "pensando" to "🤔 💭",
    "susto" to "😱 😨 😰 😲",
    "medo" to "😨 😰 😱 👻",
    "foguete" to "🚀",
    "computador" to "💻 🖥 ⌨️",
    "telefone" to "📱 ☎️ 📞",
    "presente" to "🎁",
    "flor" to "🌸 🌺 🌻 🌷 🌹 💐",
    "chuva" to "🌧 ☔ 💧 ⛈",
    "neve" to "❄️ ☃️ ⛄ 🌨",
    "praia" to "🏖 🏝 🌊 ⛱",
    "carro" to "🚗 🚙 🏎 🚕",
    "aviao" to "✈️ 🛫 🛬",
    "avião" to "✈️ 🛫 🛬",
    "casa" to "🏠 🏡 🏘",
    "trabalho" to "💼 🏢 👔",
    "livro" to "📚 📖 📕 📗",
    "relogio" to "⏰ ⌚ 🕰 ⏳",
    "relógio" to "⏰ ⌚ 🕰 ⏳",
    "bandeira" to "🚩 🏁 🏳️ 🏴",
    "raio" to "⚡ 🌩",
    "agua" to "💧 💦 🌊 🚰",
    "água" to "💧 💦 🌊 🚰",
)

private val INDICE: Map<String, String> by lazy(LazyThreadSafetyMode.NONE) {
    val mapa = HashMap<String, String>(1024)
    for (c in CATEGORIAS_EMOJI) {
        for (glifo in c.itens) {
            val nome = runCatching { Character.getName(glifo.codePointAt(0)) }.getOrNull().orEmpty()
            mapa[glifo] = (nome + " " + c.nome).lowercase(Locale.ROOT)
        }
    }
    for ((apelido, glifos) in APELIDOS) {
        for (g in glifos.split(' ')) {
            if (g.isBlank()) continue
            mapa[g] = (mapa[g].orEmpty() + " " + apelido).trim()
        }
    }
    mapa
}

val TODOS_OS_EMOJIS: List<String> by lazy(LazyThreadSafetyMode.NONE) {
    CATEGORIAS_EMOJI.flatMap { it.itens }
}

fun buscarEmojis(termo: String): List<String> {
    val t = termo.trim().lowercase(Locale.ROOT)
    if (t.isEmpty()) return emptyList()
    val idx = INDICE
    val comeca = ArrayList<String>()
    val contem = ArrayList<String>()
    for (g in TODOS_OS_EMOJIS) {
        val texto = idx[g] ?: continue
        when {
            texto.split(' ').any { it.startsWith(t) } -> comeca.add(g)
            texto.contains(t) -> contem.add(g)
        }
    }
    return comeca + contem
}
