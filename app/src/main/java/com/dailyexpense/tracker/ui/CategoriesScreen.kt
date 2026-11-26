package com.dailyexpense.tracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dailyexpense.tracker.data.Category

/** Lists expense types with edit and remove actions. Adding is via the screen's FAB. */
@Composable
fun CategoriesScreen(
    categories: List<Category>,
    onEdit: (Category) -> Unit,
    onDelete: (Category) -> Unit,
    contentPadding: PaddingValues,
) {
    if (categories.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            Text(
                "No expense types.\nTap + to add one.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories, key = { it.id }) { c ->
            Card(Modifier.fillMaxWidth().clickable { onEdit(c) }) {
                Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(c.name, c.icon, c.color, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(c.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { onEdit(c) }) { Icon(Icons.Filled.Edit, "Edit ${c.name}") }
                    IconButton(onClick = { onDelete(c) }) {
                        Icon(Icons.Filled.Delete, "Remove ${c.name}", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/**
 * Add (existing == null) or modify an expense type. The user picks an icon from [TYPE_ICONS];
 * until they do, the icon suggested by the name is highlighted. [error] is shown under the name field.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryDialog(
    existing: Category?,
    error: String?,
    onSave: (Category) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    // Icon picked in this dialog (or the one the type already shows). null = follow the name.
    var pickedKey by rememberSaveable { mutableStateOf(existing?.let { iconFor(it.icon, it.name)?.key }) }
    // Circle colour: the type's current colour, replaced by the icon's colour when one is picked.
    var color by rememberSaveable { mutableStateOf(existing?.color) }

    val shown: TypeIcon = TYPE_ICONS.firstOrNull { it.key == pickedKey }
        ?: suggestIconForName(name)
        ?: TYPE_ICONS.first { it.key == "tag" }
    val shownColor = color ?: shown.color

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add expense type" else "Modify expense type") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    leadingIcon = { IconCircle(shown.vector, shownColor, 28.dp) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Icon: ${shown.label}", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TYPE_ICONS.forEach { option ->
                        val selected = option.key == shown.key
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .then(
                                    if (selected) Modifier.border(
                                        BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), CircleShape
                                    ) else Modifier
                                )
                                .clickable(onClickLabel = option.label) {
                                    pickedKey = option.key
                                    color = option.color
                                }
                                .padding(5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            IconCircle(option.vector, option.color, 38.dp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    (existing ?: Category(name = "", color = shownColor))
                        .copy(name = name, color = shownColor, icon = shown.key)
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun DeleteCategoryDialog(category: Category, expenseCount: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove \"${category.name}\"?") },
        text = {
            Text(
                if (expenseCount == 0) "This expense type isn't used by any expenses."
                else "$expenseCount expense(s) use this type. They will be kept and shown as \"Uncategorized\"."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Remove", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
