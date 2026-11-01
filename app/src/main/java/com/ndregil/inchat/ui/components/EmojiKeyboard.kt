package com.ndregil.inchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class EmojiCategory(
    val title: String,
    val emojis: List<String>
)

val EMOJI_CATEGORIES = listOf(
    EmojiCategory(
        "Senyum",
        listOf("😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩", "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣", "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗", "🤫", "🤭", "🥱", "😴", "🤤", "😪", "😷", "🤒", "🤕", "🤢", "🤮", "🤧")
    ),
    EmojiCategory(
        "Orang",
        listOf("👋", "🤚", "🖐", "✋", "🖖", "👌", "🤌", "🤏", "✌️", "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆", "🖕", "👇", "☝️", "👍", "👎", "✊", "👊", "🤛", "👏", "🙌", "👐", "🤲", "🤝", "🙏", "✍️", "💅", "🤳", "💪", "🦾", "🦿", "🦵", "🦶", "👂", "👃", "🧠", "🦷", "🦴", "👀", "👁", "👅", "👄", "💋", "👤", "👥", "🫂")
    ),
    EmojiCategory(
        "Hewan & Alam",
        listOf("🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔", "🐧", "🐦", "🐤", "🐣", "🐥", "🦆", "🦅", "🦉", "🦇", "🐺", "🐗", "🐴", "🦄", "🐝", "🐛", "🦋", "🐌", "🐞", "🐜", "🐢", "🐍", "🦎", "🐙", "🦑", "🦐", "🦞", "🦀", "🐡", "🐠", "🐟", "🐬", "🐳", "🐋", "🦈", "🐊", "🐅", "🐆", "🦓", "🦍", "🐘", "🦛", "🦏", "🐪", "🐫", "🦒", "🦘", "🐃", "🐂", "🐄", "🐎", "🐖", "🐏", "🐑", "🐐", "🦌", "🐕", "🐩", "🐈", "🐓", "🦃", "🦚", "🦜", "🦢", "🕊", "🐇", "🌲", "🌳", "🌴", "🌵", "🌾", "🌿", "🍀", "🍁", "🍂", "🍃")
    ),
    EmojiCategory(
        "Makanan",
        listOf("🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🍈", "🍒", "🍑", "🍍", "🥥", "🥝", "🍅", "🍆", "🥑", "🥦", "🥬", "🥒", "🌶", "🌽", "🥕", "🧄", "🧅", "🥔", "🍠", "🥐", "🥯", "🍞", "🥖", "🥨", "🧀", "🥚", "🍳", "🥞", "🥓", "🥩", "🍗", "🍖", "🌭", "🍔", "🍟", "🍕", "🥪", "🌮", "🌯", "🥗", "🥘", "🍝", "🍜", "🍲", "🍛", "🍣", "🍱", "🥟", "🍤", "🍙", "🍚", "🍘", "🍢", "🍡", "🍧", "🍨", "🍦", "🧁", "🍰", "🎂", "🍮", "🍭", "🍬", "🍫", "🍿", "🍩", "🍪", "🌰", "🍯", "🥛", "☕️", "🍵", "🧃", "🥤", "🧋", "🍺", "🍻", "🥂", "🍷", "🥃", "🍸", "🍹", "🍾")
    ),
    EmojiCategory(
        "Aktivitas",
        listOf("⚽️", "🏀", "🏈", "⚾️", "🎾", "🏐", "🏉", "🥏", "🎱", "🏓", "🏸", "🏒", "🏑", "🥍", "🏏", "🥅", "⛳️", "🪁", "🏹", "🎣", "🤿", "🥋", "🛹", "🛼", "🛷", "⛸", "🎿", "🏋️‍♀️", "🤸‍♀️", "⛹️‍♀️", "🚴‍♀️", "🏎", "🏍", "🛵", "🚲", "🚗", "🚕", "🚙", "🚌", "🚑", "🚒", "🚐", "🚚", "🚜", "🚄", "🚅", "🚆", "🚇", "🚉", "✈️", "🚀", "🛸", "🚁", "⛵️", "🚤", "🛳", "🚢", "⚓️", "⛽️", "🚧", "🚦", "🛑")
    ),
    EmojiCategory(
        "Objek & Simbol",
        listOf("❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "☮️", "✝️", "☪️", "🕉", "☸️", "✡️", "☯️", "⚛️", "💡", "🔦", "🏮", "📔", "📕", "📖", "📗", "📘", "📙", "📚", "📓", "📄", "📑", "📊", "📈", "📉", "💰", "🪙", "💵", "💶", "💷", "💳", "🧾", "✉️", "📧", "📨", "📩", "📦", "📫", "✏️", "✒️", "🖊", "🖌", "🖍", "📝", "💼", "📅", "📆", "📌", "📍", "📎", "📏", "📐", "✂️", "🔓", "🔒", "🔑", "🔨", "⚒", "🛠", "🗡", "⚔️", "🛡", "🔧", "🔩", "⚙️", "⚗️", "🧪", "🧫", "🧬", "🔬", "🔭", "📡", "💉", "💊", "🚪", "🛏", "🛋", "🪑", "🚽", "🚿", "🛁", "🪞", "🪟", "🧻", "🧹", "🧼", "🧽", "🧯", "🛒", "🗿")
    )
)

@Composable
fun EmojiKeyboard(
    onEmojiSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategoryIndex by remember { mutableStateOf(0) }
    val category = EMOJI_CATEGORIES[selectedCategoryIndex]

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Category Tab Row
            ScrollableTabRow(
                selectedTabIndex = selectedCategoryIndex,
                edgePadding = 8.dp,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                divider = {}
            ) {
                EMOJI_CATEGORIES.forEachIndexed { index, cat ->
                    Tab(
                        selected = selectedCategoryIndex == index,
                        onClick = { selectedCategoryIndex = index },
                        text = { Text(cat.title, fontSize = 12.sp) },
                        modifier = Modifier.testTag("emoji_tab_${cat.title}")
                    )
                }
            }

            // Emoji Grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 40.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(category.emojis) { emoji ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onEmojiSelected(emoji) }
                            .testTag("emoji_item_$emoji"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = emoji,
                            fontSize = 22.sp
                        )
                    }
                }
            }
        }
    }
}
