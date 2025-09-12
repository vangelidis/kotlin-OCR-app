package com.example.navlesson

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.util.Log
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString

class ListEntries : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ListEntriesScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListEntriesScreen() {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(emptyList<Entry>()) }

    // Load entries from the database
    LaunchedEffect(Unit) {
        entries = getAllEntriesFromDatabase(context)
        if (entries.isEmpty()) {
            Log.d("Explain", "No entries")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("List of Entries") },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(entries) { entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    elevation = CardDefaults.cardElevation(4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Text: \n ${entry.text}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "Link Icon")
                            Spacer(modifier = Modifier.width(4.dp))
                            val uriHandler = LocalUriHandler.current
                            ClickableText(
                                text = AnnotatedString("Link: ${entry.link}"),
                                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.primary),
                                onClick = {
                                    uriHandler.openUri(entry.link)
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Type: ${entry.type}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = when (entry.type.lowercase()) {
                                "text" -> MaterialTheme.colorScheme.primary
                                "image" -> MaterialTheme.colorScheme.secondary
                                "video" -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }
    }
}
