package com.example.navlesson

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.util.Log
import androidx.compose.material3.Button
import androidx.compose.ui.platform.LocalContext
//import kotlin.coroutines.jvm.internal.CompletedContinuation.context

class AdminScreen : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AdminScreenContent(
                onAddNewEntryClick = {
                    // Handle "Add New Entry" button click
                },
                onListEntriesClick = {
                    Log.d("Explain", "List Entries button clicked")
                    val intent = Intent(this, ListEntries::class.java)
                    startActivity(intent)
                }
            )
        }
    }
}

@Composable
fun AdminScreenContent(
    onAddNewEntryClick: () -> Unit,
    onListEntriesClick: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    val intent = Intent(context, AddEntry::class.java)
                    context.startActivity(intent)
                }
            ) {
                Text("New")
            }
            Button(
                onClick = {
                    val intent = Intent(context, ListEntries::class.java)
                    context.startActivity(intent)
                }
            ) {
                Text("Open List Entries")
            }
        }
    }
}