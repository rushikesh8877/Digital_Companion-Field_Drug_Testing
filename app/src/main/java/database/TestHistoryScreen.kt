package database

import com.sih.drugtestclassifier.models.DigitalTestRecord

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TestHistoryScreen(
    tests: List<DigitalTestRecord>,
    onSearch: (String) -> Unit,
    onFilter: (String) -> Unit,
    onShowAll: () -> Unit
) {

    var searchText by remember {
        mutableStateOf("")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            text = "Test History"
        )

        Text(
            text = "Total Tests: ${tests.size}",
            modifier = Modifier.padding(vertical = 8.dp)
        )

        OutlinedTextField(
            value = searchText,
            onValueChange = {
                searchText = it
            },
            label = {
                Text("Search Test ID / Operator / Result")
            },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                onSearch(searchText)
            },
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Text("Search")
        }

        Row(
            modifier = Modifier.fillMaxWidth()
        ) {

            Button(
                onClick = {
                    onFilter("Positive")
                }
            ) {
                Text("Positive")
            }

            Button(
                onClick = {
                    onFilter("Negative")
                },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("Negative")
            }

            Button(
                onClick = {
                    onFilter("Inconclusive")
                },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("Inconclusive")
            }
        }

        Button(
            onClick = onShowAll,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Text("Show All")
        }

        tests.forEach { test ->

            Text(
                text = """
                    ID: ${test.testId}
                    Result: ${test.result}
                    Operator: ${test.operatorId}
                    Confidence: ${test.confidence}
                """.trimIndent(),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}