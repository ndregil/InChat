package com.ndregil.inchat

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.ndregil.inchat.data.model.OccupantAffiliation
import com.ndregil.inchat.data.model.OccupantRole
import com.ndregil.inchat.data.model.XmppRoom
import com.ndregil.inchat.ui.components.RoomItemCard
import com.ndregil.inchat.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val sampleRoom = XmppRoom(
        jid = "general@conference.xmpp.today",
        name = "# Umum (General)",
        topic = "Ruang obrolan umum multi-user XMPP",
        myNickname = "AndroidUser",
        occupantCount = 5,
        unreadCount = 2,
        lastMessageText = "Halo selamat datang di InChat!",
        lastMessageTime = System.currentTimeMillis(),
        isJoined = true,
        isBookmarked = true,
        myRole = OccupantRole.MEMBER,
        myAffiliation = OccupantAffiliation.MEMBER,
        colorHex = "#0284C7"
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        RoomItemCard(
            room = sampleRoom,
            isSelected = true,
            onClick = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}

