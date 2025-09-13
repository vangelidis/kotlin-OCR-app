package com.example.navlesson

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.navlesson.ui.theme.NavLessonTheme
import com.example.navlesson.composable.StartMenuScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NavLessonTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MyApp()
                }
            }
        }
    }
}

@Composable
fun MyApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "startmenu") {
        composable("startmenu") {
            StartMenuScreen(
                onUserClick = { navController.navigate("firstscreen") },
                onAdminClick = { navController.navigate("adminscreen") }
            )
        }
        composable("firstscreen") {
            FirstScreen { name, age ->
                // Handle navigation or action if needed
            }
        }
        composable("adminscreen") {
            AdminScreenContent(
                onAddNewEntryClick = {
                    // Handle navigation or action for "Add New Entry"
                },
                onListEntriesClick = {
                    // Handle navigation or action for "List Entries"
                }
            )
        }
    }
}
