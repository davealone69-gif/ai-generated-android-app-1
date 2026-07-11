package com.example.droidcraft

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.ButtonDefaults
import com.example.droidcraft.ui.theme.DroidCraftTheme // Assuming this theme is defined in ui.theme package

/**
 * MainActivity for the DroidCraft application.
 * This activity hosts the main Compose UI for an interactive counter.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Apply the custom DroidCraftTheme to the entire application.
            // This ensures consistent styling across all Material3 components.
            DroidCraftTheme {
                // The main entry point for the counter application's UI.
                CounterAppScreen()
            }
        }
    }
}

/**
 * The root composable for the Counter Application, incorporating Material3's Scaffold.
 * It manages the main state of the counter and orchestrates the child components.
 */
@OptIn(ExperimentalMaterial3Api::class) // Required for using TopAppBar
@Composable
fun CounterAppScreen() {
    // State to hold the current count, remembered across recompositions.
    var clickCount by remember { mutableStateOf(0) }

    // Scaffold provides basic screen layout structure (TopAppBar, SnackbarHost, FloatingActionButton, etc.).
    Scaffold(
        topBar = {
            // A Material3 TopAppBar for the screen title.
            TopAppBar(
                title = {
                    Text(
                        text = "DroidCraft Counter",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        // Surface provides a themed background for the content.
        Surface(
            modifier = Modifier
                .fillMaxSize()
                // Apply padding from the Scaffold to ensure content is below the TopAppBar.
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background // Use the background color from the theme.
        ) {
            // The main content of the counter, extracted into a separate composable for clarity.
            CounterContent(
                count = clickCount,
                onIncrement = { clickCount++ }, // Lambda for incrementing the count.
                onReset = { clickCount = 0 }    // Lambda for resetting the count.
            )
        }
    }
}

/**
 * Encapsulates the core layout and interaction logic for the counter display and controls.
 * This composable receives the count and action lambdas as parameters, promoting
 * unidirectional data flow and better testability.
 *
 * @param count The current value of the counter.
 * @param onIncrement Callback to be invoked when the increment action is requested.
 * @param onReset Callback to be invoked when the reset action is requested.
 * @param modifier Optional [Modifier] for this composable.
 */
@Composable
fun CounterContent(
    count: Int,
    onIncrement: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp), // Enhanced padding
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceAround // Distribute space evenly
    ) {
        // Descriptive title for the counter application.
        TitleText("Smart Interactive Counter")

        // Display for the current count value.
        CounterDisplay(count = count)

        // Buttons for incrementing and resetting the counter.
        ActionButtons(
            onIncrement = onIncrement,
            onReset = onReset,
            modifier = Modifier.fillMaxWidth(0.8f) // Make buttons take 80% of width
        )
    }
}

/**
 * Composable for displaying the main title of the counter.
 *
 * @param text The title text to display.
 * @param modifier Optional [Modifier] for this composable.
 */
@Composable
fun TitleText(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        fontWeight = FontWeight.ExtraBold, // Stronger emphasis
        style = MaterialTheme.typography.headlineMedium, // Appropriate headline style
        color = MaterialTheme.colorScheme.onBackground, // Text color from theme
        modifier = modifier.padding(bottom = 16.dp) // Add bottom padding
    )
}

/**
 * Composable for displaying the current count value.
 *
 * @param count The integer value to display.
 * @param modifier Optional [Modifier] for this composable.
 */
@Composable
fun CounterDisplay(
    count: Int,
    modifier: Modifier = Modifier
) {
    Text(
        text = "Total counts: $count",
        style = MaterialTheme.typography.displayLarge, // Larger, more prominent display style
        color = MaterialTheme.colorScheme.primary,     // Use primary color for the count value
        modifier = modifier.padding(top = 16.dp, bottom = 32.dp) // Generous vertical padding
    )
}

/**
 * Composable for holding the action buttons (Increment and Reset).
 *
 * @param onIncrement Callback for the increment button.
 * @param onReset Callback for the reset button.
 * @param modifier Optional [Modifier] for this composable.
 */
@Composable
fun ActionButtons(
    onIncrement: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly, // Evenly spaced buttons
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Primary action button using ElevatedButton for visual prominence.
        ElevatedButton(
            onClick = onIncrement,
            modifier = Modifier
                .weight(1f)
                .height(56.dp), // Consistent button height
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 8.dp) // Deeper shadow
        ) {
            Text("Increment", style = MaterialTheme.typography.titleLarge) // Larger text for buttons
        }

        Spacer(modifier = Modifier.width(16.dp)) // Space between buttons

        // Secondary action button using OutlinedButton for a subtle, but clear action.
        OutlinedButton(
            onClick = onReset,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error // Use error color for reset for clear distinction
            ),
            border = ButtonDefaults.outlinedButtonBorder.copy(width = 2.dp) // Thicker border
        ) {
            Text("Reset", style = MaterialTheme.typography.titleLarge) // Larger text for buttons
        }
    }
}

// NOTE: The `com.example.droidcraft.ui.theme` package, containing `DroidCraftTheme`,
// `Theme.kt`, `Color.kt`, and `Type.kt`, is assumed to exist in a standard Android Compose project setup.
// It is not included in this single file output as per the instructions.