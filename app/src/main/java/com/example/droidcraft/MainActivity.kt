package com.example.droidcraft

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * MainActivity is the entry point for the DroidCraft application.
 * It sets up the Compose UI content with a custom Material3 theme and the main application screen.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Apply the custom DroidCraft theme to the entire application content.
            DroidCraftTheme {
                MainAppScreen()
            }
        }
    }
}

// --- Theme Definition ---
// (Normally, these would reside in a separate `ui.theme` package for better modularity.)

/**
 * Defines the dark color scheme for the DroidCraft application.
 * Utilizes a vibrant purple as the primary color with complementary tones.
 */
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFBB86FC), // Vibrant purple for main elements
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF3700B3), // Darker purple for contained elements
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF03DAC5), // Teal for secondary actions
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF00BFA5),
    onSecondaryContainer = Color.Black,
    tertiary = Color(0xFF3700B3), // Another shade of purple
    onTertiary = Color.White,
    background = Color(0xFF121212), // Deep dark background
    onBackground = Color.White,
    surface = Color(0xFF1E1E1E), // Slightly lighter surface for depth
    onSurface = Color.White,
    surfaceVariant = Color(0xFF424242), // More pronounced variant for cards/containers
    onSurfaceVariant = Color(0xFFE0E0E0),
    error = Color(0xFFCF6679), // Red for error states or destructive actions
    onError = Color.Black,
    outline = Color(0xFF707070) // Neutral gray for borders
)

/**
 * Defines the light color scheme for the DroidCraft application.
 * Uses a classic Material purple as the primary color with light backgrounds.
 */
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6200EE), // Rich purple for main elements
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBB86FC),
    onPrimaryContainer = Color.Black,
    secondary = Color(0xFF03DAC5), // Teal for secondary actions
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF00BFA5),
    onSecondaryContainer = Color.Black,
    tertiary = Color(0xFF3700B3),
    onTertiary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color(0xFFF0F0F0), // Light gray surface for depth
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE0E0E0), // More pronounced variant for cards/containers
    onSurfaceVariant = Color(0xFF424242),
    error = Color(0xFFB00020), // Red for error states or destructive actions
    onError = Color.White,
    outline = Color(0xFFC0C0C0) // Light gray for borders
)

/**
 * Defines the typography for the DroidCraft application.
 * Customizes various text styles for consistent visual hierarchy and readability.
 */
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle( // Used for the main count number, bold and large
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 48.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle( // Used for TopAppBar title, prominent
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle( // Used for button text, clear and readable
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    labelLarge = TextStyle( // Used for "Total counts" label, semi-bold for emphasis
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle( // Standard body text
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)

/**
 * Defines the shapes for various components in the DroidCraft application.
 * Provides consistent rounded corners across the UI.
 */
val Shapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(12.dp), // Medium rounded corners for general components
    large = RoundedCornerShape(20.dp) // Larger rounded corners for prominent cards
)

/**
 * A custom Material3 theme composable for the DroidCraft application.
 * It selects between light and dark color schemes based on the system setting.
 */
@Composable
fun DroidCraftTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}

// --- Main Application Screen and Reusable Components ---

/**
 * The main application screen composable that orchestrates the counter UI.
 * It uses Scaffold for a structured layout including a TopAppBar and content area.
 */
@OptIn(ExperimentalMaterial3Api::class) // TopAppBar is still experimental in M3
@Composable
fun MainAppScreen() {
    // Manages the mutable state for the click count, surviving recompositions.
    var clickCount by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Smart Interactive Counter",
                        style = MaterialTheme.typography.headlineSmall, // Apply theme typography
                        color = MaterialTheme.colorScheme.onSurface // Ensure text color contrasts with surface
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface, // Use theme surface color
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        modifier = Modifier.fillMaxSize() // Occupy the entire available screen space
    ) { paddingValues ->
        // Main content column, centered horizontally and vertically
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // Apply padding from Scaffold to correctly position content below TopAppBar
                .padding(horizontal = 24.dp, vertical = 16.dp), // Additional inner padding for aesthetics
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center // Center content vertically
        ) {
            // Extracted component for displaying the current counter value
            CounterDisplay(count = clickCount)

            Spacer(modifier = Modifier.height(48.dp)) // Significant spacing for visual separation and rhythm

            // Extracted component for the increment button
            IncrementButton(onClick = { clickCount++ })

            Spacer(modifier = Modifier.height(24.dp)) // Spacing between the action buttons

            // Added a reset button for additional user interaction
            ResetButton(onClick = { clickCount = 0 })
        }
    }
}

/**
 * A dedicated composable for displaying the counter value within a Material3 Card.
 * This component encapsulates the visual presentation of the count.
 *
 * @param count The integer value to display.
 */
@Composable
fun CounterDisplay(count: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth(0.8f) // Take 80% of parent width for good presence
            .aspectRatio(1.5f), // Maintain a consistent aspect ratio for the card shape
        shape = MaterialTheme.shapes.large, // Apply large rounded corners from the theme
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant, // Use a distinct background color for contrast
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp) // Add shadow for depth
    ) {
        Box(
            modifier = Modifier.fillMaxSize(), // Fill the card's available space
            contentAlignment = Alignment.Center // Center content within the card
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Total counts:",
                    style = MaterialTheme.typography.labelLarge, // Apply theme label style
                    color = MaterialTheme.colorScheme.onSurfaceVariant // Ensure good contrast
                )
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.displayMedium, // Apply prominent display style
                    color = MaterialTheme.colorScheme.primary // Highlight the count with the primary theme color
                )
            }
        }
    }
}

/**
 * A reusable composable for an elevated increment button.
 * It provides a clear call to action with Material3 elevation.
 *
 * @param onClick The lambda to be executed when the button is clicked.
 */
@Composable
fun IncrementButton(onClick: () -> Unit) {
    ElevatedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth(0.7f) // Take 70% of parent width for a medium size
            .height(64.dp), // Taller button for easier tapping
        shape = MaterialTheme.shapes.medium, // Apply medium rounded corners from the theme
        elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 8.dp, pressedElevation = 4.dp), // Distinct shadow effects
        colors = ButtonDefaults.elevatedButtonColors(
            containerColor = MaterialTheme.colorScheme.primary, // Use primary color for the button background
            contentColor = MaterialTheme.colorScheme.onPrimary // Text color that contrasts with primary
        )
    ) {
        Text("Increment", style = MaterialTheme.typography.titleMedium) // Apply theme title style
    }
}

/**
 * A reusable composable for an outlined reset button.
 * It provides a secondary action with a visual distinction (outline) and error-indicating color.
 *
 * @param onClick The lambda to be executed when the button is clicked.
 */
@Composable
fun ResetButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth(0.7f) // Match width of increment button
            .height(64.dp), // Match height of increment button
        shape = MaterialTheme.shapes.medium, // Apply medium rounded corners from the theme
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline), // Thicker border for visual weight
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error // Use error color to signify a 'reset' or potentially destructive action
        )
    ) {
        Text("Reset", style = MaterialTheme.typography.titleMedium) // Apply theme title style
    }
}